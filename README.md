# Huga Buga Hotel API

API REST do **Huga Buga Hotel** (projeto acadêmico): busca e reserva de cavernas ("tocas"), checkout com extras, cupom e ossos (fidelidade), pagamento simulado (cartão, Pix e na chegada), atividades com vagas e o restaurante **O Banquete**.

## Tecnologias

- Java 25 e Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation)
- PostgreSQL com Flyway (migrações em `src/main/resources/db/migration`)
- JWT (JJWT 0.13) com refresh token opaco guardado como hash
- OpenPDF (voucher em PDF)
- Lombok
- Testes de integração com Testcontainers (PostgreSQL real)

## Pré-requisitos

- JDK 25
- PostgreSQL 16 (ou Docker, para subir um rapidamente)
- Docker, **obrigatório para rodar os testes**
- Não precisa instalar o Maven: use o wrapper (`./mvnw`)

## Como rodar localmente

### 1. Suba o banco

Com Docker, usando os valores padrão do perfil `dev`:

```bash
docker run -d --name hugabuga-db \
  -e POSTGRES_DB=hugabuga \
  -e POSTGRES_USER=hugabuga \
  -e POSTGRES_PASSWORD=hugabuga \
  -p 5432:5432 postgres:16-alpine
```

### 2. Inicie a API com o perfil `dev`

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

No Windows: `mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"`.

Na primeira execução o Flyway cria as tabelas (`V1__schema.sql`) e carrega os dados iniciais (`V2__dados_iniciais.sql`). A API sobe em `http://localhost:8080`.

Teste se está no ar:

```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP"}
```

### Perfil `dev`

O perfil `dev` (`application-dev.yml`):

- usa valores padrão de conexão (`localhost:5432/hugabuga`, usuário e senha `hugabuga`) e um segredo JWT de desenvolvimento;
- habilita `POST /api/v1/pagamentos/{id}/simular-confirmacao`;
- aprova o Pix sozinho 20 s depois de gerado;
- ativa logs `DEBUG` do pacote `com.hugabuga`.

## Variáveis de ambiente (produção)

Sem o perfil `dev`, a aplicação **não sobe** se faltar alguma das obrigatórias.

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DB_URL` | sim | URL JDBC, ex.: `jdbc:postgresql://host:5432/hugabuga` |
| `DB_USER` | sim | Usuário do banco |
| `DB_PASSWORD` | sim | Senha do banco |
| `JWT_SEGREDO` | sim | Segredo do JWT, **mínimo 32 caracteres** |
| `CORS_ORIGENS` | não | Origens permitidas, separadas por vírgula (padrão: `http://localhost:3000,http://localhost:5173`) |

Exemplo:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/hugabuga
export DB_USER=hugabuga
export DB_PASSWORD=sua-senha
export JWT_SEGREDO=um-segredo-longo-com-pelo-menos-32-caracteres
./mvnw clean package
java -jar target/main-0.0.1-SNAPSHOT.jar
```

> **Atenção:** `V2__dados_iniciais.sql` contém uma usuária e uma senha de teste. Não use essa carga em produção.

## Dados de teste

| Item | Valor |
|---|---|
| Usuária | `marina.souza@example.com` |
| Senha | `Uga!Buga2026` |
| Saldo de ossos | 450 |
| Cupom | `UGABUGA10` (10% até 31/12/2026) |
| Cavernas | `toca-do-mamute`, `gruta-do-casal`, `covil-dos-cacadores`, `suite-do-chefe-da-tribo` |

## Autenticação e permissões

Todas as rotas começam com `/api/v1`. Há três níveis de acesso:

- **Livre:** catálogo, busca, cotação, atividades, extras, health, cadastro, login e refresh.
- **Login:** `/me/**`, `POST /reservas/{codigo}/ossos`, logout. Envie `Authorization: Bearer <accessToken>`.
- **Dono da reserva:** rotas de `/reservas/**`, `/pagamentos/**` e `/restaurante/**` ligadas a uma reserva. Você é o dono se estiver logado com a conta que fez a reserva **ou** se enviar o header `X-Responsavel-Email` com o e-mail do responsável (reserva feita sem conta). Caso contrário, a resposta é `403 SEM_PERMISSAO`.

O access token dura 15 minutos. O refresh token dura 30 dias com `lembrar: true` e 1 dia sem isso.

### Exemplo: login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"marina.souza@example.com","senha":"Uga!Buga2026","lembrar":true}'
```

A resposta traz `accessToken`, `refreshToken`, `expiraEmSegundos`, `usuarioId` e `nome`.

## Endpoints

### Auth

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/cadastro` | Cria conta |
| POST | `/auth/login` | Login |
| POST | `/auth/refresh` | Renova o access token |
| POST | `/auth/logout` | Revoga o refresh token (exige login) |

### Cavernas (público)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/cavernas` | Busca com filtros: `checkIn`, `checkOut`, `adultos`, `idadesCriancas`, `categoria`, `precoMin`, `precoMax`, `comodidades`, `acessivel`, `ordem` (`nota`, `menor_preco`, `maior_preco`), `page`, `size` |
| GET | `/cavernas/{slug}` | Detalhe |
| GET | `/cavernas/{id}/cotacao` | Cotação da estadia (`checkIn`, `checkOut`, `adultos`, `idadesCriancas`) |
| GET | `/cavernas/{id}/relatos` | Avaliações paginadas |
| GET | `/comodidades` | Lista de comodidades |

### Reservas (checkout e gestão)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/reservas` | Cria reserva `PENDENTE` e segura a toca por 15 min |
| PUT | `/reservas/{codigo}/hospedes` | Responsável e hóspedes |
| POST | `/reservas/{codigo}/prorrogar` | +10 min (uma única vez) |
| GET | `/extras` | Catálogo de extras |
| PUT | `/reservas/{codigo}/extras` | Lista completa de extras (vazia = pular) |
| POST / DELETE | `/reservas/{codigo}/cupom` | Aplica / remove cupom |
| POST / DELETE | `/reservas/{codigo}/ossos` | Usa / desfaz ossos (exige login) |
| GET | `/reservas/buscar?codigo=&email=` | Consulta de reserva sem conta |
| GET | `/reservas/{codigo}` | Voucher |
| GET | `/reservas/{codigo}/voucher.pdf` | Voucher em PDF |
| PATCH | `/reservas/{codigo}/datas` | Altera datas (até 2 dias antes do check-in) |
| POST | `/reservas/{codigo}/cancelar` | Cancela |
| POST | `/reservas/{codigo}/checkin` | Check-in online (abre 3 dias antes) |
| POST | `/reservas/{codigo}/relato` | Avaliação (só reserva `CONCLUIDA`) |

### Pagamentos (simulados)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/reservas/{codigo}/pagamentos` | Paga com `CARTAO`, `PIX` ou `NA_CHEGADA` |
| GET | `/pagamentos/{id}` | Consulta (o front consulta a cada 3 s enquanto espera o Pix) |
| POST | `/pagamentos/{id}/simular-confirmacao` | Confirma o Pix (só no perfil `dev`; fora dele, 404) |

### Atividades

| Método | Rota | Descrição |
|---|---|---|
| GET | `/atividades` | Catálogo (filtro `categoria`) |
| GET | `/atividades/{id}` | Detalhe |
| GET | `/atividades/{id}/sessoes` | Horários e vagas (`de`, `ate`) |
| POST | `/alertas-vaga` | Botão "Avisar" |
| POST | `/reservas/{codigo}/agendamentos` | Agenda sessão (reserva `CONFIRMADA`) |
| DELETE | `/reservas/{codigo}/agendamentos/{id}` | Cancela (até 4 h antes) |
| GET | `/reservas/{codigo}/agenda` | Agenda do dia (café, atividades, mesa) |

### O Banquete (restaurante)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/restaurante` | Turnos, "aberto agora" e desconto de hóspede |
| GET | `/restaurante/cardapio` | Cardápio (filtro `categoria`) |
| GET | `/restaurante/horarios?data=&pessoas=` | Horários `LIVRE` ou `LOTADO` |
| POST | `/restaurante/reservas-mesa` | Reserva mesa (exige reserva `CONFIRMADA`) |
| GET | `/restaurante/reservas-mesa/{codigo}` | Detalhe |
| DELETE | `/restaurante/reservas-mesa/{codigo}` | Cancela (até 2 h antes) |

### Conta (`/me`, exige login)

| Método | Rota | Descrição |
|---|---|---|
| GET / PATCH | `/me` | Perfil |
| PATCH | `/me/senha` | Troca de senha (derruba todas as sessões) |
| GET / POST | `/me/membros` | Lista / adiciona membro da tribo |
| PATCH / DELETE | `/me/membros/{id}` | Edita / remove membro |
| GET | `/me/ossos` | Saldo, nível e extrato |
| GET | `/me/favoritos` | Favoritos |
| POST / DELETE | `/me/favoritos/{cavernaId}` | Favoritar / desfavoritar |
| GET | `/me/reservas?aba=` | `proximas`, `passadas` ou `canceladas` |

### Sistema

| Método | Rota | Descrição |
|---|---|---|
| GET | `/health` | Consulta o banco; se ele cair, responde `503 INDISPONIVEL` |

## Fluxo típico de uma reserva

1. `GET /cavernas?checkIn=2026-12-10&checkOut=2026-12-13&adultos=2` para buscar.
2. `POST /reservas` com `cavernaId`, datas, `adultos`, `idadesCriancas` e `email` (sem conta) para criar a reserva e segurar a toca.
3. `PUT /reservas/{codigo}/hospedes` com os dados do responsável e dos hóspedes.
4. `PUT /reservas/{codigo}/extras` (opcional) e `POST /reservas/{codigo}/cupom` (opcional).
5. `POST /reservas/{codigo}/pagamentos` para pagar. A reserva vira `CONFIRMADA`.
6. Depois de confirmada: agendar atividades, reservar mesa no Banquete, fazer check-in online e baixar o voucher.

Reserva sem conta: envie o header `X-Responsavel-Email: seu@email.com` em todas as chamadas seguintes.

```bash
curl -X POST http://localhost:8080/api/v1/reservas \
  -H "Content-Type: application/json" \
  -d '{
    "cavernaId": "<uuid da caverna>",
    "checkIn": "2026-12-10",
    "checkOut": "2026-12-13",
    "adultos": 2,
    "idadesCriancas": [],
    "email": "cliente@example.com"
  }'
```

### Máquina de estados da reserva

```
PENDENTE ──> AGUARDANDO_PAGAMENTO ──> CONFIRMADA ──> CONCLUIDA
   │                  │                    │
   ├──> CONFIRMADA    └──> EXPIRADA        └──> CANCELADA
   ├──> EXPIRADA
   └──> CANCELADA
```

## Regras de negócio

- **Preço:** a diária cobre até 2 adultos; cada adulto extra soma R$ 60 por noite. Criança de até 5 anos não paga e não conta na capacidade; de 6 a 12 não soma adicional; acima de 12 conta como adulto. Datas de feriado usam a tarifa especial.
- **Total:** diárias + extras − cupom − ossos. O desconto de 5% do Pix é aplicado por último, só no valor do pagamento.
- **Ossos:** 10 ossos = R$ 1, usados em múltiplos de 10 e cobrindo no máximo 50% do total. São debitados só na confirmação. Cada R$ 10 em diárias rende 1 osso quando a estadia é concluída.
- **Níveis:** Coletor (0), Caçador (200), Xamã (500) e Chefe da Tribo (1000), calculados pelos ossos acumulados.
- **Cancelamento:** com 2 dias ou mais de antecedência, reembolso integral e ossos devolvidos. Fora do prazo, sem reembolso; se o pagamento seria na chegada, há multa de 1 diária.
- **Reserva dupla:** impedida no service e, como última barreira, por uma restrição de exclusão do PostgreSQL (`btree_gist`). O check-out de um hóspede pode coincidir com o check-in do próximo.

Os valores ajustáveis ficam em `application.yml`, em `hugabuga.regras` (tempo de retenção, prorrogação, taxa de adulto extra, desconto do Pix etc.) e `hugabuga.restaurante` (turnos).

## Pagamento simulado

Nenhum dado de cartão é gravado: só os 4 últimos dígitos e a bandeira. Não registre o corpo das requisições de pagamento em log.

| Cartão | Resultado |
|---|---|
| `4111111111111111` | Aprovado (Visa, passa no Luhn) |
| Final `0002` (ex.: `4000000000000002`) | Recusado: saldo insuficiente |
| Final `0069` (ex.: `4000000000000069`) | Recusado: cartão vencido |
| Outros | Precisam passar no algoritmo de Luhn; senão, `400 VALIDACAO` |

Exemplo de pagamento com cartão (parcelas de 1 a 6, `aceitoRegras` obrigatório):

```bash
curl -X POST http://localhost:8080/api/v1/reservas/HB-XXXX/pagamentos \
  -H "Content-Type: application/json" \
  -H "X-Responsavel-Email: cliente@example.com" \
  -d '{"metodo":"CARTAO","parcelas":3,"numeroCartao":"4111111111111111","validade":"12/30","cvv":"123","aceitoRegras":true}'
```

Pix: `{"metodo":"PIX"}` devolve um código copia e cola válido por 10 minutos e deixa a reserva em `AGUARDANDO_PAGAMENTO`. No perfil `dev`, ele é aprovado após 20 s ou via `simular-confirmacao`.

## Formato de erro

Toda resposta de erro segue o mesmo formato:

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

`ref` também vai para o log do servidor, para rastrear o problema. Códigos principais: `VALIDACAO` (400), `NAO_AUTENTICADO` e `SESSAO_EXPIRADA` (401), `SEM_PERMISSAO` (403), `NAO_ENCONTRADO` (404), `SEM_DISPONIBILIDADE`, `PRECO_ALTERADO` e `VAGAS_ESGOTADAS` (409), `RESERVA_EXPIRADA` (410), `ESTADO_INVALIDO`, `FORA_DO_PRAZO`, `CUPOM_INVALIDO` e `PAGAMENTO_RECUSADO` (422), `INDISPONIVEL` (503).

## Testes

Os testes usam um PostgreSQL real via Testcontainers, então o **Docker precisa estar rodando**:

```bash
./mvnw test
```

Cobrem cálculo de total, cotação, disponibilidade e reserva dupla, máquina de estados, pagamento simulado, cancelamento e autorização (usuário A não acessa a reserva do usuário B).

## Estrutura do projeto

```
src/main/java/com/hugabuga/api/
├── config/        SecurityConfig, JwtFilter, WebConfig (resolve o "Solicitante")
├── controller/    Recebem, validam e delegam (sem regra de negócio)
├── dto/           Records de entrada e saída
├── erro/          ApiException, ErroResposta, GlobalExceptionHandler
├── model/         Entidades JPA e enums
├── repository/    Spring Data JPA
└── service/       Regras de negócio, JWT e tarefas agendadas
src/main/resources/
├── application.yml / application-dev.yml
└── db/migration/  V1__schema.sql, V2__dados_iniciais.sql
```

## Tarefas agendadas

`TarefasAgendadas` tem dois jobs:

- a cada 1 minuto: expira reservas `PENDENTE` e Pix vencidos, liberando a toca;
- todo dia às 03:00 (America/Sao_Paulo): conclui estadias com check-out passado e credita os ossos.

> **Atenção:** a classe `MainApplication` não tem `@EnableScheduling`. Para os jobs rodarem de verdade, adicione essa anotação nela.

## Observações

- A documentação interativa (`/api/v1/docs`, Swagger) está liberada no `SecurityConfig`, mas o projeto ainda não inclui uma dependência como o springdoc. Se quiser Swagger, é preciso adicioná-la.
- Os testes e a busca sem conta têm um `TODO` de limitar tentativas por IP (o código `HB-XXXX` é curto).