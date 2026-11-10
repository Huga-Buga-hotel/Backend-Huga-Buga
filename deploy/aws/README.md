# Deploy na AWS (EC2 + RDS + S3)

```
                 ┌──────────────────────────── EC2 (Docker) ─────────────────────────────┐
App Android ─┐   │                                                                        │
             ├─HTTPS─▶ caddy :443 ──▶ api :8080 ──── X-Chatbot-Token ───▶ chatbot :8000 ──┼──▶ Groq
Site (Web) ──┘   │      (Let's Encrypt)   │   ▲                                │          │
                 │                        │   └──── tools: GET /api/v1/... ────┘          │
                 └────────────────────────┼───────────────────────────────────────────────┘
                                          ├──▶ RDS PostgreSQL 16 (privado, só a EC2 acessa)
                                          └──▶ S3 (fotos das avaliações, bucket privado, IAM role)
```

- Só o Caddy abre portas (80/443). A API e o chatbot ficam na rede interna do Docker.
- O app e o site falam **só com a API** (`/api/v1/chat`); a chave do Groq nunca sai da EC2.
- O chatbot guarda as conversas em memória: rode **uma** instância (é o que este compose faz).

## 1. S3 (fotos)

1. Crie o bucket (ex.: `hugabuga-uploads-seunome`) na mesma região da EC2.
2. Deixe **Block all public access** ligado. As fotos saem pela API (`GET /api/v1/arquivos/relatos/...`),
   que confere se a foto é de uma avaliação publicada.

## 2. IAM role da EC2

Crie uma role para EC2 com esta política (troque o nome do bucket) e anexe à instância:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::hugabuga-uploads-seunome/uploads/*"
  }]
}
```

Nada de `AWS_ACCESS_KEY_ID` no `.env`: o SDK pega credenciais temporárias da role.

## 3. Security groups

| SG          | Entrada                                              |
| ----------- | ---------------------------------------------------- |
| `sg-ec2`    | 80 e 443 de `0.0.0.0/0`; 22 **só do seu IP**         |
| `sg-rds`    | 5432 **só de `sg-ec2`** (origem = o security group)  |

## 4. RDS (PostgreSQL 16)

- Engine PostgreSQL 16, `db.t4g.micro` serve para o TCC. Banco inicial: `hugabuga`.
- **Public access: No**, security group `sg-rds`, mesma VPC da EC2.
- O Flyway cria tudo na primeira subida, inclusive as extensões `pgcrypto` e `btree_gist`
  (o usuário master do RDS tem permissão).
- A conexão usa SSL: `DB_URL=jdbc:postgresql://<endpoint>:5432/hugabuga?sslmode=require`.

## 5. EC2

- Amazon Linux 2023, **t3.medium** (4 GB). O chatbot carrega PyTorch e o modelo de embeddings (~1 GB de RAM)
  e a API Java usa ~0,5–1 GB; numa t3.small o build da imagem do chatbot costuma ficar sem memória.
- Disco de 30 GB (a imagem do chatbot tem ~1,5 GB).
- Associe um **Elastic IP** e aponte o domínio para ele. Sem domínio, use o sslip.io:
  IP `3.91.10.20` → `DOMINIO=3-91-10-20.sslip.io` (o Let's Encrypt aceita).
- Containers usando a IAM role: o IMDSv2 precisa de *hop limit* 2 (padrão no AL2023). Se o upload
  der erro de credencial, rode:
  `aws ec2 modify-instance-metadata-options --instance-id <id> --http-put-response-hop-limit 2 --http-tokens required`

```bash
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user   # saia e entre de novo no SSH
# Docker Compose v2 (plugin)
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/download/v2.39.2/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Os dois repositórios LADO A LADO
mkdir ~/hugabuga && cd ~/hugabuga
git clone <url>/Backend-Huga-Buga.git
git clone <url>/chatbot-huga-buga-hotel.git

cd Backend-Huga-Buga/deploy/aws
cp .env.example .env && nano .env      # preencha tudo (veja os comentários)
chmod 600 .env
docker compose up -d --build
docker compose logs -f api             # espere "Started MainApplication"
```

Teste: `curl https://$DOMINIO/api/v1/health` e abra `https://$DOMINIO/api/v1/docs`.

### Atualizar

```bash
cd ~/hugabuga/Backend-Huga-Buga && git pull
cd ../chatbot-huga-buga-hotel && git pull
cd ../Backend-Huga-Buga/deploy/aws && docker compose up -d --build
```

## 6. App Android

```bash
./gradlew assembleRelease -Phugabuga.apiUrl=https://$DOMINIO/api/v1/
```

## Checklist de segurança

- **Usuários de teste no RDS:** o Flyway aplica o `V2__dados_iniciais.sql`, que cria
  `admin@hugabuga.example` / `marina.souza@example.com` com as senhas que estão no repositório.
  Antes de divulgar o endereço, crie sua conta de admin real e remova/troque esses usuários
  (ou separe a carga de teste do V2 numa pasta de migração só do perfil `dev`).
- `SPRING_PROFILES_ACTIVE` vazio em produção (o compose já força): sem Pix simulado.
- `JWT_SEGREDO` e `CHATBOT_TOKEN` gerados com `openssl rand`, diferentes dos de desenvolvimento.
- `.env` com `chmod 600`, nunca no git.
- RDS e chatbot sem acesso público; SSH só do seu IP.
- O `/api/v1/chat` é aberto para visitantes e cada mensagem custa uma chamada ao Groq. Se houver abuso,
  coloque um limite de requisições por IP/usuário (ex.: filtro no Spring com Bucket4j).
