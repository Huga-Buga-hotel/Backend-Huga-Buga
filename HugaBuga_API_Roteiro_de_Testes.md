# Huga Buga Hotel API: roteiro de testes (rota, body, resultado esperado e erro)

> Os resultados esperados foram escritos a partir do código (services, DTOs e handlers). Ids, datas, tokens e códigos são **exemplos**: os seus serão outros. Os horários podem vir com o fuso `-03:00` ou em UTC (`Z`). As mensagens de campo da validação (`campos[].erro`) seguem o idioma da JVM.

## Antes de começar

| Item | Valor |
|---|---|
| URL base | `http://localhost:8080/api/v1` |
| Subir a API | `mvn spring-boot:run -Dspring-boot.run.profiles=dev` |
| Login de teste | `marina.souza@example.com` / `Uga!Buga2026` (450 ossos, tribo: Rafael, Theo e Lia) |
| Content-Type | `application/json` nos bodies |
| Dinheiro | sempre em **centavos** (`52000` = R$ 520,00) |
| Datas | ISO: `2026-12-01`; horas: `20:00` na entrada e `20:00:00` na saída |
| Cupom | `UGABUGA10` (10%, até 31/12/2026) |
| Cartões | `4111111111111111` aprova · final `0002` recusa (saldo) · final `0069` recusa (vencido) · número inválido no Luhn = 400 |
| Cavernas | `toca-do-mamute` R$ 520 (cap. 5), `gruta-do-casal` R$ 390 (cap. 2), `covil-dos-cacadores` R$ 610 (cap. 6), `suite-do-chefe-da-tribo` R$ 980 (cap. 8) |

**Como a rota sabe quem você é (acesso):**

- **livre**: sem token.
- **login**: `Authorization: Bearer <accessToken>` (pegue no login).
- **dono**: o token da dona da reserva **ou** o header `X-Responsavel-Email: <e-mail do responsável>` (para reserva feita sem conta). Qualquer outra pessoa recebe 403.

**Fluxo mínimo de uma reserva:** 1.2 Login → 3.2 Detalhe (pegue o `id` da caverna) → 4.1 Criar → 4.2 Hóspedes → 4.5 Extras → 4.6 Cupom → 4.10 Pagar → 4.15 Voucher.

## Formato padrão de erro

Toda resposta de erro tem o mesmo formato. `campos` só aparece em erro de validação e `dados` só quando o erro traz informação extra (datas sugeridas, preço antigo e novo...). A `ref` também fica gravada no log do servidor, para rastrear o problema.

```json
{
  "status": 422,
  "codigo": "CODIGO_DO_ERRO",
  "titulo": "Título curto",
  "mensagem": "O que aconteceu, o que NÃO aconteceu e o próximo passo.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "nomeDoCampo",
      "erro": "motivo"
    }
  ],
  "dados": {
    "chave": "valor"
  }
}
```

| HTTP | `codigo` | Quando |
|---|---|---|
| 400 | `VALIDACAO` | Campos inválidos, JSON ilegível, parâmetro faltando ou com tipo errado |
| 401 | `NAO_AUTENTICADO` | Sem token, token inválido, login errado |
| 401 | `SESSAO_EXPIRADA` | Token ou refresh vencido: renove com `/auth/refresh` |
| 403 | `SEM_PERMISSAO` | Reserva de outra pessoa |
| 404 | `NAO_ENCONTRADO` | Caverna, reserva, mesa... ou rota inexistente |
| 409 | `SEM_DISPONIBILIDADE` | Toca ocupada entre a busca e o clique (traz datas sugeridas) |
| 409 | `PRECO_ALTERADO` | O preço mudou desde a busca |
| 409 | `VAGAS_ESGOTADAS` | Atividade ou horário de mesa lotou |
| 410 | `RESERVA_EXPIRADA` | Ação em reserva cujo tempo acabou |
| 422 | `PAGAMENTO_RECUSADO` | Cartão recusado ("Nada foi cobrado") |
| 422 | `CUPOM_INVALIDO` | Cupom inexistente, vencido ou esgotado |
| 422 | `FORA_DO_PRAZO` | Cancelar/alterar fora do prazo, check-in antes de liberar |
| 422 | `ESTADO_INVALIDO` | Ação que o estado atual da reserva/pagamento não permite |
| 503 | `INDISPONIVEL` | Banco fora do ar ou falha inesperada |

Nos exemplos de erro abaixo, `ref` aparece sempre como `HB-7Q2X`; na prática ela muda a cada resposta.

## Índice

- **1. Sistema e Auth**
  - 1.1 `GET /health`: Status da API
  - 1.2 `POST /auth/login`: Login
  - 1.3 `POST /auth/cadastro`: Cadastro
  - 1.4 `POST /auth/refresh`: Renovar o token de acesso
  - 1.5 `POST /auth/logout`: Logout (revoga o refresh)
- **2. Conta (/me): tudo exige login**
  - 2.1 `GET /me`: Perfil
  - 2.2 `PATCH /me`: Editar perfil (só o que for enviado muda)
  - 2.3 `PATCH /me/senha`: Trocar senha (derruba todas as sessões)
  - 2.4 `GET /me/membros`: Listar membros da tribo
  - 2.5 `POST /me/membros`: Adicionar membro
  - 2.6 `PATCH /me/membros/{id}`: Editar membro (envie todos os campos)
  - 2.7 `DELETE /me/membros/{id}`: Remover membro
  - 2.8 `GET /me/ossos`: Ossos: saldo, nível e extrato
  - 2.9 `GET /me/favoritos`: Listar favoritos
  - 2.10 `POST /me/favoritos/{cavernaId}`: Favoritar caverna
  - 2.11 `DELETE /me/favoritos/{cavernaId}`: Desfavoritar caverna
  - 2.12 `GET /me/reservas`: Minhas reservas (aba = proximas | passadas | canceladas)
- **3. Cavernas (público)**
  - 3.1 `GET /cavernas`: Busca com datas e filtros
  - 3.2 `GET /cavernas/toca-do-mamute`: Detalhe da caverna
  - 3.3 `GET /cavernas/{id}/cotacao`: Cotação (o quadro "R$ 520 × 3 noites")
  - 3.4 `GET /cavernas/{id}/relatos`: Relatos (10 por página)
  - 3.5 `GET /comodidades`: Comodidades (para o filtro)
- **4. Reserva e checkout (rode na ordem)**
  - 4.1 `POST /reservas`: Criar reserva (segura a toca por 15 min)
  - 4.2 `PUT /reservas/{codigo}/hospedes`: Passo 1: quem vem (hóspedes)
  - 4.3 `POST /reservas/{codigo}/prorrogar`: +10 minutos (uma única vez)
  - 4.4 `GET /extras`: Catálogo de extras
  - 4.5 `PUT /reservas/{codigo}/extras`: Passo 2: escolher extras (lista completa; vazia = "Pular extras")
  - 4.6 `POST /reservas/{codigo}/cupom`: Passo 3: aplicar cupom
  - 4.7 `DELETE /reservas/{codigo}/cupom`: Remover cupom
  - 4.8 `POST /reservas/{codigo}/ossos`: Usar ossos (múltiplos de 10, até 50% do total; exige login)
  - 4.9 `DELETE /reservas/{codigo}/ossos`: Desfazer ossos
  - 4.10 `POST /reservas/{codigo}/pagamentos`: Pagar: cartão (aprovado na hora)
  - 4.11 `POST /reservas/{codigo}/pagamentos`: Pagar: Pix (5% de desconto, expira em 10 min)
  - 4.12 `POST /reservas/{codigo}/pagamentos`: Pagar: na chegada (confirma sem cobrar)
  - 4.13 `GET /pagamentos/{id}`: Status do pagamento (o front consulta a cada 3 s no Pix)
  - 4.14 `POST /pagamentos/{id}/simular-confirmacao`: Confirmar o Pix de mentira (só perfil dev)
  - 4.15 `GET /reservas/{codigo}`: Voucher (resumo completo)
  - 4.16 `GET /reservas/{codigo}/voucher.pdf`: Voucher em PDF ("Baixar tábua")
  - 4.17 `GET /reservas/buscar`: Reserva sem conta: buscar por código e e-mail
  - 4.18 `POST /reservas/{codigo}/checkin`: Check-in online (libera 3 dias antes da chegada)
  - 4.19 `PATCH /reservas/{codigo}/datas`: Alterar datas (recalcula o preço)
  - 4.20 `POST /reservas/{codigo}/cancelar`: Cancelar (grátis até 2 dias antes do check-in)
  - 4.21 `POST /reservas/{codigo}/relato`: Relato pós-estadia (nota 1 a 5)
- **5. Atividades**
  - 5.1 `GET /atividades`: Catálogo (filtro opcional: `?categoria=CRIANCAS`)
  - 5.2 `GET /atividades/{id}`: Detalhe da atividade
  - 5.3 `GET /atividades/{id}/sessoes`: Sessões com vagas
  - 5.4 `POST /reservas/{codigo}/agendamentos`: Agendar uma vaga
  - 5.5 `DELETE /reservas/{codigo}/agendamentos/{id}`: Cancelar agendamento (até 4 h antes; devolve a vaga)
  - 5.6 `GET /reservas/{codigo}/agenda`: Agenda da tribo no dia (café, atividades e mesa)
  - 5.7 `POST /alertas-vaga`: Avisar quando abrir vaga (opcional)
- **6. O Banquete (restaurante)**
  - 6.1 `GET /restaurante`: Turnos, "aberto agora" e desconto de hóspede
  - 6.2 `GET /restaurante/cardapio`: Cardápio (filtro opcional: `?categoria=NA_FOGUEIRA`)
  - 6.3 `GET /restaurante/horarios`: Horários LIVRE ou LOTADO
  - 6.4 `POST /restaurante/reservas-mesa`: Reservar mesa ("Uga! Reservar mesa")
  - 6.5 `GET /restaurante/reservas-mesa/{codigo}`: Detalhe da mesa
  - 6.6 `DELETE /restaurante/reservas-mesa/{codigo}`: Cancelar mesa (até 2 h antes)
- **7. Erros que podem acontecer em qualquer rota**

---

## 1. Sistema e Auth

### 1.1 GET `/health`

**Status da API**  ·  Acesso: livre

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "status": "UP"
}
```

**Body com erro: Banco de dados fora do ar (pare o PostgreSQL e chame a rota)**

(não tem body)

**Resultado esperado: 503 Service Unavailable**

```json
{
  "status": 503,
  "codigo": "INDISPONIVEL",
  "titulo": "Ugh! A caverna desabou por um instante.",
  "mensagem": "Algo falhou do nosso lado e o seu pedido pode não ter sido concluído. Confira em Minhas reservas antes de repetir. Tente de novo em instantes; se persistir, informe o código HB-7Q2X.",
  "ref": "HB-7Q2X"
}
```

### 1.2 POST `/auth/login`

**Login**  ·  Acesso: livre

**Body**

```json
{
  "email": "marina.souza@example.com",
  "senha": "Uga!Buga2026",
  "lembrar": true
}
```

**Resultado esperado: 200 OK**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjMGE4MDEw...",
  "refreshToken": "q8Zk3vN1xT0b-Ur7mWc2YpLd5EaHsJf9oGtRiKnVhXw",
  "expiraEmSegundos": 900,
  "usuarioId": "c0a80101-5d2e-4b6f-8a11-0f3c9e7d21a1",
  "nome": "Marina"
}
```

**Body com erro (1/2): Senha errada (e-mail inexistente responde exatamente igual)**

```json
{
  "email": "marina.souza@example.com",
  "senha": "senha-errada",
  "lembrar": false
}
```

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "NAO_AUTENTICADO",
  "titulo": "Uga! Não reconhecemos você.",
  "mensagem": "E-mail ou senha incorretos. Você não foi conectado. Confira os dados e tente de novo.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Campos inválidos (e-mail mal formado, senha vazia)**

```json
{
  "email": "isso-nao-e-email",
  "senha": "",
  "lembrar": false
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "email",
      "erro": "deve ser um endereço de e-mail bem formado"
    },
    {
      "campo": "senha",
      "erro": "não deve estar em branco"
    }
  ]
}
```

### 1.3 POST `/auth/cadastro`

**Cadastro**  ·  Acesso: livre

> A senha precisa ter 8+ caracteres e o e-mail é único (sem diferenciar maiúsculas).

**Body**

```json
{
  "nome": "Ana",
  "sobrenome": "Silva",
  "email": "ana.silva@example.com",
  "senha": "Senha@123",
  "celular": "+55 11 91111-2222",
  "pais": "BR",
  "tipoDocumento": "CPF",
  "numeroDocumento": "52998224725",
  "dataNascimento": "1995-05-05",
  "aceitaOfertas": true
}
```

**Resultado esperado: 201 Created**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjMGE4MDEw...",
  "refreshToken": "q8Zk3vN1xT0b-Ur7mWc2YpLd5EaHsJf9oGtRiKnVhXw",
  "expiraEmSegundos": 900,
  "usuarioId": "c0a80101-5d2e-4b6f-8a11-0f3c9e7d21a1",
  "nome": "Ana"
}
```

**Body com erro (1/2): E-mail já cadastrado (use o da Marina)**

```json
{
  "nome": "Ana",
  "sobrenome": "Silva",
  "email": "marina.souza@example.com",
  "senha": "Senha@123",
  "celular": "+55 11 91111-2222",
  "pais": "BR",
  "tipoDocumento": "CPF",
  "numeroDocumento": "52998224725",
  "dataNascimento": "1995-05-05",
  "aceitaOfertas": true
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Esse e-mail já tem toca.",
  "mensagem": "Já existe uma conta com este e-mail. Nenhuma conta nova foi criada. Entre na sua conta ou use outro e-mail.",
  "ref": "HB-7Q2X",
  "dados": {
    "campos": [
      {
        "campo": "email",
        "erro": "E-mail já cadastrado"
      }
    ]
  }
}
```

**Body com erro (2/2): Senha curta e campos faltando**

```json
{
  "nome": "Ana",
  "email": "ana2@example.com",
  "senha": "123"
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "senha",
      "erro": "o tamanho deve ser entre 8 e 2147483647"
    },
    {
      "campo": "sobrenome",
      "erro": "não deve estar em branco"
    },
    {
      "campo": "celular",
      "erro": "não deve estar em branco"
    },
    {
      "campo": "pais",
      "erro": "não deve estar em branco"
    },
    {
      "campo": "tipoDocumento",
      "erro": "não deve estar em branco"
    },
    {
      "campo": "numeroDocumento",
      "erro": "não deve estar em branco"
    }
  ]
}
```

### 1.4 POST `/auth/refresh`

**Renovar o token de acesso**  ·  Acesso: livre

**Body**

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

**Resultado esperado: 200 OK**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.novo...",
  "refreshToken": "q8Zk3vN1xT0b-Ur7mWc2YpLd5EaHsJf9oGtRiKnVhXw",
  "expiraEmSegundos": 900,
  "usuarioId": "c0a80101-5d2e-4b6f-8a11-0f3c9e7d21a1",
  "nome": "Marina"
}
```

**Body com erro: Refresh token inexistente, vencido ou revogado**

```json
{
  "refreshToken": "token-invalido"
}
```

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "SESSAO_EXPIRADA",
  "titulo": "Uga! Sua sessão acabou.",
  "mensagem": "Sua sessão expirou. Nada foi alterado. Entre novamente para continuar.",
  "ref": "HB-7Q2X"
}
```

### 1.5 POST `/auth/logout`

**Logout (revoga o refresh)**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

**Body**

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

**Resultado esperado: 204 No Content**

(sem corpo)

**Body com erro: Sem o header Authorization**

Headers: (sem Authorization)

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "NAO_AUTENTICADO",
  "titulo": "Uga! Quem é você?",
  "mensagem": "Você precisa entrar na sua conta para fazer isso. Nada foi alterado. Faça login e tente de novo.",
  "ref": "HB-7Q2X"
}
```

---

## 2. Conta (/me): tudo exige login

### 2.1 GET `/me`

**Perfil**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

> O documento sai mascarado (só os 4 últimos) e a senha nunca aparece.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "id": "c0a80101-5d2e-4b6f-8a11-0f3c9e7d21a1",
  "nome": "Marina",
  "sobrenome": "Souza",
  "email": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "pais": "BR",
  "tipoDocumento": "CPF",
  "documentoMascarado": "•••• 8909",
  "dataNascimento": "1989-04-12",
  "aceitaOfertas": true,
  "saldoOssos": 450,
  "nivel": "Xamã",
  "idioma": "PT"
}
```

**Body com erro: Token inválido**

Headers: `Authorization: Bearer abc.def.ghi`

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "NAO_AUTENTICADO",
  "titulo": "Uga! Quem é você?",
  "mensagem": "Você precisa entrar na sua conta para fazer isso. Nada foi alterado. Faça login e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 2.2 PATCH `/me`

**Editar perfil (só o que for enviado muda)**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

**Body**

```json
{
  "celular": "+55 11 97777-0000",
  "aceitaOfertas": false
}
```

**Resultado esperado: 200 OK**

```json
{
  "id": "c0a80101-5d2e-4b6f-8a11-0f3c9e7d21a1",
  "nome": "Marina",
  "sobrenome": "Souza",
  "email": "marina.souza@example.com",
  "celular": "+55 11 97777-0000",
  "pais": "BR",
  "tipoDocumento": "CPF",
  "documentoMascarado": "•••• 8909",
  "dataNascimento": "1989-04-12",
  "aceitaOfertas": false,
  "saldoOssos": 450,
  "nivel": "Xamã",
  "idioma": "PT"
}
```

**Body com erro: Data de nascimento no futuro**

```json
{
  "dataNascimento": "2999-01-01"
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "A data de nascimento está no futuro. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

### 2.3 PATCH `/me/senha`

**Trocar senha (derruba todas as sessões)**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

> Depois de trocar, o refresh token antigo deixa de valer e a Marina passa a entrar com a senha nova.

**Body**

```json
{
  "senhaAtual": "Uga!Buga2026",
  "novaSenha": "NovaSenha@2027"
}
```

**Resultado esperado: 204 No Content**

(sem corpo)

**Body com erro (1/2): Senha atual errada**

```json
{
  "senhaAtual": "errada",
  "novaSenha": "NovaSenha@2027"
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "A senha atual está incorreta. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Nova senha curta**

```json
{
  "senhaAtual": "Uga!Buga2026",
  "novaSenha": "123"
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "novaSenha",
      "erro": "o tamanho deve ser entre 8 e 2147483647"
    }
  ]
}
```

### 2.4 GET `/me/membros`

**Listar membros da tribo**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

> A idade é calculada na hora, nunca armazenada.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
[
  {
    "id": "9b2d7e41-6a3c-4f58-b0d9-1e8a4c6f3d72",
    "nome": "Rafael",
    "tipo": "ADULTO",
    "dataNascimento": "1987-09-03",
    "idade": 39,
    "observacoes": null
  },
  {
    "id": "9b2d7e41-6a3c-4f58-b0d9-1e8a4c6f3d72",
    "nome": "Theo",
    "tipo": "CRIANCA",
    "dataNascimento": "2017-10-26",
    "idade": 9,
    "observacoes": "Alergia a amendoim"
  },
  {
    "id": "9b2d7e41-6a3c-4f58-b0d9-1e8a4c6f3d72",
    "nome": "Lia",
    "tipo": "CRIANCA",
    "dataNascimento": "2022-10-26",
    "idade": 4,
    "observacoes": null
  }
]
```

**Body com erro: Sem token**

Headers: (sem Authorization)

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "NAO_AUTENTICADO",
  "titulo": "Uga! Quem é você?",
  "mensagem": "Você precisa entrar na sua conta para fazer isso. Nada foi alterado. Faça login e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 2.5 POST `/me/membros`

**Adicionar membro**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

**Body**

```json
{
  "nome": "Bento",
  "tipo": "CRIANCA",
  "dataNascimento": "2020-03-10",
  "observacoes": "Gosta de dinossauros"
}
```

**Resultado esperado: 201 Created**

```json
{
  "id": "9b2d7e41-6a3c-4f58-b0d9-1e8a4c6f3d72",
  "nome": "Bento",
  "tipo": "CRIANCA",
  "dataNascimento": "2020-03-10",
  "idade": 6,
  "observacoes": "Gosta de dinossauros"
}
```

**Body com erro (1/2): Criança sem data de nascimento**

```json
{
  "nome": "Bento",
  "tipo": "CRIANCA"
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Informe a data de nascimento da criança. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Campos obrigatórios faltando**

```json
{}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "nome",
      "erro": "não deve estar em branco"
    },
    {
      "campo": "tipo",
      "erro": "não deve ser nulo"
    }
  ]
}
```

### 2.6 PATCH `/me/membros/{id}`

**Editar membro (envie todos os campos)**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

**Body**

```json
{
  "nome": "Bento Souza",
  "tipo": "CRIANCA",
  "dataNascimento": "2020-03-10",
  "observacoes": "Sem restrições"
}
```

**Resultado esperado: 200 OK**

```json
{
  "id": "9b2d7e41-6a3c-4f58-b0d9-1e8a4c6f3d72",
  "nome": "Bento Souza",
  "tipo": "CRIANCA",
  "dataNascimento": "2020-03-10",
  "idade": 6,
  "observacoes": "Sem restrições"
}
```

**Body com erro: Id que não é da sua tribo (ou inexistente)**

Rota: `PATCH /me/membros/00000000-0000-0000-0000-000000000000`

```json
{
  "nome": "Bento Souza",
  "tipo": "CRIANCA",
  "dataNascimento": "2020-03-10"
}
```

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Membro da tribo não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 2.7 DELETE `/me/membros/{id}`

**Remover membro**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

**Body**

(não tem body)

**Resultado esperado: 204 No Content**

(sem corpo)

**Body com erro: Id inexistente**

Rota: `DELETE /me/membros/00000000-0000-0000-0000-000000000000`

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Membro da tribo não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 2.8 GET `/me/ossos`

**Ossos: saldo, nível e extrato**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

> O nível usa o total ACUMULADO de ganhos (520), não o saldo (450): por isso nunca cai.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "saldo": 450,
  "acumulado": 520,
  "nivel": "Xamã",
  "proximoNivel": "Chefe da Tribo",
  "faltam": 480,
  "extrato": [
    {
      "quantidade": -70,
      "motivo": "RESGATE",
      "reservaId": null,
      "criadoEm": "2026-10-26T18:00:00-03:00"
    },
    {
      "quantidade": 520,
      "motivo": "GANHO_ESTADIA",
      "reservaId": null,
      "criadoEm": "2026-10-26T18:00:00-03:00"
    }
  ]
}
```

**Body com erro: Sem token**

Headers: (sem Authorization)

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "NAO_AUTENTICADO",
  "titulo": "Uga! Quem é você?",
  "mensagem": "Você precisa entrar na sua conta para fazer isso. Nada foi alterado. Faça login e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 2.9 GET `/me/favoritos`

**Listar favoritos**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
[
  {
    "id": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
    "slug": "toca-do-mamute",
    "nome": "Toca do Mamute",
    "categoria": "FAMILIA",
    "ala": "Ala do Vale",
    "resumo": "A toca preferida das tribos grandes: espaço de sobra e fogueira só sua.",
    "capacidade": 5,
    "camas": 3,
    "areaM2": 42,
    "precoBaseCentavos": 52000,
    "totalEstadiaCentavos": null,
    "notaMedia": 4.9,
    "totalRelatos": 128,
    "acessivel": true,
    "aceitaPets": false,
    "fotoUrl": "https://cdn.hugabuga.example/cavernas/toca-do-mamute/1.jpg",
    "unidadesLivres": null,
    "ultimas": false,
    "comodidades": [
      "fogueira-privativa",
      "vista-pro-vale",
      "berco-de-pele",
      "rede-de-descanso"
    ]
  }
]
```

**Body com erro: Sem token**

Headers: (sem Authorization)

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "NAO_AUTENTICADO",
  "titulo": "Uga! Quem é você?",
  "mensagem": "Você precisa entrar na sua conta para fazer isso. Nada foi alterado. Faça login e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 2.10 POST `/me/favoritos/{cavernaId}`

**Favoritar caverna**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

> Repetir a chamada não dá erro (é idempotente).

**Body**

(não tem body)

**Resultado esperado: 204 No Content**

(sem corpo)

**Body com erro: Caverna inexistente**

Rota: `POST /me/favoritos/00000000-0000-0000-0000-000000000000`

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Caverna não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 2.11 DELETE `/me/favoritos/{cavernaId}`

**Desfavoritar caverna**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

**Body**

(não tem body)

**Resultado esperado: 204 No Content**

(sem corpo)

**Body com erro: Id que não é um UUID**

Rota: `DELETE /me/favoritos/abc`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O valor informado para 'cavernaId' não é válido. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "cavernaId",
      "erro": "valor inválido"
    }
  ]
}
```

### 2.12 GET `/me/reservas?aba=proximas`

**Minhas reservas (aba = proximas | passadas | canceladas)**  ·  Acesso: login

Headers: `Authorization: Bearer {{token}}`

> Reservas PENDENTE e EXPIRADA nunca aparecem em nenhuma aba.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "estadias": [
    {
      "codigo": "HB-4F7K",
      "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
      "cavernaNome": "Toca do Mamute",
      "checkIn": "2026-12-01",
      "checkOut": "2026-12-04",
      "status": "CONFIRMADA",
      "total": 161100
    }
  ],
  "mesas": [
    {
      "codigo": "BQ-9XQ2",
      "data": "2026-12-02",
      "horario": "20:00:00",
      "pessoas": 4,
      "status": "CONFIRMADA"
    }
  ]
}
```

**Body com erro: Aba inexistente**

Rota: `GET /me/reservas?aba=futuras`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "A aba precisa ser proximas, passadas ou canceladas. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

---

## 3. Cavernas (público)

### 3.1 GET `/cavernas?checkIn=2026-12-01&checkOut=2026-12-04&adultos=2&idadesCriancas=9&idadesCriancas=4&ordem=menor_preco&page=0&size=10`

**Busca com datas e filtros**  ·  Acesso: livre

> Filtros opcionais: `categoria` (FAMILIA, CASAL, AMIGOS, GRUPO; repetível), `precoMin`/`precoMax` (centavos), `comodidades` (repetível), `acessivel=true`, `ordem` (menor_preco, maior_preco, nota). `unidadesLivres <= 2` liga `"ultimas": true` (selo "Últimas 2!"). Sem nenhuma toca livre: `itens` vem vazio e `sugestoes` traz datas próximas. Sem datas, `totalEstadiaCentavos` e `unidadesLivres` vêm `null`.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "total": 4,
  "filtrosAtivos": [],
  "avisos": [],
  "itens": [
    {
      "id": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
      "slug": "toca-do-mamute",
      "nome": "Toca do Mamute",
      "categoria": "FAMILIA",
      "ala": "Ala do Vale",
      "resumo": "A toca preferida das tribos grandes: espaço de sobra e fogueira só sua.",
      "capacidade": 5,
      "camas": 3,
      "areaM2": 42,
      "precoBaseCentavos": 52000,
      "totalEstadiaCentavos": 156000,
      "notaMedia": 4.9,
      "totalRelatos": 128,
      "acessivel": true,
      "aceitaPets": false,
      "fotoUrl": "https://cdn.hugabuga.example/cavernas/toca-do-mamute/1.jpg",
      "unidadesLivres": 3,
      "ultimas": false,
      "comodidades": [
        "fogueira-privativa",
        "vista-pro-vale",
        "berco-de-pele",
        "rede-de-descanso"
      ]
    }
  ],
  "sugestoes": []
}
```

**Body com erro (1/2): Só check-in, sem check-out**

Rota: `GET /cavernas?checkIn=2026-12-01`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Informe check-in e check-out juntos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Categoria inexistente**

Rota: `GET /cavernas?categoria=XYZ`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O valor informado para 'categoria' não é válido. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "categoria",
      "erro": "valor inválido"
    }
  ]
}
```

### 3.2 GET `/cavernas/toca-do-mamute`

**Detalhe da caverna**  ·  Acesso: livre

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "caverna": {
    "id": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
    "slug": "toca-do-mamute",
    "nome": "Toca do Mamute",
    "categoria": "FAMILIA",
    "ala": "Ala do Vale",
    "resumo": "A toca preferida das tribos grandes: espaço de sobra e fogueira só sua.",
    "capacidade": 5,
    "camas": 3,
    "areaM2": 42,
    "precoBaseCentavos": 52000,
    "totalEstadiaCentavos": null,
    "notaMedia": 4.9,
    "totalRelatos": 128,
    "acessivel": true,
    "aceitaPets": false,
    "fotoUrl": "https://cdn.hugabuga.example/cavernas/toca-do-mamute/1.jpg",
    "unidadesLivres": null,
    "ultimas": false,
    "comodidades": [
      "fogueira-privativa",
      "vista-pro-vale",
      "berco-de-pele",
      "rede-de-descanso"
    ]
  },
  "descricao": "Escavada na rocha com vista para o vale, a Toca do Mamute acomoda até 5 pessoas...",
  "fotos": [
    {
      "url": "https://cdn.hugabuga.example/cavernas/toca-do-mamute/1.jpg",
      "legenda": "Vista da caverna"
    }
  ],
  "comodidades": [
    {
      "id": "fogueira-privativa",
      "nome": "Fogueira privativa",
      "icone": "local_fire_department"
    }
  ]
}
```

**Body com erro: Slug inexistente**

Rota: `GET /cavernas/nao-existe`

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Caverna não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 3.3 GET `/cavernas/{id}/cotacao?checkIn=2026-12-01&checkOut=2026-12-04&adultos=2&idadesCriancas=9&idadesCriancas=4`

**Cotação (o quadro "R$ 520 × 3 noites")**  ·  Acesso: livre

> Regras: a diária cobre 2 adultos; cada adulto a mais soma R$ 60/noite; criança até 5 anos é grátis; acima de 12 conta como adulto; tarifa de feriado troca o preço da noite e liga `feriado`.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "noites": 3,
  "adultosCobrados": 2,
  "adultosExtras": 0,
  "criancasGratis": 1,
  "valorDiariasCentavos": 156000,
  "detalhe": [
    {
      "data": "2026-12-01",
      "precoNoiteCentavos": 52000,
      "adicionalAdultosCentavos": 0
    },
    {
      "data": "2026-12-02",
      "precoNoiteCentavos": 52000,
      "adicionalAdultosCentavos": 0
    },
    {
      "data": "2026-12-03",
      "precoNoiteCentavos": 52000,
      "adicionalAdultosCentavos": 0
    }
  ],
  "feriado": false
}
```

**Body com erro (1/3): Grupo maior que a capacidade (6 adultos na Toca, capacidade 5)**

Rota: `GET /cavernas/{id}/cotacao?checkIn=2026-12-01&checkOut=2026-12-04&adultos=6`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "A Toca do Mamute comporta até 5 pessoas acima de 5 anos, e o grupo tem 6. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/3): Check-out igual ao check-in**

Rota: `GET /cavernas/{id}/cotacao?checkIn=2026-12-01&checkOut=2026-12-01&adultos=2`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O check-out precisa ser depois do check-in. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (3/3): Falta o parâmetro checkOut**

Rota: `GET /cavernas/{id}/cotacao?checkIn=2026-12-01`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Falta o parâmetro 'checkOut'. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "checkOut",
      "erro": "obrigatório"
    }
  ]
}
```

### 3.4 GET `/cavernas/{id}/relatos?page=0`

**Relatos (10 por página)**  ·  Acesso: livre

> Sem relatos ainda, vem `"itens": []` e `"total": 0`.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "itens": [
    {
      "nota": 5,
      "texto": "Melhor toca da tribo!",
      "autor": "Marina S.",
      "criadoEm": "2026-12-10T09:00:00-03:00"
    }
  ],
  "page": 0,
  "size": 10,
  "total": 1
}
```

**Body com erro: Caverna inexistente**

Rota: `GET /cavernas/00000000-0000-0000-0000-000000000000/relatos`

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Caverna não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 3.5 GET `/comodidades`

**Comodidades (para o filtro)**  ·  Acesso: livre

> Rota só de leitura: não há body que gere erro. Chamada sem token com outro método (ex.: `POST`) responde 401.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
[
  {
    "id": "fogueira-privativa",
    "nome": "Fogueira privativa",
    "icone": "local_fire_department"
  }
]
```

**Body com erro:** esta rota não tem entrada que gere erro de negócio.

---

## 4. Reserva e checkout (rode na ordem)

### 4.1 POST `/reservas`

**Criar reserva (segura a toca por 15 min)**  ·  Acesso: livre (com token a reserva fica na conta; sem token envie `email`)

> Opcionais: `email` (obrigatório sem login) e `precoEsperadoCentavos` (se o preço calculado for outro, dá 409). O servidor sempre recalcula o preço.

**Body**

```json
{
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "adultos": 2,
  "idadesCriancas": [
    9,
    4
  ]
}
```

**Resultado esperado: 201 Created**

```json
{
  "codigo": "HB-4F7K",
  "status": "PENDENTE",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:15:00-03:00",
  "prorrogada": false,
  "responsavelNome": null,
  "responsavelEmail": "marina.souza@example.com",
  "celular": null,
  "hospedes": [],
  "extras": [],
  "cupom": null,
  "pedidoEspecial": null,
  "lateCheckout": false,
  "valorDiarias": 156000,
  "valorExtras": 0,
  "desconto": 0,
  "ossosUsados": 0,
  "total": 156000,
  "pago": false,
  "checkinOnlineEm": null
}
```

**Body com erro (1/5): Body vazio**

```json
{}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "cavernaId",
      "erro": "não deve ser nulo"
    },
    {
      "campo": "checkIn",
      "erro": "não deve ser nulo"
    },
    {
      "campo": "checkOut",
      "erro": "não deve ser nulo"
    },
    {
      "campo": "adultos",
      "erro": "deve ser maior que ou igual a 1"
    }
  ]
}
```

**Body com erro (2/5): Preço mudou desde a busca**

```json
{
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "adultos": 2,
  "precoEsperadoCentavos": 100000
}
```

**Resultado esperado: 409 Conflict**

```json
{
  "status": 409,
  "codigo": "PRECO_ALTERADO",
  "titulo": "Ugh! O preço mudou.",
  "mensagem": "O valor da estadia mudou desde a sua busca: era R$ 1000,00 e agora é R$ 1560,00. Nenhuma reserva foi criada. Confira o novo valor e continue se quiser.",
  "ref": "HB-7Q2X",
  "dados": {
    "precoAntigoCentavos": 100000,
    "precoNovoCentavos": 156000
  }
}
```

**Body com erro (3/5): Sem token e sem e-mail**

Headers: (sem Authorization)

```json
{
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "adultos": 2
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Informe o e-mail do responsável pela reserva. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (4/5): Check-in no passado**

```json
{
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "checkIn": "2020-01-01",
  "checkOut": "2020-01-04",
  "adultos": 2,
  "email": "x@example.com"
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O check-in não pode estar no passado. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (5/5): Sem tocas livres (crie 3 reservas nas mesmas datas e peça a 4ª)**

```json
{
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "adultos": 2,
  "email": "quarta@example.com"
}
```

**Resultado esperado: 409 Conflict**

```json
{
  "status": 409,
  "codigo": "SEM_DISPONIBILIDADE",
  "titulo": "Ugh! Essa toca acabou de ser ocupada.",
  "mensagem": "Outra tribo ficou com as datas escolhidas enquanto você decidia. Nenhuma reserva foi alterada e nada foi cobrado. Veja datas próximas com toca livre.",
  "ref": "HB-7Q2X",
  "dados": {
    "sugestoes": [
      {
        "checkIn": "2026-12-03",
        "checkOut": "2026-12-06",
        "rotulo": "2 dias depois"
      },
      {
        "checkIn": "2026-12-08",
        "checkOut": "2026-12-11",
        "rotulo": "Na semana seguinte"
      }
    ]
  }
}
```

### 4.2 PUT `/reservas/{codigo}/hospedes`

**Passo 1: quem vem (hóspedes)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> Criança acima de 12 anos conta como adulto e o total sobe R$ 60/noite. A lista enviada substitui a anterior.

**Body**

```json
{
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "pais": "BR",
  "tipoDocumento": "CPF",
  "documento": "12345678909",
  "pedidoEspecial": "Berço de pele no quarto",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO"
    },
    {
      "nome": "Rafael",
      "tipo": "ADULTO"
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    },
    {
      "nome": "Lia",
      "tipo": "CRIANCA",
      "idade": 4
    }
  ]
}
```

**Resultado esperado: 200 OK**

```json
{
  "codigo": "HB-4F7K",
  "status": "PENDENTE",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:15:00-03:00",
  "prorrogada": false,
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Rafael",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    },
    {
      "nome": "Lia",
      "tipo": "CRIANCA",
      "idade": 4
    }
  ],
  "extras": [],
  "cupom": null,
  "pedidoEspecial": "Berço de pele no quarto",
  "lateCheckout": false,
  "valorDiarias": 156000,
  "valorExtras": 0,
  "desconto": 0,
  "ossosUsados": 0,
  "total": 156000,
  "pago": false,
  "checkinOnlineEm": null
}
```

**Body com erro (1/3): Só crianças, sem nenhum adulto**

```json
{
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "pais": "BR",
  "tipoDocumento": "CPF",
  "documento": "12345678909",
  "hospedes": [
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    }
  ]
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "É preciso ao menos 1 adulto na reserva. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/3): Criança sem idade**

```json
{
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "pais": "BR",
  "tipoDocumento": "CPF",
  "documento": "12345678909",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO"
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA"
    }
  ]
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Informe a idade de Theo (0 a 17 anos). Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (3/3): Reserva de outra pessoa (sem token nem header)**

Headers: (sem Authorization e sem X-Responsavel-Email)

```json
{
  "responsavelNome": "X",
  "responsavelEmail": "x@example.com",
  "celular": "1",
  "pais": "BR",
  "tipoDocumento": "CPF",
  "documento": "1",
  "hospedes": [
    {
      "nome": "X",
      "tipo": "ADULTO"
    }
  ]
}
```

**Resultado esperado: 403 Forbidden**

```json
{
  "status": 403,
  "codigo": "SEM_PERMISSAO",
  "titulo": "Uga! Essa toca não é sua.",
  "mensagem": "Esta reserva pertence a outra pessoa. Nada foi alterado. Entre com a conta certa ou informe o e-mail do responsável.",
  "ref": "HB-7Q2X"
}
```

### 4.3 POST `/reservas/{codigo}/prorrogar`

**+10 minutos (uma única vez)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "codigo": "HB-4F7K",
  "status": "PENDENTE",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:25:00-03:00",
  "prorrogada": true,
  "responsavelNome": null,
  "responsavelEmail": "marina.souza@example.com",
  "celular": null,
  "hospedes": [],
  "extras": [],
  "cupom": null,
  "pedidoEspecial": null,
  "lateCheckout": false,
  "valorDiarias": 156000,
  "valorExtras": 0,
  "desconto": 0,
  "ossosUsados": 0,
  "total": 156000,
  "pago": false,
  "checkinOnlineEm": null
}
```

**Body com erro: Segunda tentativa**

(não tem body)

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "O tempo extra já foi usado nesta reserva (só dá uma vez). Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

### 4.4 GET `/extras`

**Catálogo de extras**  ·  Acesso: livre

> Ids: CAFE_JURASSICO (incluso), KIT_MINI_CACADOR (R$ 45 por criança), LATE_CHECKOUT (R$ 80), TRANSFER (R$ 180), FESTA_DA_TRIBO (R$ 350), FOGUEIRA_VIP (R$ 150). Rota só de leitura.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
[
  {
    "id": "LATE_CHECKOUT",
    "nome": "Late check-out",
    "descricao": "Saída até as 15h em vez das 12h",
    "icone": "schedule",
    "precoCentavos": 8000,
    "cobranca": "POR_ESTADIA",
    "incluso": false
  },
  {
    "id": "CAFE_JURASSICO",
    "nome": "Café jurássico",
    "descricao": "Incluso em toda reserva",
    "icone": "free_breakfast",
    "precoCentavos": 0,
    "cobranca": "POR_ESTADIA",
    "incluso": true
  }
]
```

**Body com erro:** esta rota não tem entrada que gere erro de negócio.

### 4.5 PUT `/reservas/{codigo}/extras`

**Passo 2: escolher extras (lista completa; vazia = "Pular extras")**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> Os preços são congelados no momento da escolha. Extras "por criança" usam o número de crianças da reserva.

**Body**

```json
{
  "extras": [
    {
      "extraId": "LATE_CHECKOUT",
      "quantidade": 1
    },
    {
      "extraId": "FOGUEIRA_VIP",
      "quantidade": 1
    }
  ]
}
```

**Resultado esperado: 200 OK**

```json
{
  "codigo": "HB-4F7K",
  "status": "PENDENTE",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:15:00-03:00",
  "prorrogada": false,
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Rafael",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    },
    {
      "nome": "Lia",
      "tipo": "CRIANCA",
      "idade": 4
    }
  ],
  "extras": [
    {
      "extraId": "LATE_CHECKOUT",
      "nome": "Late check-out",
      "quantidade": 1,
      "precoUnitarioCentavos": 8000,
      "subtotalCentavos": 8000
    },
    {
      "extraId": "FOGUEIRA_VIP",
      "nome": "Fogueira VIP",
      "quantidade": 1,
      "precoUnitarioCentavos": 15000,
      "subtotalCentavos": 15000
    }
  ],
  "cupom": null,
  "pedidoEspecial": "Berço de pele no quarto",
  "lateCheckout": true,
  "valorDiarias": 156000,
  "valorExtras": 23000,
  "desconto": 0,
  "ossosUsados": 0,
  "total": 179000,
  "pago": false,
  "checkinOnlineEm": null
}
```

**Body com erro (1/2): Extra inexistente**

```json
{
  "extras": [
    {
      "extraId": "XPTO",
      "quantidade": 1
    }
  ]
}
```

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "O extra XPTO não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Extra por criança em reserva sem crianças**

```json
{
  "extras": [
    {
      "extraId": "KIT_MINI_CACADOR"
    }
  ]
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O extra Kit mini-caçador é cobrado por criança e esta reserva não tem crianças. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

### 4.6 POST `/reservas/{codigo}/cupom`

**Passo 3: aplicar cupom**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> 10% sobre diárias + extras: 179000 − 17900 = 161100. O uso do cupom só é contado quando a reserva é confirmada.

**Body**

```json
{
  "codigo": "UGABUGA10"
}
```

**Resultado esperado: 200 OK**

```json
{
  "codigo": "HB-4F7K",
  "status": "PENDENTE",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:15:00-03:00",
  "prorrogada": false,
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Rafael",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    },
    {
      "nome": "Lia",
      "tipo": "CRIANCA",
      "idade": 4
    }
  ],
  "extras": [
    {
      "extraId": "LATE_CHECKOUT",
      "nome": "Late check-out",
      "quantidade": 1,
      "precoUnitarioCentavos": 8000,
      "subtotalCentavos": 8000
    },
    {
      "extraId": "FOGUEIRA_VIP",
      "nome": "Fogueira VIP",
      "quantidade": 1,
      "precoUnitarioCentavos": 15000,
      "subtotalCentavos": 15000
    }
  ],
  "cupom": "UGABUGA10",
  "pedidoEspecial": "Berço de pele no quarto",
  "lateCheckout": true,
  "valorDiarias": 156000,
  "valorExtras": 23000,
  "desconto": 17900,
  "ossosUsados": 0,
  "total": 161100,
  "pago": false,
  "checkinOnlineEm": null
}
```

**Body com erro (1/2): Cupom inexistente, vencido ou esgotado**

```json
{
  "codigo": "NAOEXISTE"
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "CUPOM_INVALIDO",
  "titulo": "Uga! Esse cupom não vale.",
  "mensagem": "O cupom não existe, venceu ou já esgotou. Nenhum desconto foi aplicado e o total continua o mesmo. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Código em branco**

```json
{
  "codigo": ""
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "codigo",
      "erro": "não deve estar em branco"
    }
  ]
}
```

### 4.7 DELETE `/reservas/{codigo}/cupom`

**Remover cupom**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "codigo": "HB-4F7K",
  "status": "PENDENTE",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:15:00-03:00",
  "prorrogada": false,
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Rafael",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    },
    {
      "nome": "Lia",
      "tipo": "CRIANCA",
      "idade": 4
    }
  ],
  "extras": [
    {
      "extraId": "LATE_CHECKOUT",
      "nome": "Late check-out",
      "quantidade": 1,
      "precoUnitarioCentavos": 8000,
      "subtotalCentavos": 8000
    },
    {
      "extraId": "FOGUEIRA_VIP",
      "nome": "Fogueira VIP",
      "quantidade": 1,
      "precoUnitarioCentavos": 15000,
      "subtotalCentavos": 15000
    }
  ],
  "cupom": null,
  "pedidoEspecial": "Berço de pele no quarto",
  "lateCheckout": true,
  "valorDiarias": 156000,
  "valorExtras": 23000,
  "desconto": 0,
  "ossosUsados": 0,
  "total": 179000,
  "pago": false,
  "checkinOnlineEm": null
}
```

**Body com erro: Reserva de outra pessoa**

Headers: (sem Authorization e sem X-Responsavel-Email)

**Resultado esperado: 403 Forbidden**

```json
{
  "status": 403,
  "codigo": "SEM_PERMISSAO",
  "titulo": "Uga! Essa toca não é sua.",
  "mensagem": "Esta reserva pertence a outra pessoa. Nada foi alterado. Entre com a conta certa ou informe o e-mail do responsável.",
  "ref": "HB-7Q2X"
}
```

### 4.8 POST `/reservas/{codigo}/ossos`

**Usar ossos (múltiplos de 10, até 50% do total; exige login)**  ·  Acesso: login + dono

Headers: `Authorization: Bearer {{token}}`

> 100 ossos = R$ 10. O saldo só é debitado quando a reserva é confirmada.

**Body**

```json
{
  "quantidade": 100
}
```

**Resultado esperado: 200 OK**

```json
{
  "codigo": "HB-4F7K",
  "status": "PENDENTE",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:15:00-03:00",
  "prorrogada": false,
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Rafael",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    },
    {
      "nome": "Lia",
      "tipo": "CRIANCA",
      "idade": 4
    }
  ],
  "extras": [
    {
      "extraId": "LATE_CHECKOUT",
      "nome": "Late check-out",
      "quantidade": 1,
      "precoUnitarioCentavos": 8000,
      "subtotalCentavos": 8000
    },
    {
      "extraId": "FOGUEIRA_VIP",
      "nome": "Fogueira VIP",
      "quantidade": 1,
      "precoUnitarioCentavos": 15000,
      "subtotalCentavos": 15000
    }
  ],
  "cupom": "UGABUGA10",
  "pedidoEspecial": "Berço de pele no quarto",
  "lateCheckout": true,
  "valorDiarias": 156000,
  "valorExtras": 23000,
  "desconto": 17900,
  "ossosUsados": 100,
  "total": 160100,
  "pago": false,
  "checkinOnlineEm": null
}
```

**Body com erro (1/3): Não é múltiplo de 10**

```json
{
  "quantidade": 15
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O resgate é em múltiplos de 10 ossos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/3): Mais do que o saldo (450)**

```json
{
  "quantidade": 460
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Você tem 450 ossos e tentou usar 460. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (3/3): Sem login**

Headers: (sem Authorization)

```json
{
  "quantidade": 100
}
```

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "NAO_AUTENTICADO",
  "titulo": "Uga! Quem é você?",
  "mensagem": "Você precisa entrar na sua conta para fazer isso. Nada foi alterado. Faça login e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 4.9 DELETE `/reservas/{codigo}/ossos`

**Desfazer ossos**  ·  Acesso: login + dono

Headers: `Authorization: Bearer {{token}}`

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "codigo": "HB-4F7K",
  "status": "PENDENTE",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:15:00-03:00",
  "prorrogada": false,
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Rafael",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    },
    {
      "nome": "Lia",
      "tipo": "CRIANCA",
      "idade": 4
    }
  ],
  "extras": [
    {
      "extraId": "LATE_CHECKOUT",
      "nome": "Late check-out",
      "quantidade": 1,
      "precoUnitarioCentavos": 8000,
      "subtotalCentavos": 8000
    },
    {
      "extraId": "FOGUEIRA_VIP",
      "nome": "Fogueira VIP",
      "quantidade": 1,
      "precoUnitarioCentavos": 15000,
      "subtotalCentavos": 15000
    }
  ],
  "cupom": "UGABUGA10",
  "pedidoEspecial": "Berço de pele no quarto",
  "lateCheckout": true,
  "valorDiarias": 156000,
  "valorExtras": 23000,
  "desconto": 17900,
  "ossosUsados": 0,
  "total": 161100,
  "pago": false,
  "checkinOnlineEm": null
}
```

**Body com erro: Sem login**

Headers: (sem Authorization)

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "NAO_AUTENTICADO",
  "titulo": "Uga! Quem é você?",
  "mensagem": "Você precisa entrar na sua conta para fazer isso. Nada foi alterado. Faça login e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 4.10 POST `/reservas/{codigo}/pagamentos`

**Pagar: cartão (aprovado na hora)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> Aprova qualquer número que passe no algoritmo de Luhn. Só os 4 últimos dígitos e a bandeira são guardados. A reserva vira CONFIRMADA e o cupom/ossos são consumidos.

**Body**

```json
{
  "metodo": "CARTAO",
  "parcelas": 3,
  "numeroCartao": "4111111111111111",
  "validade": "12/30",
  "cvv": "123",
  "aceitoRegras": true
}
```

**Resultado esperado: 201 Created**

```json
{
  "id": "5e8f1a36-2c7d-4b90-9d14-7a6b3c0e8f25",
  "reservaCodigo": "HB-4F7K",
  "metodo": "CARTAO",
  "status": "APROVADO",
  "valorCentavos": 161100,
  "parcelas": 3,
  "valorParcelaCentavos": 53700,
  "cartaoFinal": "1111",
  "cartaoBandeira": "VISA",
  "pixCopiaCola": null,
  "pixExpiraEm": null,
  "motivoRecusa": null
}
```

**Body com erro (1/7): Cartão final 0002: recusado por saldo (a reserva continua PENDENTE e nada é consumido)**

```json
{
  "metodo": "CARTAO",
  "parcelas": 1,
  "numeroCartao": "4000000000000002",
  "validade": "12/30",
  "cvv": "123",
  "aceitoRegras": true
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "PAGAMENTO_RECUSADO",
  "titulo": "Ugh! O pagamento não passou.",
  "mensagem": "O banco recusou o cartão final 0002 (saldo insuficiente). Nada foi cobrado e a sua toca continua segurada. Tente outro cartão ou escolha Pix.",
  "ref": "HB-7Q2X",
  "dados": {
    "motivo": "SALDO_INSUFICIENTE",
    "cartaoFinal": "0002"
  }
}
```

**Body com erro (2/7): Cartão final 0069: recusado por cartão vencido**

```json
{
  "metodo": "CARTAO",
  "parcelas": 1,
  "numeroCartao": "4000000000000069",
  "validade": "12/30",
  "cvv": "123",
  "aceitoRegras": true
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "PAGAMENTO_RECUSADO",
  "titulo": "Ugh! O pagamento não passou.",
  "mensagem": "O banco recusou o cartão final 0069 (cartão vencido). Nada foi cobrado e a sua toca continua segurada. Tente outro cartão ou escolha Pix.",
  "ref": "HB-7Q2X",
  "dados": {
    "motivo": "CARTAO_EXPIRADO",
    "cartaoFinal": "0069"
  }
}
```

**Body com erro (3/7): Número inválido no Luhn**

```json
{
  "metodo": "CARTAO",
  "parcelas": 1,
  "numeroCartao": "4111111111111112",
  "validade": "12/30",
  "cvv": "123",
  "aceitoRegras": true
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O número do cartão é inválido. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (4/7): Não aceitou as regras**

```json
{
  "metodo": "CARTAO",
  "parcelas": 1,
  "numeroCartao": "4111111111111111",
  "validade": "12/30",
  "cvv": "123",
  "aceitoRegras": false
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "É preciso aceitar as regras da estadia para pagar com cartão. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (5/7): Parcelas fora de 1 a 6**

```json
{
  "metodo": "CARTAO",
  "parcelas": 7,
  "numeroCartao": "4111111111111111",
  "validade": "12/30",
  "cvv": "123",
  "aceitoRegras": true
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O parcelamento vai de 1 a 6 vezes. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (6/7): Pagar de novo uma reserva já confirmada (clique duplo)**

```json
{
  "metodo": "CARTAO",
  "parcelas": 1,
  "numeroCartao": "4111111111111111",
  "validade": "12/30",
  "cvv": "123",
  "aceitoRegras": true
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "A reserva está CONFIRMADA e essa ação não é possível nesse estado. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (7/7): Tempo da reserva esgotado (após 15 min sem prorrogar)**

```json
{
  "metodo": "NA_CHEGADA"
}
```

**Resultado esperado: 410 Gone**

```json
{
  "status": 410,
  "codigo": "RESERVA_EXPIRADA",
  "titulo": "Uga! O tempo da toca acabou.",
  "mensagem": "O tempo desta reserva venceu e a toca foi liberada. Nada foi cobrado. Faça uma nova busca para reservar de novo.",
  "ref": "HB-7Q2X"
}
```

### 4.11 POST `/reservas/{codigo}/pagamentos`

**Pagar: Pix (5% de desconto, expira em 10 min)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> 161100 − 5% = 153045. A reserva fica AGUARDANDO_PAGAMENTO. Pedir o Pix de novo devolve o mesmo pagamento. O desconto vale só no pagamento: o total da reserva continua 161100.

**Body**

```json
{
  "metodo": "PIX"
}
```

**Resultado esperado: 201 Created**

```json
{
  "id": "5e8f1a36-2c7d-4b90-9d14-7a6b3c0e8f25",
  "reservaCodigo": "HB-4F7K",
  "metodo": "PIX",
  "status": "PENDENTE",
  "valorCentavos": 153045,
  "parcelas": null,
  "valorParcelaCentavos": null,
  "cartaoFinal": null,
  "cartaoBandeira": null,
  "pixCopiaCola": "00020126HUGABUGAHB4F7K8a1c5e90d3f24b7a9e6c1d08f5b2a37",
  "pixExpiraEm": "2026-12-01T14:10:00-03:00",
  "motivoRecusa": null
}
```

**Body com erro (1/2): Tentar pagar com cartão enquanto existe Pix aberto**

```json
{
  "metodo": "CARTAO",
  "parcelas": 1,
  "numeroCartao": "4111111111111111",
  "validade": "12/30",
  "cvv": "123",
  "aceitoRegras": true
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "Já existe um Pix aguardando pagamento. Pague-o ou espere ele expirar. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Método inexistente**

```json
{
  "metodo": "BOLETO"
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Não conseguimos ler os dados enviados (confira o JSON e o formato das datas, ex.: 2026-10-12). Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

### 4.12 POST `/reservas/{codigo}/pagamentos`

**Pagar: na chegada (confirma sem cobrar)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> A reserva fica CONFIRMADA com `pago: false`. Cancelar fora do prazo gera multa de 1 diária.

**Body**

```json
{
  "metodo": "NA_CHEGADA"
}
```

**Resultado esperado: 201 Created**

```json
{
  "id": "5e8f1a36-2c7d-4b90-9d14-7a6b3c0e8f25",
  "reservaCodigo": "HB-4F7K",
  "metodo": "NA_CHEGADA",
  "status": "PENDENTE",
  "valorCentavos": 161100,
  "parcelas": null,
  "valorParcelaCentavos": null,
  "cartaoFinal": null,
  "cartaoBandeira": null,
  "pixCopiaCola": null,
  "pixExpiraEm": null,
  "motivoRecusa": null
}
```

**Body com erro: Body sem o método**

```json
{}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "metodo",
      "erro": "não deve ser nulo"
    }
  ]
}
```

### 4.13 GET `/pagamentos/{id}`

**Status do pagamento (o front consulta a cada 3 s no Pix)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> No perfil `dev`, o Pix pendente é aprovado sozinho 20 s depois de criado (quando você consulta).

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "id": "5e8f1a36-2c7d-4b90-9d14-7a6b3c0e8f25",
  "reservaCodigo": "HB-4F7K",
  "metodo": "PIX",
  "status": "PENDENTE",
  "valorCentavos": 153045,
  "parcelas": null,
  "valorParcelaCentavos": null,
  "cartaoFinal": null,
  "cartaoBandeira": null,
  "pixCopiaCola": "00020126HUGABUGA...",
  "pixExpiraEm": "2026-12-01T14:10:00-03:00",
  "motivoRecusa": null
}
```

**Body com erro (1/2): Pagamento inexistente**

Rota: `GET /pagamentos/00000000-0000-0000-0000-000000000000`

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Pagamento não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Pagamento de outra reserva**

Headers: (sem Authorization e sem X-Responsavel-Email)

**Resultado esperado: 403 Forbidden**

```json
{
  "status": 403,
  "codigo": "SEM_PERMISSAO",
  "titulo": "Uga! Essa toca não é sua.",
  "mensagem": "Esta reserva pertence a outra pessoa. Nada foi alterado. Entre com a conta certa ou informe o e-mail do responsável.",
  "ref": "HB-7Q2X"
}
```

### 4.14 POST `/pagamentos/{id}/simular-confirmacao`

**Confirmar o Pix de mentira (só perfil dev)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> Fora do perfil `dev` esta rota responde 404.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "id": "5e8f1a36-2c7d-4b90-9d14-7a6b3c0e8f25",
  "reservaCodigo": "HB-4F7K",
  "metodo": "PIX",
  "status": "APROVADO",
  "valorCentavos": 153045,
  "parcelas": null,
  "valorParcelaCentavos": null,
  "cartaoFinal": null,
  "cartaoBandeira": null,
  "pixCopiaCola": "00020126HUGABUGA...",
  "pixExpiraEm": "2026-12-01T14:10:00-03:00",
  "motivoRecusa": null
}
```

**Body com erro: Pix que já foi aprovado ou que expirou**

(não tem body)

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "Este Pix não está mais aguardando pagamento. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

### 4.15 GET `/reservas/{codigo}`

**Voucher (resumo completo)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "codigo": "HB-4F7K",
  "status": "CONFIRMADA",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:15:00-03:00",
  "prorrogada": false,
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Rafael",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    },
    {
      "nome": "Lia",
      "tipo": "CRIANCA",
      "idade": 4
    }
  ],
  "extras": [
    {
      "extraId": "LATE_CHECKOUT",
      "nome": "Late check-out",
      "quantidade": 1,
      "precoUnitarioCentavos": 8000,
      "subtotalCentavos": 8000
    },
    {
      "extraId": "FOGUEIRA_VIP",
      "nome": "Fogueira VIP",
      "quantidade": 1,
      "precoUnitarioCentavos": 15000,
      "subtotalCentavos": 15000
    }
  ],
  "cupom": "UGABUGA10",
  "pedidoEspecial": "Berço de pele no quarto",
  "lateCheckout": true,
  "valorDiarias": 156000,
  "valorExtras": 23000,
  "desconto": 17900,
  "ossosUsados": 0,
  "total": 161100,
  "pago": true,
  "checkinOnlineEm": null
}
```

**Body com erro (1/2): Sem token nem header**

Headers: (sem Authorization e sem X-Responsavel-Email)

**Resultado esperado: 403 Forbidden**

```json
{
  "status": 403,
  "codigo": "SEM_PERMISSAO",
  "titulo": "Uga! Essa toca não é sua.",
  "mensagem": "Esta reserva pertence a outra pessoa. Nada foi alterado. Entre com a conta certa ou informe o e-mail do responsável.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Código inexistente**

Rota: `GET /reservas/HB-ZZZZ`

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Reserva não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 4.16 GET `/reservas/{codigo}/voucher.pdf`

**Voucher em PDF ("Baixar tábua")**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

**Body**

(não tem body)

**Resultado esperado: 200 OK**

Resposta binária, `Content-Type: application/pdf` e `Content-Disposition: attachment; filename="tabua-HB-4F7K.pdf"`. No Postman use *Send and Download*.

**Body com erro: Reserva de outra pessoa**

Headers: (sem Authorization e sem X-Responsavel-Email)

**Resultado esperado: 403 Forbidden**

```json
{
  "status": 403,
  "codigo": "SEM_PERMISSAO",
  "titulo": "Uga! Essa toca não é sua.",
  "mensagem": "Esta reserva pertence a outra pessoa. Nada foi alterado. Entre com a conta certa ou informe o e-mail do responsável.",
  "ref": "HB-7Q2X"
}
```

### 4.17 GET `/reservas/buscar?codigo=HB-4F7K&email=cliente@example.com`

**Reserva sem conta: buscar por código e e-mail**  ·  Acesso: livre

> Exige código E e-mail. Código certo com e-mail errado responde igual a código inexistente (não revela quais códigos existem).

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "codigo": "HB-4F7K",
  "status": "PENDENTE",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:15:00-03:00",
  "prorrogada": false,
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Rafael",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    },
    {
      "nome": "Lia",
      "tipo": "CRIANCA",
      "idade": 4
    }
  ],
  "extras": [
    {
      "extraId": "LATE_CHECKOUT",
      "nome": "Late check-out",
      "quantidade": 1,
      "precoUnitarioCentavos": 8000,
      "subtotalCentavos": 8000
    },
    {
      "extraId": "FOGUEIRA_VIP",
      "nome": "Fogueira VIP",
      "quantidade": 1,
      "precoUnitarioCentavos": 15000,
      "subtotalCentavos": 15000
    }
  ],
  "cupom": "UGABUGA10",
  "pedidoEspecial": "Berço de pele no quarto",
  "lateCheckout": true,
  "valorDiarias": 156000,
  "valorExtras": 23000,
  "desconto": 17900,
  "ossosUsados": 0,
  "total": 161100,
  "pago": false,
  "checkinOnlineEm": null
}
```

**Body com erro (1/2): E-mail diferente do responsável**

Rota: `GET /reservas/buscar?codigo=HB-4F7K&email=outro@example.com`

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Reserva não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Falta o parâmetro email**

Rota: `GET /reservas/buscar?codigo=HB-4F7K`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Falta o parâmetro 'email'. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "email",
      "erro": "obrigatório"
    }
  ]
}
```

### 4.18 POST `/reservas/{codigo}/checkin`

**Check-in online (libera 3 dias antes da chegada)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "codigo": "HB-4F7K",
  "status": "CONFIRMADA",
  "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
  "cavernaNome": "Toca do Mamute",
  "unidadeCodigo": "T-07",
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-04",
  "noites": 3,
  "adultos": 2,
  "criancas": 2,
  "expiraEm": "2026-12-01T14:15:00-03:00",
  "prorrogada": false,
  "responsavelNome": "Marina Souza",
  "responsavelEmail": "marina.souza@example.com",
  "celular": "+55 11 98888-7777",
  "hospedes": [
    {
      "nome": "Marina",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Rafael",
      "tipo": "ADULTO",
      "idade": null
    },
    {
      "nome": "Theo",
      "tipo": "CRIANCA",
      "idade": 9
    },
    {
      "nome": "Lia",
      "tipo": "CRIANCA",
      "idade": 4
    }
  ],
  "extras": [
    {
      "extraId": "LATE_CHECKOUT",
      "nome": "Late check-out",
      "quantidade": 1,
      "precoUnitarioCentavos": 8000,
      "subtotalCentavos": 8000
    },
    {
      "extraId": "FOGUEIRA_VIP",
      "nome": "Fogueira VIP",
      "quantidade": 1,
      "precoUnitarioCentavos": 15000,
      "subtotalCentavos": 15000
    }
  ],
  "cupom": "UGABUGA10",
  "pedidoEspecial": "Berço de pele no quarto",
  "lateCheckout": true,
  "valorDiarias": 156000,
  "valorExtras": 23000,
  "desconto": 17900,
  "ossosUsados": 0,
  "total": 161100,
  "pago": true,
  "checkinOnlineEm": "2026-11-29T10:00:00-03:00"
}
```

**Body com erro: Cedo demais (faltam mais de 3 dias)**

(não tem body)

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "FORA_DO_PRAZO",
  "titulo": "Uga! Fora do prazo.",
  "mensagem": "O check-in online abre 3 dias antes da chegada (2026-11-28). Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

### 4.19 PATCH `/reservas/{codigo}/datas`

**Alterar datas (recalcula o preço)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> Só reserva CONFIRMADA e até 2 dias antes do check-in. `diferencaCentavos` positivo vira novo pagamento PENDENTE; negativo é valor a devolver.

**Body**

```json
{
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-05"
}
```

**Resultado esperado: 200 OK**

```json
{
  "reserva": {
    "codigo": "HB-4F7K",
    "status": "CONFIRMADA",
    "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
    "cavernaNome": "Toca do Mamute",
    "unidadeCodigo": "T-07",
    "checkIn": "2026-12-01",
    "checkOut": "2026-12-05",
    "noites": 4,
    "adultos": 2,
    "criancas": 2,
    "expiraEm": "2026-12-01T14:15:00-03:00",
    "prorrogada": false,
    "responsavelNome": "Marina Souza",
    "responsavelEmail": "marina.souza@example.com",
    "celular": "+55 11 98888-7777",
    "hospedes": [
      {
        "nome": "Marina",
        "tipo": "ADULTO",
        "idade": null
      },
      {
        "nome": "Rafael",
        "tipo": "ADULTO",
        "idade": null
      },
      {
        "nome": "Theo",
        "tipo": "CRIANCA",
        "idade": 9
      },
      {
        "nome": "Lia",
        "tipo": "CRIANCA",
        "idade": 4
      }
    ],
    "extras": [
      {
        "extraId": "LATE_CHECKOUT",
        "nome": "Late check-out",
        "quantidade": 1,
        "precoUnitarioCentavos": 8000,
        "subtotalCentavos": 8000
      },
      {
        "extraId": "FOGUEIRA_VIP",
        "nome": "Fogueira VIP",
        "quantidade": 1,
        "precoUnitarioCentavos": 15000,
        "subtotalCentavos": 15000
      }
    ],
    "cupom": "UGABUGA10",
    "pedidoEspecial": "Berço de pele no quarto",
    "lateCheckout": true,
    "valorDiarias": 208000,
    "valorExtras": 23000,
    "desconto": 23100,
    "ossosUsados": 0,
    "total": 207900,
    "pago": true,
    "checkinOnlineEm": null
  },
  "diferencaCentavos": 46800
}
```

**Body com erro (1/4): Reserva ainda PENDENTE**

```json
{
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-05"
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "A reserva está PENDENTE e essa ação não é possível nesse estado. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/4): Check-in a menos de 2 dias**

```json
{
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-05"
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "FORA_DO_PRAZO",
  "titulo": "Uga! Fora do prazo.",
  "mensagem": "As datas só podem ser alteradas até 2 dias antes do check-in. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (3/4): Datas novas já ocupadas pela unidade**

```json
{
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-05"
}
```

**Resultado esperado: 409 Conflict**

```json
{
  "status": 409,
  "codigo": "SEM_DISPONIBILIDADE",
  "titulo": "Ugh! Essa toca acabou de ser ocupada.",
  "mensagem": "Outra tribo ficou com as datas escolhidas enquanto você decidia. Nenhuma reserva foi alterada e nada foi cobrado. Veja datas próximas com toca livre.",
  "ref": "HB-7Q2X",
  "dados": {
    "sugestoes": [
      {
        "checkIn": "2026-12-03",
        "checkOut": "2026-12-08",
        "rotulo": "2 dias depois"
      }
    ]
  }
}
```

**Body com erro (4/4): Campos faltando**

```json
{}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "checkIn",
      "erro": "não deve ser nulo"
    },
    {
      "campo": "checkOut",
      "erro": "não deve ser nulo"
    }
  ]
}
```

### 4.20 POST `/reservas/{codigo}/cancelar`

**Cancelar (grátis até 2 dias antes do check-in)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> Dentro do prazo: reembolso integral e ossos de volta. Fora: sem reembolso; se era "pagar na chegada", `multaCentavos` = 1 diária (52000).

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "reserva": {
    "codigo": "HB-4F7K",
    "status": "CANCELADA",
    "cavernaId": "3f1c8d52-9a4b-4e7a-b1d2-6c0e5a7f9b10",
    "cavernaNome": "Toca do Mamute",
    "unidadeCodigo": "T-07",
    "checkIn": "2026-12-01",
    "checkOut": "2026-12-04",
    "noites": 3,
    "adultos": 2,
    "criancas": 2,
    "expiraEm": "2026-12-01T14:15:00-03:00",
    "prorrogada": false,
    "responsavelNome": "Marina Souza",
    "responsavelEmail": "marina.souza@example.com",
    "celular": "+55 11 98888-7777",
    "hospedes": [
      {
        "nome": "Marina",
        "tipo": "ADULTO",
        "idade": null
      },
      {
        "nome": "Rafael",
        "tipo": "ADULTO",
        "idade": null
      },
      {
        "nome": "Theo",
        "tipo": "CRIANCA",
        "idade": 9
      },
      {
        "nome": "Lia",
        "tipo": "CRIANCA",
        "idade": 4
      }
    ],
    "extras": [
      {
        "extraId": "LATE_CHECKOUT",
        "nome": "Late check-out",
        "quantidade": 1,
        "precoUnitarioCentavos": 8000,
        "subtotalCentavos": 8000
      },
      {
        "extraId": "FOGUEIRA_VIP",
        "nome": "Fogueira VIP",
        "quantidade": 1,
        "precoUnitarioCentavos": 15000,
        "subtotalCentavos": 15000
      }
    ],
    "cupom": "UGABUGA10",
    "pedidoEspecial": "Berço de pele no quarto",
    "lateCheckout": true,
    "valorDiarias": 156000,
    "valorExtras": 23000,
    "desconto": 17900,
    "ossosUsados": 0,
    "total": 161100,
    "pago": true,
    "checkinOnlineEm": null
  },
  "reembolsoCentavos": 161100,
  "multaCentavos": 0
}
```

**Body com erro (1/2): Reserva que já foi cancelada**

(não tem body)

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "A reserva está CANCELADA e essa ação não é possível nesse estado. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Reserva de outra pessoa**

Headers: (sem Authorization e sem X-Responsavel-Email)

**Resultado esperado: 403 Forbidden**

```json
{
  "status": 403,
  "codigo": "SEM_PERMISSAO",
  "titulo": "Uga! Essa toca não é sua.",
  "mensagem": "Esta reserva pertence a outra pessoa. Nada foi alterado. Entre com a conta certa ou informe o e-mail do responsável.",
  "ref": "HB-7Q2X"
}
```

### 4.21 POST `/reservas/{codigo}/relato`

**Relato pós-estadia (nota 1 a 5)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}`

> Só em reserva CONCLUIDA (o job diário das 03:00 conclui depois do check-out). Um relato por estadia; só quem tem conta.

**Body**

```json
{
  "nota": 5,
  "texto": "Melhor toca da tribo! As crianças amaram."
}
```

**Resultado esperado: 201 Created**

(sem corpo)

**Body com erro (1/3): Nota fora de 1 a 5**

```json
{
  "nota": 9,
  "texto": "Ótimo"
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "nota",
      "erro": "deve ser menor que ou igual a 5"
    }
  ]
}
```

**Body com erro (2/3): Estadia ainda não concluída**

```json
{
  "nota": 5,
  "texto": "Ótimo"
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "A reserva está CONFIRMADA e essa ação não é possível nesse estado. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (3/3): Segundo relato da mesma estadia**

```json
{
  "nota": 4,
  "texto": "De novo"
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "Esta estadia já tem um relato. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

---

## 5. Atividades

### 5.1 GET `/atividades`

**Catálogo (filtro opcional: `?categoria=CRIANCAS`)**  ·  Acesso: livre

> 7 atividades: Escavação de ovo de dino, Pintura rupestre, Trilha dos mamutes, Arco e flecha, Fogueira & histórias, Banho de lama, Mini-caçadores. Preço 0 = grátis.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "itens": [
    {
      "id": "a47c3e19-8b5d-4f26-a3e0-2d9c6b1f7e84",
      "nome": "Arco e flecha",
      "categoria": "AVENTURA",
      "descricao": "Aula de pontaria com monitor, equipamento de segurança incluso.",
      "duracaoMin": 45,
      "idadeMin": 8,
      "idadeMax": null,
      "precoCentavos": 5000,
      "cobranca": "POR_PESSOA",
      "destaque": null
    }
  ],
  "contagemPorCategoria": {
    "ADULTOS": 1,
    "AVENTURA": 2,
    "CRIANCAS": 2,
    "FAMILIA": 1,
    "NOITE": 1
  }
}
```

**Body com erro: Categoria inexistente**

Rota: `GET /atividades?categoria=XYZ`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O valor informado para 'categoria' não é válido. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "categoria",
      "erro": "valor inválido"
    }
  ]
}
```

### 5.2 GET `/atividades/{id}`

**Detalhe da atividade**  ·  Acesso: livre

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "id": "a47c3e19-8b5d-4f26-a3e0-2d9c6b1f7e84",
  "nome": "Arco e flecha",
  "categoria": "AVENTURA",
  "descricao": "Aula de pontaria com monitor, equipamento de segurança incluso.",
  "duracaoMin": 45,
  "idadeMin": 8,
  "idadeMax": null,
  "precoCentavos": 5000,
  "cobranca": "POR_PESSOA",
  "destaque": null
}
```

**Body com erro: Id inexistente**

Rota: `GET /atividades/00000000-0000-0000-0000-000000000000`

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Atividade não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

### 5.3 GET `/atividades/{id}/sessoes?de=2026-12-01&ate=2026-12-04`

**Sessões com vagas**  ·  Acesso: livre

> `esgotada: true` quando `vagasLivres` chega a 0 (aí o front mostra o botão "Avisar"). Sem `de`/`ate`, devolve de hoje a 7 dias. O fuso do horário pode vir como `-03:00` ou em UTC (`Z`).

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
[
  {
    "id": "d81b6f05-3e9a-4c72-8f1d-5b0a7e2c9d46",
    "inicio": "2026-12-02T11:00:00-03:00",
    "vagasTotais": 6,
    "vagasLivres": 4,
    "esgotada": false
  },
  {
    "id": "17e4b9c2-6d8a-4f31-b5e7-9c3a0d2f8b61",
    "inicio": "2026-12-02T14:30:00-03:00",
    "vagasTotais": 6,
    "vagasLivres": 0,
    "esgotada": true
  }
]
```

**Body com erro (1/2): Data final antes da inicial**

Rota: `GET /atividades/{id}/sessoes?de=2026-12-04&ate=2026-12-01`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "A data final não pode ser anterior à inicial. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Data fora do formato**

Rota: `GET /atividades/{id}/sessoes?de=01-12-2026`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O valor informado para 'de' não é válido. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "de",
      "erro": "valor inválido"
    }
  ]
}
```

### 5.4 POST `/reservas/{codigo}/agendamentos`

**Agendar uma vaga**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> Só reserva CONFIRMADA cuja estadia inclua o dia da sessão. Respeita idade mínima/máxima (Arco e flecha: a partir de 8 anos). O valor entra na conta da toca.

**Body**

```json
{
  "sessaoId": "d81b6f05-3e9a-4c72-8f1d-5b0a7e2c9d46",
  "quantidade": 1
}
```

**Resultado esperado: 201 Created**

```json
{
  "id": "17e4b9c2-6d8a-4f31-b5e7-9c3a0d2f8b61",
  "sessaoId": "d81b6f05-3e9a-4c72-8f1d-5b0a7e2c9d46",
  "atividade": "Arco e flecha",
  "inicio": "2026-12-02T11:00:00-03:00",
  "quantidade": 1,
  "valorCentavos": 5000,
  "status": "ATIVO"
}
```

**Body com erro (1/5): Sessão lotada (use uma com `esgotada: true`)**

```json
{
  "sessaoId": "d81b6f05-3e9a-4c72-8f1d-5b0a7e2c9d46",
  "quantidade": 1
}
```

**Resultado esperado: 409 Conflict**

```json
{
  "status": 409,
  "codigo": "VAGAS_ESGOTADAS",
  "titulo": "Ugh! As vagas acabaram.",
  "mensagem": "Restam 0 vaga(s) neste horário e você pediu 1. Nada foi reservado nem cobrado. Escolha outro horário ou toque em \"Avisar\".",
  "ref": "HB-7Q2X",
  "dados": {
    "vagasLivres": 0
  }
}
```

**Body com erro (2/5): Mais pessoas na faixa de idade do que a tribo tem**

```json
{
  "sessaoId": "d81b6f05-3e9a-4c72-8f1d-5b0a7e2c9d46",
  "quantidade": 4
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Sua tribo tem 3 pessoa(s) na faixa de idade desta atividade e você pediu 4. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (3/5): Sessão fora da estadia**

```json
{
  "sessaoId": "{{sessaoForaDaEstadia}}",
  "quantidade": 1
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "A sessão do dia 2026-12-20 está fora da sua estadia (2026-12-01 a 2026-12-04). Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (4/5): Quantidade zero**

```json
{
  "sessaoId": "d81b6f05-3e9a-4c72-8f1d-5b0a7e2c9d46",
  "quantidade": 0
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "quantidade",
      "erro": "deve ser maior que ou igual a 1"
    }
  ]
}
```

**Body com erro (5/5): Reserva ainda não confirmada**

```json
{
  "sessaoId": "d81b6f05-3e9a-4c72-8f1d-5b0a7e2c9d46",
  "quantidade": 1
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "A reserva está PENDENTE e essa ação não é possível nesse estado. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

### 5.5 DELETE `/reservas/{codigo}/agendamentos/{id}`

**Cancelar agendamento (até 4 h antes; devolve a vaga)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

**Body**

(não tem body)

**Resultado esperado: 204 No Content**

(sem corpo)

**Body com erro (1/3): Atividade começa em menos de 4 horas**

(não tem body)

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "FORA_DO_PRAZO",
  "titulo": "Uga! Fora do prazo.",
  "mensagem": "O cancelamento vai até 4 horas antes da atividade. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/3): Agendamento inexistente**

Rota: `DELETE /reservas/{codigo}/agendamentos/00000000-0000-0000-0000-000000000000`

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Agendamento não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (3/3): Cancelar de novo**

(não tem body)

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "Esse agendamento já foi cancelado. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

### 5.6 GET `/reservas/{codigo}/agenda?data=2026-12-02`

**Agenda da tribo no dia (café, atividades e mesa)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> Sem `data`, usa o dia do check-in. O café só aparece a partir da manhã seguinte à chegada.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "data": "2026-12-02",
  "itens": [
    {
      "hora": "07:00",
      "tipo": "CAFE",
      "titulo": "Café jurássico",
      "detalhe": "Incluso na sua reserva"
    },
    {
      "hora": "11:00",
      "tipo": "ATIVIDADE",
      "titulo": "Arco e flecha",
      "detalhe": "1 pessoa(s)"
    },
    {
      "hora": "20:00",
      "tipo": "MESA",
      "titulo": "O Banquete",
      "detalhe": "Mesa para 4 (BQ-9XQ2)"
    }
  ]
}
```

**Body com erro: Dia fora da estadia**

Rota: `GET /reservas/{codigo}/agenda?data=2027-01-01`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Esse dia está fora da sua estadia. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

### 5.7 POST `/alertas-vaga`

**Avisar quando abrir vaga (opcional)**  ·  Acesso: livre

> Só grava o pedido; o projeto não tem job de notificação. `tipo`: CAVERNA (use `cavernaId`, `checkIn`, `checkOut`) ou ATIVIDADE (use `sessaoId`).

**Body**

```json
{
  "email": "marina.souza@example.com",
  "tipo": "ATIVIDADE",
  "sessaoId": "d81b6f05-3e9a-4c72-8f1d-5b0a7e2c9d46",
  "pessoas": 2
}
```

**Resultado esperado: 201 Created**

(sem corpo)

**Body com erro: E-mail inválido e tipo faltando**

```json
{
  "email": "isso-nao-e-email"
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "email",
      "erro": "deve ser um endereço de e-mail bem formado"
    },
    {
      "campo": "tipo",
      "erro": "não deve estar em branco"
    }
  ]
}
```

---

## 6. O Banquete (restaurante)

### 6.1 GET `/restaurante`

**Turnos, "aberto agora" e desconto de hóspede**  ·  Acesso: livre

> Rota só de leitura: não há body que gere erro.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "turnos": [
    {
      "nome": "Café",
      "abre": "07:00:00",
      "fecha": "10:30:00"
    },
    {
      "nome": "Almoço",
      "abre": "12:00:00",
      "fecha": "15:00:00"
    },
    {
      "nome": "Jantar",
      "abre": "19:00:00",
      "fecha": "23:00:00"
    }
  ],
  "abertoAgora": false,
  "turnoAtual": null,
  "descontoHospedePct": 15
}
```

**Body com erro:** esta rota não tem entrada que gere erro de negócio.

### 6.2 GET `/restaurante/cardapio`

**Cardápio (filtro opcional: `?categoria=NA_FOGUEIRA`)**  ·  Acesso: livre

> Categorias: NA_FOGUEIRA, ENTRADAS, MENU_KIDS, DA_HORTA, SOBREMESAS, POCOES.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
[
  {
    "id": "62c9a0f7-1b4e-4d85-9a3c-8e5f2b7d1c09",
    "nome": "Costela de Mamute",
    "descricao": "Costela bovina assada por 10 horas na brasa, com farofa de milho.",
    "precoCentavos": 8900,
    "categoria": "NA_FOGUEIRA",
    "tags": [
      "Carro-chefe",
      "Pra dividir"
    ]
  }
]
```

**Body com erro: Categoria inexistente**

Rota: `GET /restaurante/cardapio?categoria=XYZ`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "O valor informado para 'categoria' não é válido. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "categoria",
      "erro": "valor inválido"
    }
  ]
}
```

### 6.3 GET `/restaurante/horarios?data=2026-12-02&pessoas=4`

**Horários LIVRE ou LOTADO**  ·  Acesso: livre

> 6 horários (19:00 a 21:30), capacidade de 40 pessoas cada. Fica LOTADO quando (já reservados + pessoas pedidas) passa de 40.

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
[
  {
    "horario": "19:00:00",
    "status": "LIVRE",
    "vagasRestantes": 40
  },
  {
    "horario": "19:30:00",
    "status": "LIVRE",
    "vagasRestantes": 40
  },
  {
    "horario": "20:00:00",
    "status": "LOTADO",
    "vagasRestantes": 2
  }
]
```

**Body com erro (1/3): 13 pessoas (o máximo é 12)**

Rota: `GET /restaurante/horarios?data=2026-12-02&pessoas=13`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "A mesa comporta de 1 a 12 pessoas. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/3): Data no passado**

Rota: `GET /restaurante/horarios?data=2020-01-01&pessoas=2`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Escolha uma data a partir de hoje. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (3/3): Falta a data**

Rota: `GET /restaurante/horarios?pessoas=2`

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Falta o parâmetro 'data'. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "data",
      "erro": "obrigatório"
    }
  ]
}
```

### 6.4 POST `/restaurante/reservas-mesa`

**Reservar mesa ("Uga! Reservar mesa")**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

> Só hóspede com reserva CONFIRMADA e a data dentro da estadia. `descontoHospedePct` (15%) é informativo. `nomeContato` e `celularContato` são opcionais (usa os do responsável).

**Body**

```json
{
  "codigoReserva": "HB-4F7K",
  "nomeContato": "Marina Souza",
  "celularContato": "+55 11 98888-7777",
  "data": "2026-12-02",
  "horario": "20:00",
  "pessoas": 4,
  "cadeirinhas": 1,
  "pertoFogueira": true
}
```

**Resultado esperado: 201 Created**

```json
{
  "codigo": "BQ-9XQ2",
  "data": "2026-12-02",
  "horario": "20:00:00",
  "pessoas": 4,
  "cadeirinhas": 1,
  "pertoFogueira": true,
  "status": "CONFIRMADA",
  "nomeContato": "Marina Souza",
  "descontoHospedePct": 15
}
```

**Body com erro (1/6): 13 pessoas**

```json
{
  "codigoReserva": "HB-4F7K",
  "data": "2026-12-02",
  "horario": "20:00",
  "pessoas": 13,
  "cadeirinhas": 0,
  "pertoFogueira": false
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Alguns campos estão inválidos. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X",
  "campos": [
    {
      "campo": "pessoas",
      "erro": "deve ser menor que ou igual a 12"
    }
  ]
}
```

**Body com erro (2/6): Horário lotado (já há 40 pessoas às 20:00 nessa data)**

```json
{
  "codigoReserva": "HB-4F7K",
  "data": "2026-12-02",
  "horario": "20:00",
  "pessoas": 1,
  "cadeirinhas": 0,
  "pertoFogueira": false
}
```

**Resultado esperado: 409 Conflict**

```json
{
  "status": 409,
  "codigo": "VAGAS_ESGOTADAS",
  "titulo": "Ugh! A caverna do Banquete lotou.",
  "mensagem": "Esse horário tem 0 lugar(es) livre(s) e você pediu 1. Nenhuma mesa foi reservada. Escolha outro horário.",
  "ref": "HB-7Q2X",
  "dados": {
    "vagasRestantes": 0
  }
}
```

**Body com erro (3/6): Horário que não existe**

```json
{
  "codigoReserva": "HB-4F7K",
  "data": "2026-12-02",
  "horario": "18:00",
  "pessoas": 2,
  "cadeirinhas": 0,
  "pertoFogueira": false
}
```

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Horário não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (4/6): Data fora da estadia**

```json
{
  "codigoReserva": "HB-4F7K",
  "data": "2026-12-20",
  "horario": "20:00",
  "pessoas": 2,
  "cadeirinhas": 0,
  "pertoFogueira": false
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "A data da mesa precisa estar dentro da sua estadia (2026-12-01 a 2026-12-04). Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (5/6): Mais cadeirinhas que pessoas**

```json
{
  "codigoReserva": "HB-4F7K",
  "data": "2026-12-02",
  "horario": "20:00",
  "pessoas": 2,
  "cadeirinhas": 3,
  "pertoFogueira": false
}
```

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Não pode haver mais cadeirinhas que pessoas. Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (6/6): Reserva ainda PENDENTE**

```json
{
  "codigoReserva": "HB-4F7K",
  "data": "2026-12-02",
  "horario": "20:00",
  "pessoas": 2,
  "cadeirinhas": 0,
  "pertoFogueira": false
}
```

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "A reserva está PENDENTE e essa ação não é possível nesse estado. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

### 6.5 GET `/restaurante/reservas-mesa/{codigo}`

**Detalhe da mesa**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "codigo": "BQ-9XQ2",
  "data": "2026-12-02",
  "horario": "20:00:00",
  "pessoas": 4,
  "cadeirinhas": 1,
  "pertoFogueira": true,
  "status": "CONFIRMADA",
  "nomeContato": "Marina Souza",
  "descontoHospedePct": 15
}
```

**Body com erro (1/2): Código inexistente**

Rota: `GET /restaurante/reservas-mesa/BQ-ZZZZ`

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos isso.",
  "mensagem": "Reserva de mesa não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Mesa de outra pessoa**

Headers: (sem Authorization e sem X-Responsavel-Email)

**Resultado esperado: 403 Forbidden**

```json
{
  "status": 403,
  "codigo": "SEM_PERMISSAO",
  "titulo": "Uga! Essa toca não é sua.",
  "mensagem": "Esta reserva pertence a outra pessoa. Nada foi alterado. Entre com a conta certa ou informe o e-mail do responsável.",
  "ref": "HB-7Q2X"
}
```

### 6.6 DELETE `/restaurante/reservas-mesa/{codigo}`

**Cancelar mesa (até 2 h antes)**  ·  Acesso: dono

Headers: `Authorization: Bearer {{token}}  **ou**  X-Responsavel-Email: marina.souza@example.com`

**Body**

(não tem body)

**Resultado esperado: 200 OK**

```json
{
  "codigo": "BQ-9XQ2",
  "data": "2026-12-02",
  "horario": "20:00:00",
  "pessoas": 4,
  "cadeirinhas": 1,
  "pertoFogueira": true,
  "status": "CANCELADA",
  "nomeContato": "Marina Souza",
  "descontoHospedePct": 15
}
```

**Body com erro (1/2): Faltam menos de 2 horas**

(não tem body)

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "FORA_DO_PRAZO",
  "titulo": "Uga! Fora do prazo.",
  "mensagem": "A mesa só pode ser cancelada até 2 horas antes do horário. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

**Body com erro (2/2): Mesa já cancelada**

(não tem body)

**Resultado esperado: 422 Unprocessable Entity**

```json
{
  "status": 422,
  "codigo": "ESTADO_INVALIDO",
  "titulo": "Uga! Isso não dá agora.",
  "mensagem": "Essa mesa já foi cancelada ou encerrada. Nada foi alterado.",
  "ref": "HB-7Q2X"
}
```

---

## 7. Erros que podem acontecer em qualquer rota

**JSON malformado ou data fora do formato**

Como provocar: POST /reservas  com body  `{ "cavernaId": ` 

**Resultado esperado: 400 Bad Request**

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "titulo": "Uga! Algo não bateu nos dados.",
  "mensagem": "Não conseguimos ler os dados enviados (confira o JSON e o formato das datas, ex.: 2026-10-12). Nada foi salvo. Corrija e tente novamente.",
  "ref": "HB-7Q2X"
}
```

**Token de acesso vencido (15 min)**

Como provocar: Qualquer rota com `Authorization: Bearer <token vencido>`

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "SESSAO_EXPIRADA",
  "titulo": "Uga! Sua sessão acabou.",
  "mensagem": "Sua sessão expirou. Nada foi alterado. Entre novamente para continuar.",
  "ref": "HB-7Q2X"
}
```

**Token adulterado ou lixo**

Como provocar: Qualquer rota com `Authorization: Bearer abc.def.ghi`

**Resultado esperado: 401 Unauthorized**

```json
{
  "status": 401,
  "codigo": "NAO_AUTENTICADO",
  "titulo": "Uga! Quem é você?",
  "mensagem": "Você precisa entrar na sua conta para fazer isso. Nada foi alterado. Faça login e tente de novo.",
  "ref": "HB-7Q2X"
}
```

**Rota inexistente (com token)**

Como provocar: GET /api/v1/rota-que-nao-existe

**Resultado esperado: 404 Not Found**

```json
{
  "status": 404,
  "codigo": "NAO_ENCONTRADO",
  "titulo": "Uga! Não achamos esse caminho.",
  "mensagem": "Essa rota não existe na API. Nada foi alterado. Confira o endereço e o método da chamada.",
  "ref": "HB-7Q2X"
}
```

**Falha inesperada ou banco fora do ar**

Como provocar: Qualquer rota; o stack trace vai só para o log, com a mesma `ref`

**Resultado esperado: 503 Service Unavailable**

```json
{
  "status": 503,
  "codigo": "INDISPONIVEL",
  "titulo": "Ugh! A caverna desabou por um instante.",
  "mensagem": "Algo falhou do nosso lado e o seu pedido pode não ter sido concluído. Confira em Minhas reservas antes de repetir. Tente de novo em instantes; se persistir, informe o código HB-7Q2X.",
  "ref": "HB-7Q2X"
}
```
