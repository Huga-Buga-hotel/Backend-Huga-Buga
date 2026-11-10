# Huga Buga Hotel API

API REST do **Huga Buga Hotel** (projeto de conclusão de curso): busca e reserva de cavernas (suítes), disponibilidade por noite, checkout com extras, combo quarto + restaurante, cupom, pagamento simulado (cartão, Pix e na chegada), restaurante **O Banquete** (mesa de hóspede ou de visitante de fora), atividades com vagas, avaliações com fotos e um painel para a equipe do hotel (role **ADMIN**).

## Tecnologias

- Java 25 e Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation)
- PostgreSQL 16 com Flyway (migrações em `src/main/resources/db/migration`)
- JWT (JJWT 0.13) com refresh token opaco guardado como hash
- springdoc-openapi (Swagger UI)
- OpenPDF (voucher em PDF)
- Lombok
- Testes de integração com Testcontainers (PostgreSQL real)

## Documentação da API (Swagger)

Com a API no ar:

- **Swagger UI:** http://localhost:8080/api/v1/docs
- **OpenAPI (JSON):** http://localhost:8080/v3/api-docs

No Swagger, faça login em `POST /auth/login`, copie o `accessToken` e clique em **Authorize** (`bearerAuth`). Para reservas feitas sem conta, use o esquema `responsavelEmail` (header `X-Responsavel-Email`).

## Como rodar

### Opção 1: Docker Compose (banco + API)

```bash
cp .env.example .env      # ajuste DB_PASSWORD e JWT_SEGREDO
docker compose up -d --build
```

- API em `http://localhost:8080` (healthcheck em `/api/v1/health`)
- PostgreSQL exposto em `localhost:5433` (para o pgAdmin/DBeaver)
- Fotos das avaliações ficam no volume `hugabuga-uploads`

> As migrações V1/V2 foram reescritas nesta versão. Se você já tinha subido o banco antes, recrie o volume: `docker compose down -v`.

### Opção 2: só o banco no Docker e a API pelo Maven

```bash
docker run -d --name hugabuga-db \
  -e POSTGRES_DB=hugabuga -e POSTGRES_USER=hugabuga -e POSTGRES_PASSWORD=hugabuga \
  -p 5432:5432 postgres:16-alpine

./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

No Windows: `mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"`.

Na primeira execução o Flyway cria as tabelas (`V1__schema.sql`) e carrega os dados iniciais (`V2__dados_iniciais.sql`).

```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP"}
```

### Perfil `dev`

- valores padrão de conexão (`localhost:5432/hugabuga`, usuário e senha `hugabuga`) e um segredo JWT de desenvolvimento;
- habilita `POST /api/v1/pagamentos/{id}/simular-confirmacao` e aprova o Pix sozinho 20 s depois de gerado;
- `POST /auth/esqueci-senha` devolve o token em `tokenDev` (não há servidor de e-mail);
- logs `DEBUG` do pacote `com.hugabuga`.

## Variáveis de ambiente

Sem o perfil `dev`, a aplicação **não sobe** se faltar alguma das obrigatórias.

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DB_URL` | sim | URL JDBC, ex.: `jdbc:postgresql://host:5432/hugabuga` (no compose já vem pronta) |
| `DB_USER` | sim | Usuário do banco |
| `DB_PASSWORD` | sim | Senha do banco |
| `JWT_SEGREDO` | sim | Segredo do JWT, **mínimo 32 caracteres** |
| `CORS_ORIGENS` | não | Origens permitidas, separadas por vírgula (padrão: `http://localhost:3000,http://localhost:5173`) |
| `UPLOADS_DIR` | não | Pasta das fotos das avaliações (padrão: `./uploads`; no Docker: `/app/uploads`) |
| `SPRING_PROFILES_ACTIVE` | não | `dev` para desenvolvimento; vazio em produção |

> **Atenção:** `V2__dados_iniciais.sql` contém usuários e senhas de teste. Não use essa carga em produção.

## Dados de teste

| Item | Valor |
|---|---|
| Visitante | `marina.souza@example.com` / `Uga!Buga2026` |
| Admin | `admin@hugabuga.example` / `Admin!Buga2026` |
| Cupom | `UGABUGA10` (10% até 31/12/2026) |
| Combos | `JANTAR_DA_TRIBO`, `BANQUETE_COMPLETO` |

### Suítes (5 tipos, 25 tocas)

| Caverna | Slug | Categoria | Capacidade | Diária | Tocas |
|---|---|---|---|---|---|
| Toca do Mamute | `toca-do-mamute` | FAMILIA | 5 | R$ 520 | 6 (T-01 a T-06) |
| Gruta do Casal | `gruta-do-casal` | CASAL | 2 | R$ 390 | 6 (G-01 a G-06) |
| Ninho do Pterodáctilo | `ninho-do-pterodactilo` | CASAL | 3 | R$ 470 | 5 (N-01 a N-05) |
| Covil dos Caçadores | `covil-dos-cacadores` | AMIGOS | 6 | R$ 610 | 5 (C-01 a C-05) |
| Suíte do Chefe da Tribo | `suite-do-chefe-da-tribo` | GRUPO | 8 | R$ 980 | 3 (S-01 a S-03) |

## Autenticação, roles e permissões

Todas as rotas começam com `/api/v1`. Há dois papéis (**roles**), que vão dentro do token JWT:

- **VISITANTE:** todo cadastro público nasce assim. Reserva, avalia e gerencia a própria conta.
- **ADMIN:** equipe do hotel. Acessa `/admin/**` e pode agir em **qualquer** reserva pelas rotas normais de `/reservas` (ver voucher, cancelar, check-in).

Níveis de acesso:

- **Livre:** catálogo, busca, filtros, disponibilidade, cotação, atividades, cardápio, combos, extras, home, health, cadastro, login, refresh e "esqueci a senha".
- **Login:** `/me/**`, logout e enviar avaliação.
- **Dono da reserva:** rotas de `/reservas/**`, `/pagamentos/**` e mesas do Banquete. Você é o dono se estiver logado com a conta que fez a reserva **ou** se enviar o header `X-Responsavel-Email` com o e-mail do responsável. Senão: `403 SEM_PERMISSAO`.
- **Admin:** `/admin/**` (`403` para visitante, `401` sem token).

O access token dura 15 minutos. O refresh token dura 30 dias com `lembrar: true` e 1 dia sem isso. Mudança de role feita pelo admin vale a partir do próximo login/refresh.

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"marina.souza@example.com","senha":"Uga!Buga2026","lembrar":true}'
```

A resposta traz `accessToken`, `refreshToken`, `expiraEmSegundos`, `usuarioId`, `nome` e `role`.

## Endpoints

A referência completa (corpos, respostas e erros) está no Swagger. Resumo por tela:

### Home e cavernas (público)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/home` | Cavernas em destaque, aventuras, card do Banquete e depoimentos |
| GET | `/cavernas` | Busca com filtros: `checkIn`, `checkOut`, `adultos`, `idadesCriancas`, `categoria`, `precoMin`, `precoMax`, `comodidades`, `acessivel`, `ordem`, `page`, `size` |
| GET | `/cavernas/filtros` | Opções da barra de filtros + regras de tempo de estadia |
| GET | `/cavernas/{slug}` | Detalhe |
| GET | `/cavernas/{id}/disponibilidade?de=&ate=` | Calendário: tocas livres e preço de cada noite |
| GET | `/cavernas/{id}/cotacao` | Cotação da estadia |
| GET | `/cavernas/{id}/relatos` | Avaliações paginadas (com fotos) |
| GET | `/comodidades` | Lista de comodidades |

### Checkout e reserva

| Método | Rota | Descrição |
|---|---|---|
| POST | `/reservas` | Cria reserva `PENDENTE` e segura a toca por 15 min |
| PUT | `/reservas/{codigo}/hospedes` | Responsável e hóspedes (obrigatório antes de pagar) |
| POST | `/reservas/{codigo}/prorrogar` | +10 min (uma única vez) |
| GET | `/extras` | Catálogo de extras |
| PUT | `/reservas/{codigo}/extras` | Lista completa de extras (vazia = pular) |
| PUT / DELETE | `/reservas/{codigo}/combo` | Escolhe / tira o combo quarto + Banquete |
| POST / DELETE | `/reservas/{codigo}/cupom` | Aplica / remove cupom |
| POST | `/reservas/{codigo}/pagamentos` | Paga com `CARTAO`, `PIX` ou `NA_CHEGADA` |
| GET | `/pagamentos/{id}` | Consulta (o front consulta a cada 3 s enquanto espera o Pix) |
| POST | `/pagamentos/{id}/simular-confirmacao` | Confirma o Pix (só no perfil `dev`) |
| GET | `/reservas/{codigo}` | Voucher |
| GET | `/reservas/{codigo}/voucher.pdf` | Voucher em PDF |
| GET | `/reservas/{codigo}/agenda.ics` | "Adicionar à agenda" |
| GET | `/reservas/buscar?codigo=&email=` | Consulta de reserva sem conta |
| PATCH | `/reservas/{codigo}/datas` | Altera datas (até 2 dias antes do check-in) |
| POST | `/reservas/{codigo}/cancelar` | Cancela |
| POST | `/reservas/{codigo}/checkin` | Check-in online (abre 3 dias antes) |

### Avaliações

| Método | Rota | Descrição |
|---|---|---|
| POST | `/reservas/{codigo}/relato` | `multipart/form-data`: `nota` (1 a 5), `texto` e até 5 `fotos` de até 25 MB |
| GET | `/arquivos/relatos/{arquivo}` | Imagem de uma avaliação |

### Atividades

| Método | Rota | Descrição |
|---|---|---|
| GET | `/atividades` | Catálogo (filtro `categoria`) com contagem por categoria |
| GET | `/atividades/sessoes?data=` | Sessões de todas as atividades em um dia |
| GET | `/atividades/{id}` | Detalhe |
| GET | `/atividades/{id}/sessoes` | Horários e vagas (`de`, `ate`) |
| POST | `/alertas-vaga` | Botão "Avisar" |
| GET | `/reservas/{codigo}/agendamentos` | Agendamentos da reserva (com o `id` para cancelar) |
| POST | `/reservas/{codigo}/agendamentos` | Agenda sessão (reserva `CONFIRMADA`) |
| DELETE | `/reservas/{codigo}/agendamentos/{id}` | Cancela (até 4 h antes) |
| GET | `/reservas/{codigo}/agenda` | Agenda do dia (café, atividades, mesa) |

### O Banquete (restaurante)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/restaurante` | Turnos, "aberto agora", desconto de hóspede e capacidade |
| GET | `/restaurante/cardapio` | Cardápio (filtro `categoria`) |
| GET | `/restaurante/combos` | Combos quarto + Banquete |
| GET | `/restaurante/horarios?data=&pessoas=` | Horários `LIVRE` ou `LOTADO` |
| POST | `/restaurante/reservas-mesa` | Mesa de hóspede (com `codigoReserva`) ou de visitante de fora (sem) |
| GET | `/restaurante/reservas-mesa/{codigo}` | Detalhe |
| DELETE | `/restaurante/reservas-mesa/{codigo}` | Cancela (até 2 h antes) |

### Auth e conta

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/cadastro` | Cria conta (VISITANTE) |
| POST | `/auth/login` | Login |
| POST | `/auth/refresh` | Renova o access token |
| POST | `/auth/logout` | Revoga o refresh token |
| POST | `/auth/esqueci-senha` | Gera link de redefinição (30 min) |
| POST | `/auth/redefinir-senha` | Cria a nova senha com o token |
| GET / PATCH | `/me` | Perfil |
| PATCH | `/me/senha` | Troca de senha (derruba todas as sessões) |
| GET / PUT | `/me/preferencias` | Idioma e notificações |
| GET / POST | `/me/membros` | Lista / adiciona membro da tribo |
| PATCH / DELETE | `/me/membros/{id}` | Edita / remove membro |
| GET | `/me/favoritos` | Favoritos |
| POST / DELETE | `/me/favoritos/{cavernaId}` | Favoritar / desfavoritar |
| GET | `/me/pagamentos` | Histórico de pagamentos |
| GET | `/me/reservas?aba=` | `proximas`, `passadas` ou `canceladas`, com contagem por aba |

### Admin (role ADMIN)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/admin/dashboard?data=` | Ocupação, entradas/saídas, receita do mês, lotação do Banquete |
| GET | `/admin/reservas` | Lista com filtros (`status`, `de`, `ate`, `busca`) |
| GET | `/admin/ocupacao?data=` | Mapa das 25 tocas em uma noite |
| PATCH | `/admin/unidades/{id}` | Coloca/tira uma toca de manutenção |
| GET / PATCH | `/admin/cavernas`, `/admin/cavernas/{id}` | Lista / edita as suítes (preço, textos, ativa) |
| GET | `/admin/restaurante/mesas?data=` | Mesas do dia e lotação por horário |
| POST | `/admin/restaurante/mesas/{codigo}/nao-compareceu` | Marca não comparecimento |
| DELETE | `/admin/relatos/{id}` | Remove avaliação (moderação) |
| GET | `/admin/usuarios` | Lista usuários |
| PATCH | `/admin/usuarios/{id}/role` | Muda o role |

### Sistema

| Método | Rota | Descrição |
|---|---|---|
| GET | `/health` | Consulta o banco; se ele cair, responde `503 INDISPONIVEL` |

## Fluxo típico de uma reserva

1. `GET /cavernas?checkIn=2026-12-10&checkOut=2026-12-13&adultos=2` para buscar (ou `GET /cavernas/{id}/disponibilidade` para o calendário).
2. `POST /reservas` com `cavernaId`, datas, `adultos`, `idadesCriancas` e `email` (sem conta) para criar a reserva e segurar a toca.
3. `PUT /reservas/{codigo}/hospedes` com os dados do responsável e dos hóspedes.
4. Opcionais: `PUT /reservas/{codigo}/extras`, `PUT /reservas/{codigo}/combo` e `POST /reservas/{codigo}/cupom`.
5. `POST /reservas/{codigo}/pagamentos` para pagar. A reserva vira `CONFIRMADA` (e as mesas do combo também).
6. Depois de confirmada: agendar atividades, reservar mesa no Banquete, check-in online, voucher e `.ics`.
7. Depois da estadia (`CONCLUIDA`): avaliar com nota, comentário e fotos.

Reserva sem conta: envie o header `X-Responsavel-Email: seu@email.com` nas chamadas seguintes.

### Máquina de estados da reserva

```
PENDENTE ──> AGUARDANDO_PAGAMENTO ──> CONFIRMADA ──> CONCLUIDA
   │                  │                    │
   ├──> CONFIRMADA    └──> EXPIRADA        └──> CANCELADA
   ├──> EXPIRADA
   └──> CANCELADA
```

## Regras de negócio

- **Tempo de estadia:** de 1 a 30 noites, com check-in em até 365 dias. Entrada às 14h e saída às 12h (15h com late check-out). Ajustável em `hugabuga.regras` (`min-noites`, `max-noites`, `antecedencia-max-dias`).
- **Disponibilidade:** cada caverna tem várias tocas físicas; a reserva pega uma toca livre no período. O check-out de um hóspede pode coincidir com o check-in do próximo. Reserva dupla é impedida no service e, como última barreira, por uma restrição de exclusão do PostgreSQL (`btree_gist`).
- **Preço:** a diária cobre até 2 adultos; cada adulto extra soma R$ 60 por noite. Criança de até 5 anos não paga e não conta na capacidade; de 6 a 12 não soma adicional; acima de 12 conta como adulto. Feriados usam tarifa especial.
- **Total:** diárias + extras + combo − cupom. O desconto de 5% do Pix é aplicado por último, só no valor do pagamento.
- **O Banquete:** o salão tem **50 lugares**. Cada mesa ocupa o salão por 90 minutos, então mesas de horários vizinhos somam juntas. Hóspede com estadia confirmada tem 15% de desconto; visitante de fora reserva sem estadia (nome, e-mail e celular), até 60 dias à frente.
- **Combo quarto + Banquete:** jantar todas as noites da estadia, no horário escolhido, para a tribo inteira. Adulto e criança de 6 a 12 anos têm preços próprios por noite; até 5 anos não paga. As mesas seguram lugar no salão desde a escolha e são confirmadas no pagamento; se a reserva expirar ou for cancelada, os lugares voltam.
- **Avaliações:** só quem reservou com conta, depois da estadia, uma vez por estadia. Nota de 1 a 5, comentário de até 2.000 caracteres e até 5 fotos JPG/PNG/WEBP de até **25 MB** cada (o tipo é conferido pelos bytes do arquivo). A nota média da caverna é recalculada a cada avaliação.
- **Cancelamento:** com 2 dias ou mais de antecedência, reembolso integral. Fora do prazo, sem reembolso; se o pagamento seria na chegada, há multa de 1 diária.

Os valores ajustáveis ficam em `application.yml`, em `hugabuga.regras`, `hugabuga.restaurante` e `hugabuga.uploads`.

## Pagamento simulado

Nenhum dado de cartão é gravado: só os 4 últimos dígitos e a bandeira. Não registre o corpo das requisições de pagamento em log.

| Cartão | Resultado |
|---|---|
| `4111111111111111` | Aprovado (Visa, passa no Luhn) |
| Final `0002` (ex.: `4000000000000002`) | Recusado: saldo insuficiente |
| Final `0069` (ex.: `4000000000000069`) | Recusado: cartão vencido |
| Outros | Precisam passar no algoritmo de Luhn; senão, `400 VALIDACAO` |

```bash
curl -X POST http://localhost:8080/api/v1/reservas/HB-XXXX/pagamentos \
  -H "Content-Type: application/json" \
  -H "X-Responsavel-Email: cliente@example.com" \
  -d '{"metodo":"CARTAO","parcelas":3,"numeroCartao":"4111111111111111","validade":"12/30","cvv":"123","aceitoRegras":true}'
```

## Formato de erro

```json
{
  "status": 403,
  "codigo": "SEM_PERMISSAO",
  "titulo": "Uga! Essa toca não é sua.",
  "mensagem": "Você não tem permissão para isso. Nada foi alterado. ...",
  "ref": "HB-7Q2X",
  "campos": [],
  "dados": {}
}
```

`ref` também vai para o log do servidor. Códigos: `VALIDACAO` (400), `NAO_AUTENTICADO` e `SESSAO_EXPIRADA` (401), `SEM_PERMISSAO` (403), `NAO_ENCONTRADO` (404), `METODO_NAO_PERMITIDO` (405), `SEM_DISPONIBILIDADE`, `PRECO_ALTERADO`, `VAGAS_ESGOTADAS` e `CONFLITO` (409), `RESERVA_EXPIRADA` (410), `ARQUIVO_GRANDE` (413), `FORMATO_NAO_SUPORTADO` (415), `ESTADO_INVALIDO`, `FORA_DO_PRAZO`, `CUPOM_INVALIDO` e `PAGAMENTO_RECUSADO` (422), `INDISPONIVEL` (503).

## Testes

Os testes usam um PostgreSQL real via Testcontainers, então o **Docker precisa estar rodando**:

```bash
./mvnw test
```

Cobrem cálculo de total, cotação, tempo de estadia, calendário e reserva dupla, máquina de estados, pagamento simulado, cancelamento, autorização e roles, restaurante (lotação de 50 lugares e visitante de fora), combo quarto + Banquete, avaliações com fotos e "esqueci a senha".

## Estrutura do projeto

```
src/main/java/com/hugabuga/api/
├── config/        SecurityConfig, JwtFilter, WebConfig (resolve o "Solicitante"), OpenApiConfig
├── controller/    Recebem, validam e delegam (sem regra de negócio); anotados para o Swagger
├── dto/           Records de entrada e saída
├── erro/          ApiException, ErroResposta, GlobalExceptionHandler
├── model/         Entidades JPA e enums
├── repository/    Spring Data JPA
└── service/       Regras de negócio, JWT, armazenamento de fotos e tarefas agendadas
src/main/resources/
├── application.yml / application-dev.yml
└── db/migration/  V1__schema.sql, V2__dados_iniciais.sql
```

## Tarefas agendadas

`TarefasAgendadas` tem dois jobs (habilitados por `@EnableScheduling` em `MainApplication`):

- a cada 1 minuto: expira reservas `PENDENTE` e Pix vencidos, liberando a toca e os lugares do combo;
- todo dia às 03:00 (America/Sao_Paulo): conclui estadias com check-out passado (o que libera a avaliação).

## Observações

- Login com Google/Apple (botões da tela de login) não está implementado: exige credenciais OAuth dos provedores.
- "Esqueci a senha" gera o token, mas não envia e-mail (não há servidor SMTP); no perfil `dev` o token volta na resposta.
- A busca sem conta tem um `TODO` de limitar tentativas por IP (o código `HB-XXXX` é curto).
