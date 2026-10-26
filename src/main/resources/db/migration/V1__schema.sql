-- =====================================================================
-- Huga Buga Hotel - schema (25 tabelas)
-- Tipos escolhidos para bater com as entidades JPA (ddl-auto = validate).
-- Dinheiro sempre em centavos (integer). Timestamps com fuso (timestamptz).
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- UUID
CREATE EXTENSION IF NOT EXISTS btree_gist;   -- restrição de exclusão por datas

-- ---------------------------------------------------------------------
-- 3.1 Conta do hóspede
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome             varchar(255) NOT NULL,
    sobrenome        varchar(255) NOT NULL,
    email            varchar(255) NOT NULL UNIQUE,
    senha_hash       varchar(255) NOT NULL,
    celular          varchar(255),
    pais             varchar(2),
    tipo_documento   varchar(255),
    numero_documento varchar(255),
    data_nascimento  date,
    aceita_ofertas   boolean      NOT NULL DEFAULT false,
    saldo_ossos      integer      NOT NULL DEFAULT 0,
    criado_em        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_usuario_saldo_ossos CHECK (saldo_ossos >= 0)
);
CREATE UNIQUE INDEX uk_usuario_email_lower ON usuario (lower(email));

CREATE TABLE membro_tribo (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      uuid         NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    nome            varchar(255) NOT NULL,
    tipo            varchar(20)  NOT NULL,
    data_nascimento date,
    observacoes     varchar(500),
    CONSTRAINT ck_membro_tipo CHECK (tipo IN ('ADULTO', 'CRIANCA')),
    CONSTRAINT ck_membro_crianca_nasc CHECK (tipo = 'ADULTO' OR data_nascimento IS NOT NULL)
);

CREATE TABLE refresh_token (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id uuid         NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    token_hash varchar(255) NOT NULL UNIQUE,
    expira_em  timestamptz  NOT NULL,
    revogado   boolean      NOT NULL DEFAULT false
);

-- ---------------------------------------------------------------------
-- 3.2 Cavernas
-- ---------------------------------------------------------------------
CREATE TABLE caverna (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                varchar(255)     NOT NULL UNIQUE,
    nome                varchar(255)     NOT NULL,
    categoria           varchar(20)      NOT NULL,
    ala                 varchar(255),
    resumo              varchar(255),
    descricao           text,
    capacidade          integer          NOT NULL,
    camas               integer          NOT NULL DEFAULT 0,
    area_m2             integer          NOT NULL DEFAULT 0,
    preco_base_centavos integer          NOT NULL,
    acessivel           boolean          NOT NULL DEFAULT false,
    aceita_pets         boolean          NOT NULL DEFAULT false,
    nota_media          double precision NOT NULL DEFAULT 0,   -- double: a entidade usa double
    total_relatos       integer          NOT NULL DEFAULT 0,
    ativa               boolean          NOT NULL DEFAULT true,
    CONSTRAINT ck_caverna_categoria CHECK (categoria IN ('FAMILIA', 'CASAL', 'AMIGOS', 'GRUPO')),
    CONSTRAINT ck_caverna_capacidade CHECK (capacidade > 0),
    CONSTRAINT ck_caverna_preco CHECK (preco_base_centavos >= 0),
    CONSTRAINT ck_caverna_nota CHECK (nota_media >= 0 AND nota_media <= 5)
);

CREATE TABLE unidade_caverna (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    caverna_id uuid         NOT NULL REFERENCES caverna (id) ON DELETE CASCADE,
    codigo     varchar(255) NOT NULL UNIQUE,
    ativa      boolean      NOT NULL DEFAULT true
);

CREATE TABLE foto_caverna (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    caverna_id uuid         NOT NULL REFERENCES caverna (id) ON DELETE CASCADE,
    url        varchar(255) NOT NULL,
    legenda    varchar(255),
    ordem      integer      NOT NULL DEFAULT 0
);

CREATE TABLE comodidade (
    id    varchar(100) PRIMARY KEY,       -- id textual legível (ex.: fogueira-privativa)
    nome  varchar(255) NOT NULL,
    icone varchar(255)
);

CREATE TABLE caverna_comodidade (          -- ligação do @ManyToMany de Caverna
    caverna_id    uuid         NOT NULL REFERENCES caverna (id) ON DELETE CASCADE,
    comodidade_id varchar(100) NOT NULL REFERENCES comodidade (id) ON DELETE CASCADE,
    PRIMARY KEY (caverna_id, comodidade_id)
);

CREATE TABLE tarifa_especial (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    caverna_id    uuid         NOT NULL REFERENCES caverna (id) ON DELETE CASCADE,
    data          date         NOT NULL,
    preco_centavos integer     NOT NULL,
    motivo        varchar(255),
    CONSTRAINT uk_tarifa_caverna_data UNIQUE (caverna_id, data),
    CONSTRAINT ck_tarifa_preco CHECK (preco_centavos >= 0)
);

-- ---------------------------------------------------------------------
-- 3.3 Reserva (checkout)
-- ---------------------------------------------------------------------
CREATE TABLE cupom (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo       varchar(255) NOT NULL UNIQUE,
    tipo         varchar(20)  NOT NULL,
    valor        integer      NOT NULL,        -- PERCENTUAL: 10 = 10%. VALOR_FIXO: centavos
    valido_ate   date,
    usos_maximos integer,                      -- nulo = ilimitado
    usos         integer      NOT NULL DEFAULT 0,
    ativo        boolean      NOT NULL DEFAULT true,
    CONSTRAINT ck_cupom_tipo CHECK (tipo IN ('PERCENTUAL', 'VALOR_FIXO')),
    CONSTRAINT ck_cupom_valor CHECK (valor > 0),
    CONSTRAINT ck_cupom_percentual CHECK (tipo <> 'PERCENTUAL' OR valor <= 100),
    CONSTRAINT ck_cupom_usos CHECK (usos >= 0)
);

CREATE TABLE reserva (
    id                         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo                     varchar(255) NOT NULL UNIQUE,             -- HB-XXXX
    usuario_id                 uuid REFERENCES usuario (id) ON DELETE SET NULL,   -- opcional: reserva sem conta
    caverna_id                 uuid         NOT NULL REFERENCES caverna (id),
    unidade_id                 uuid         NOT NULL REFERENCES unidade_caverna (id),
    check_in                   date         NOT NULL,
    check_out                  date         NOT NULL,
    adultos                    integer      NOT NULL,
    criancas                   integer      NOT NULL DEFAULT 0,
    status                     varchar(30)  NOT NULL,
    expira_em                  timestamptz  NOT NULL,
    prorrogada                 boolean      NOT NULL DEFAULT false,
    responsavel_nome           varchar(255),
    responsavel_email          varchar(255),
    responsavel_celular        varchar(255),
    responsavel_pais           varchar(2),
    responsavel_tipo_documento varchar(255),
    responsavel_documento      varchar(255),
    pedido_especial            text,
    late_checkout              boolean      NOT NULL DEFAULT false,
    valor_diarias              integer      NOT NULL DEFAULT 0,
    valor_extras               integer      NOT NULL DEFAULT 0,
    desconto                   integer      NOT NULL DEFAULT 0,
    ossos_usados               integer      NOT NULL DEFAULT 0,
    total                      integer      NOT NULL DEFAULT 0,
    cupom_id                   uuid REFERENCES cupom (id),
    checkin_online_em          timestamptz,
    cancelada_em               timestamptz,
    criado_em                  timestamptz  NOT NULL DEFAULT now(),
    atualizado_em              timestamptz,
    CONSTRAINT ck_reserva_status CHECK (status IN
        ('PENDENTE', 'AGUARDANDO_PAGAMENTO', 'CONFIRMADA', 'CANCELADA', 'EXPIRADA', 'CONCLUIDA')),
    CONSTRAINT ck_reserva_datas CHECK (check_out > check_in),
    CONSTRAINT ck_reserva_pessoas CHECK (adultos >= 1 AND criancas >= 0),
    CONSTRAINT ck_reserva_valores CHECK (valor_diarias >= 0 AND valor_extras >= 0 AND desconto >= 0
                                         AND ossos_usados >= 0 AND total >= 0),
    -- Última barreira contra reserva dupla: a mesma unidade nunca tem duas reservas ativas em datas
    -- sobrepostas. O intervalo [) deixa o check-out de um hóspede coincidir com o check-in do próximo.
    CONSTRAINT ex_reserva_sem_sobreposicao EXCLUDE USING gist (
        unidade_id WITH =,
        daterange(check_in, check_out, '[)') WITH &&
    ) WHERE (status IN ('PENDENTE', 'AGUARDANDO_PAGAMENTO', 'CONFIRMADA'))
);

CREATE TABLE relato (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    caverna_id uuid        NOT NULL REFERENCES caverna (id) ON DELETE CASCADE,
    reserva_id uuid        NOT NULL UNIQUE REFERENCES reserva (id),   -- um relato por estadia
    usuario_id uuid        NOT NULL REFERENCES usuario (id),
    nota       integer     NOT NULL,
    texto      text,
    criado_em  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_relato_nota CHECK (nota BETWEEN 1 AND 5)
);

CREATE TABLE movimento_ossos (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    reserva_id uuid REFERENCES reserva (id) ON DELETE SET NULL,
    quantidade integer     NOT NULL,     -- positivo ganha, negativo usa
    motivo     varchar(30) NOT NULL,
    criado_em  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_movimento_motivo CHECK (motivo IN ('GANHO_ESTADIA', 'RESGATE', 'AJUSTE')),
    CONSTRAINT ck_movimento_quantidade CHECK (quantidade <> 0)
);

CREATE TABLE favorito (
    usuario_id uuid NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    caverna_id uuid NOT NULL REFERENCES caverna (id) ON DELETE CASCADE,
    PRIMARY KEY (usuario_id, caverna_id)
);

CREATE TABLE hospede_reserva (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id uuid         NOT NULL REFERENCES reserva (id) ON DELETE CASCADE,
    nome       varchar(255) NOT NULL,
    tipo       varchar(20)  NOT NULL,
    idade      integer,
    CONSTRAINT ck_hospede_tipo CHECK (tipo IN ('ADULTO', 'CRIANCA')),
    CONSTRAINT ck_hospede_idade CHECK (idade IS NULL OR idade BETWEEN 0 AND 17),
    CONSTRAINT ck_hospede_crianca_idade CHECK (tipo = 'ADULTO' OR idade IS NOT NULL)
);

CREATE TABLE extra (
    id             varchar(100) PRIMARY KEY,   -- ex.: KIT_MINI_CACADOR
    nome           varchar(255) NOT NULL,
    descricao      varchar(255),
    icone          varchar(255),
    preco_centavos integer      NOT NULL,
    cobranca       varchar(20)  NOT NULL,
    incluso        boolean      NOT NULL DEFAULT false,
    ativo          boolean      NOT NULL DEFAULT true,
    CONSTRAINT ck_extra_preco CHECK (preco_centavos >= 0),
    CONSTRAINT ck_extra_cobranca CHECK (cobranca IN ('POR_ESTADIA', 'POR_CRIANCA', 'POR_PESSOA'))
);

CREATE TABLE reserva_extra (
    reserva_id             uuid         NOT NULL REFERENCES reserva (id) ON DELETE CASCADE,
    extra_id               varchar(100) NOT NULL REFERENCES extra (id),
    quantidade             integer      NOT NULL,
    preco_unitario_centavos integer     NOT NULL,   -- CONGELADO no momento da compra
    subtotal_centavos      integer      NOT NULL,
    PRIMARY KEY (reserva_id, extra_id),
    CONSTRAINT ck_reserva_extra_qtd CHECK (quantidade > 0),
    CONSTRAINT ck_reserva_extra_valores CHECK (preco_unitario_centavos >= 0 AND subtotal_centavos >= 0)
);

CREATE TABLE pagamento (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id      uuid         NOT NULL REFERENCES reserva (id) ON DELETE CASCADE,
    metodo          varchar(20)  NOT NULL,
    status          varchar(20)  NOT NULL,
    valor_centavos  integer      NOT NULL,
    parcelas        integer,                  -- 1 a 6, só cartão
    cartao_final    varchar(4),               -- nunca o número completo nem o CVV
    cartao_bandeira varchar(255),
    pix_copia_cola  text,
    pix_expira_em   timestamptz,
    motivo_recusa   varchar(255),
    criado_em       timestamptz  NOT NULL DEFAULT now(),
    aprovado_em     timestamptz,
    CONSTRAINT ck_pagamento_metodo CHECK (metodo IN ('CARTAO', 'PIX', 'NA_CHEGADA')),
    CONSTRAINT ck_pagamento_status CHECK (status IN ('PENDENTE', 'APROVADO', 'RECUSADO', 'EXPIRADO')),
    CONSTRAINT ck_pagamento_valor CHECK (valor_centavos >= 0),
    CONSTRAINT ck_pagamento_parcelas CHECK (parcelas IS NULL OR (parcelas BETWEEN 1 AND 6 AND metodo = 'CARTAO'))
);

-- ---------------------------------------------------------------------
-- 3.4 Experiências
-- ---------------------------------------------------------------------
CREATE TABLE atividade (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome           varchar(255) NOT NULL,
    categoria      varchar(20)  NOT NULL,
    descricao      text,
    duracao_min    integer      NOT NULL DEFAULT 0,
    idade_min      integer,
    idade_max      integer,
    preco_centavos integer      NOT NULL DEFAULT 0,   -- 0 = Grátis
    cobranca       varchar(20)  NOT NULL,
    destaque       varchar(255),
    ativa          boolean      NOT NULL DEFAULT true,
    CONSTRAINT ck_atividade_categoria CHECK (categoria IN ('CRIANCAS', 'FAMILIA', 'AVENTURA', 'NOITE', 'ADULTOS')),
    CONSTRAINT ck_atividade_cobranca CHECK (cobranca IN ('POR_PESSOA', 'POR_CRIANCA')),
    CONSTRAINT ck_atividade_preco CHECK (preco_centavos >= 0),
    CONSTRAINT ck_atividade_idades CHECK (idade_min IS NULL OR idade_max IS NULL OR idade_min <= idade_max)
);

CREATE TABLE sessao_atividade (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    atividade_id   uuid        NOT NULL REFERENCES atividade (id) ON DELETE CASCADE,
    inicio         timestamptz NOT NULL,
    vagas_totais   integer     NOT NULL,
    vagas_ocupadas integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_sessao_atividade_inicio UNIQUE (atividade_id, inicio),
    CONSTRAINT ck_sessao_vagas CHECK (vagas_totais >= 0 AND vagas_ocupadas BETWEEN 0 AND vagas_totais)
);

CREATE TABLE agendamento_atividade (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id     uuid        NOT NULL REFERENCES reserva (id) ON DELETE CASCADE,
    sessao_id      uuid        NOT NULL REFERENCES sessao_atividade (id),
    quantidade     integer     NOT NULL,
    valor_centavos integer     NOT NULL,     -- vai para a conta da toca
    status         varchar(20) NOT NULL,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_agendamento_status CHECK (status IN ('ATIVO', 'CANCELADO')),
    CONSTRAINT ck_agendamento_valores CHECK (quantidade > 0 AND valor_centavos >= 0)
);

CREATE TABLE alerta_vaga (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   uuid REFERENCES usuario (id) ON DELETE SET NULL,
    email        varchar(255) NOT NULL,
    tipo         varchar(20)  NOT NULL,
    caverna_id   uuid REFERENCES caverna (id) ON DELETE CASCADE,
    sessao_id    uuid REFERENCES sessao_atividade (id) ON DELETE CASCADE,
    check_in     date,
    check_out    date,
    pessoas      integer,
    notificado_em timestamptz,
    CONSTRAINT ck_alerta_tipo CHECK (tipo IN ('CAVERNA', 'ATIVIDADE'))
);

-- ---------------------------------------------------------------------
-- 3.5 O Banquete
-- ---------------------------------------------------------------------
CREATE TABLE item_cardapio (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome           varchar(255) NOT NULL,
    descricao      varchar(255),
    preco_centavos integer      NOT NULL,
    categoria      varchar(20)  NOT NULL,
    tags           text[]       NOT NULL DEFAULT '{}',
    ordem          integer      NOT NULL DEFAULT 0,
    ativo          boolean      NOT NULL DEFAULT true,
    CONSTRAINT ck_item_preco CHECK (preco_centavos >= 0),
    CONSTRAINT ck_item_categoria CHECK (categoria IN
        ('NA_FOGUEIRA', 'ENTRADAS', 'MENU_KIDS', 'DA_HORTA', 'SOBREMESAS', 'POCOES'))
);

CREATE TABLE horario_mesa (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    horario              time    NOT NULL UNIQUE,
    capacidade_pessoas   integer NOT NULL DEFAULT 40,
    CONSTRAINT ck_horario_capacidade CHECK (capacidade_pessoas > 0)
);

CREATE TABLE reserva_mesa (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo          varchar(255) NOT NULL UNIQUE,     -- BQ-XXXX
    usuario_id      uuid REFERENCES usuario (id) ON DELETE SET NULL,
    reserva_id      uuid         NOT NULL REFERENCES reserva (id) ON DELETE CASCADE,
    nome_contato    varchar(255),
    celular_contato varchar(255),
    data            date         NOT NULL,
    horario         time         NOT NULL,
    pessoas         integer      NOT NULL,
    cadeirinhas     integer      NOT NULL DEFAULT 0,
    perto_fogueira  boolean      NOT NULL DEFAULT false,
    status          varchar(20)  NOT NULL,
    criado_em       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_mesa_status CHECK (status IN ('CONFIRMADA', 'CANCELADA', 'NAO_COMPARECEU')),
    CONSTRAINT ck_mesa_pessoas CHECK (pessoas BETWEEN 1 AND 12),
    CONSTRAINT ck_mesa_cadeirinhas CHECK (cadeirinhas >= 0)
);

-- ---------------------------------------------------------------------
-- Índices (filtros e jobs)
-- ---------------------------------------------------------------------
CREATE INDEX ix_membro_usuario        ON membro_tribo (usuario_id);
CREATE INDEX ix_refresh_usuario       ON refresh_token (usuario_id);
CREATE INDEX ix_movimento_usuario     ON movimento_ossos (usuario_id, criado_em DESC);
CREATE INDEX ix_unidade_caverna       ON unidade_caverna (caverna_id);
CREATE INDEX ix_foto_caverna          ON foto_caverna (caverna_id, ordem);
CREATE INDEX ix_relato_caverna        ON relato (caverna_id, criado_em DESC);
CREATE INDEX ix_reserva_usuario       ON reserva (usuario_id);
CREATE INDEX ix_reserva_status_expira ON reserva (status, expira_em);       -- job de expiração
CREATE INDEX ix_reserva_status_saida  ON reserva (status, check_out);       -- job de conclusão
CREATE INDEX ix_hospede_reserva       ON hospede_reserva (reserva_id);
CREATE INDEX ix_pagamento_reserva     ON pagamento (reserva_id);
CREATE INDEX ix_pagamento_pix_aberto  ON pagamento (metodo, status, pix_expira_em);
CREATE INDEX ix_agendamento_reserva   ON agendamento_atividade (reserva_id);
CREATE INDEX ix_reserva_mesa_data     ON reserva_mesa (data, horario);
CREATE INDEX ix_reserva_mesa_reserva  ON reserva_mesa (reserva_id);
