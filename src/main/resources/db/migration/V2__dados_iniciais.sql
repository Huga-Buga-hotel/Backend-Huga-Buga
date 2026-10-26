-- =====================================================================
-- Huga Buga Hotel - dados iniciais (cavernas, atividades, cardápio...)
-- Usuária de teste: marina.souza@example.com  |  senha: Uga!Buga2026
-- ATENÇÃO: contém usuária e senha de teste. Não use esta carga em produção.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Cavernas (o design detalha 4; o front fala em "7": cadastre mais 3 depois, em V3__...)
-- nota_media e total_relatos são valores de demonstração; são recalculados a cada novo relato.
-- ---------------------------------------------------------------------
INSERT INTO caverna (slug, nome, categoria, ala, resumo, descricao, capacidade, camas, area_m2,
                     preco_base_centavos, acessivel, aceita_pets, nota_media, total_relatos) VALUES
('toca-do-mamute', 'Toca do Mamute', 'FAMILIA', 'Ala do Vale',
 'A toca preferida das tribos grandes: espaço de sobra e fogueira só sua.',
 'Escavada na rocha com vista para o vale, a Toca do Mamute acomoda até 5 pessoas com conforto de sobra. Tem fogueira privativa, camas de pele macias e um cantinho para os pequenos caçadores. Café jurássico incluso todas as manhãs.',
 5, 3, 42, 52000, true, false, 4.9, 128),
('gruta-do-casal', 'Gruta do Casal', 'CASAL', 'Ala da Cascata',
 'Aconchego a dois, com banheira de pedra e silêncio de cachoeira.',
 'Uma gruta íntima para quem quer fugir da tribo por uns dias. Banheira de pedra, luz de tochas e o som da cascata ao fundo. Ideal para casais em busca de descanso.',
 2, 1, 26, 39000, false, false, 4.8, 96),
('covil-dos-cacadores', 'Covil dos Caçadores', 'AMIGOS', 'Ala da Trilha',
 'Para a turma toda: camas coletivas, mesa de pedra e fogueira grande.',
 'Pensado para grupos de amigos, o Covil tem camas coletivas, uma mesa de pedra para as histórias da noite e fogueira ampla. Pets são bem-vindos.',
 6, 4, 55, 61000, false, true, 4.7, 74),
('suite-do-chefe-da-tribo', 'Suíte do Chefe da Tribo', 'GRUPO', 'Ala do Topo',
 'A maior e mais exclusiva: banheira, fogueira e vista de todo o vale.',
 'No ponto mais alto do hotel, a Suíte do Chefe reúne tudo: banheira de pedra, fogueira privativa, berço de pele e uma vista de tirar o fôlego. Acomoda até 8 pessoas.',
 8, 5, 85, 98000, true, false, 5.0, 41);

-- Unidades físicas (2 a 3 por caverna): é o que permite "Só restam 2 tocas"
INSERT INTO unidade_caverna (caverna_id, codigo)
SELECT c.id, v.codigo
FROM (VALUES ('toca-do-mamute', 'T-07'), ('toca-do-mamute', 'T-08'), ('toca-do-mamute', 'T-09'),
             ('gruta-do-casal', 'G-01'), ('gruta-do-casal', 'G-02'), ('gruta-do-casal', 'G-03'),
             ('covil-dos-cacadores', 'C-01'), ('covil-dos-cacadores', 'C-02'),
             ('suite-do-chefe-da-tribo', 'S-01'), ('suite-do-chefe-da-tribo', 'S-02')) AS v(slug, codigo)
JOIN caverna c ON c.slug = v.slug;

-- Comodidades
INSERT INTO comodidade (id, nome, icone) VALUES
('fogueira-privativa', 'Fogueira privativa', 'local_fire_department'),
('banheira-de-pedra',  'Banheira de pedra',  'bathtub'),
('vista-pro-vale',     'Vista pro vale',     'landscape'),
('berco-de-pele',      'Berço de pele',      'crib'),
('rede-de-descanso',   'Rede de descanso',   'weekend');

INSERT INTO caverna_comodidade (caverna_id, comodidade_id)
SELECT c.id, v.comodidade
FROM (VALUES ('toca-do-mamute', 'fogueira-privativa'), ('toca-do-mamute', 'vista-pro-vale'),
             ('toca-do-mamute', 'berco-de-pele'), ('toca-do-mamute', 'rede-de-descanso'),
             ('gruta-do-casal', 'banheira-de-pedra'), ('gruta-do-casal', 'vista-pro-vale'),
             ('covil-dos-cacadores', 'fogueira-privativa'), ('covil-dos-cacadores', 'rede-de-descanso'),
             ('suite-do-chefe-da-tribo', 'fogueira-privativa'), ('suite-do-chefe-da-tribo', 'banheira-de-pedra'),
             ('suite-do-chefe-da-tribo', 'vista-pro-vale'), ('suite-do-chefe-da-tribo', 'berco-de-pele')
     ) AS v(slug, comodidade)
JOIN caverna c ON c.slug = v.slug;

-- Fotos (URLs fictícias)
INSERT INTO foto_caverna (caverna_id, url, legenda, ordem)
SELECT c.id, 'https://cdn.hugabuga.example/cavernas/' || c.slug || '/' || v.n || '.jpg', v.legenda, v.n
FROM caverna c
CROSS JOIN (VALUES (1, 'Vista da caverna'), (2, 'Fogueira e sala'), (3, 'Cantinho de descanso')) AS v(n, legenda);

-- Tarifas especiais (feriados): +25% (Natal e Réveillon: +50%).
-- Inclui o feriado de 12 de outubro pedido no guia e os próximos feriados.
INSERT INTO tarifa_especial (caverna_id, data, preco_centavos, motivo)
SELECT c.id, v.data, c.preco_base_centavos * v.pct / 100, v.motivo
FROM caverna c
CROSS JOIN (VALUES
    (DATE '2026-10-10', 125, 'Feriado de Nossa Senhora Aparecida'),
    (DATE '2026-10-11', 125, 'Feriado de Nossa Senhora Aparecida'),
    (DATE '2026-10-12', 125, 'Feriado de Nossa Senhora Aparecida'),
    (DATE '2026-11-02', 125, 'Finados'),
    (DATE '2026-11-15', 125, 'Proclamação da República'),
    (DATE '2026-11-20', 125, 'Consciência Negra'),
    (DATE '2026-12-24', 150, 'Natal'),
    (DATE '2026-12-25', 150, 'Natal'),
    (DATE '2026-12-31', 150, 'Réveillon'),
    (DATE '2027-01-01', 150, 'Ano Novo'),
    (DATE '2027-10-10', 125, 'Feriado de Nossa Senhora Aparecida'),
    (DATE '2027-10-11', 125, 'Feriado de Nossa Senhora Aparecida'),
    (DATE '2027-10-12', 125, 'Feriado de Nossa Senhora Aparecida')
) AS v(data, pct, motivo);

-- ---------------------------------------------------------------------
-- Extras (passo 2 do checkout)
-- ---------------------------------------------------------------------
INSERT INTO extra (id, nome, descricao, icone, preco_centavos, cobranca, incluso, ativo) VALUES
('CAFE_JURASSICO',   'Café jurássico',     'Incluso em toda reserva',                      'free_breakfast',   0,     'POR_ESTADIA', true,  true),
('KIT_MINI_CACADOR', 'Kit mini-caçador',   'Chapéu, mapa do tesouro e kit de escavação',   'backpack',         4500,  'POR_CRIANCA', false, true),
('LATE_CHECKOUT',    'Late check-out',     'Saída até as 15h em vez das 12h',              'schedule',         8000,  'POR_ESTADIA', false, true),
('TRANSFER',         'Transfer',           'Ida e volta do aeroporto até o hotel',         'airport_shuttle',  18000, 'POR_ESTADIA', false, true),
('FESTA_DA_TRIBO',   'Festa da tribo',     'Decoração e bolo para uma comemoração',        'celebration',      35000, 'POR_ESTADIA', false, true),
('FOGUEIRA_VIP',     'Fogueira VIP',       'Noite de fogueira com lenha, petiscos e vinho', 'local_fire_department', 15000, 'POR_ESTADIA', false, true);

-- Cupom: 10% até o fim do ano
INSERT INTO cupom (codigo, tipo, valor, valido_ate, usos_maximos, usos, ativo)
VALUES ('UGABUGA10', 'PERCENTUAL', 10, DATE '2026-12-31', NULL, 0, true);

-- ---------------------------------------------------------------------
-- Atividades (7) e sessões para os próximos 30 dias, com vagas variadas
-- ---------------------------------------------------------------------
INSERT INTO atividade (nome, categoria, descricao, duracao_min, idade_min, idade_max, preco_centavos, cobranca, destaque) VALUES
('Escavação de ovo de dino', 'CRIANCAS', 'Os pequenos escavam a areia até encontrar o ovo fóssil e levam um certificado de paleontólogo.', 60, 4, 12, 0, 'POR_CRIANCA', NULL),
('Pintura rupestre', 'FAMILIA', 'Toda a tribo pinta a parede da caverna com tintas naturais.', 90, NULL, NULL, 4500, 'POR_PESSOA', NULL),
('Trilha dos mamutes', 'AVENTURA', 'Caminhada guiada pela mata para ver as pegadas gigantes.', 120, 6, NULL, 6000, 'POR_PESSOA', NULL),
('Arco e flecha', 'AVENTURA', 'Aula de pontaria com monitor, equipamento de segurança incluso.', 45, 8, NULL, 5000, 'POR_PESSOA', NULL),
('Fogueira & histórias', 'NOITE', 'Lendas da tribo ao redor da fogueira, com marshmallows.', 60, NULL, NULL, 0, 'POR_PESSOA', NULL),
('Banho de lama', 'ADULTOS', 'Banho de lama termal para relaxar e renovar a pele.', 60, 16, NULL, 12000, 'POR_PESSOA', NULL),
('Mini-caçadores', 'CRIANCAS', 'Circuito de aventuras para os pequenos, sempre com monitor.', 120, 3, 10, 8000, 'POR_CRIANCA', 'MAIS AMADA PELAS CRIANÇAS');

-- Horários por atividade (nome, hora local, vagas)
INSERT INTO sessao_atividade (atividade_id, inicio, vagas_totais, vagas_ocupadas)
SELECT a.id,
       ((d::timestamp + v.hora) AT TIME ZONE 'America/Sao_Paulo'),
       v.vagas,
       -- ocupação variada e determinística (0 .. vagas-1: nunca lotada por acaso)
       (EXTRACT(DOY FROM d)::int + length(a.nome)) % v.vagas
FROM atividade a
JOIN (VALUES
    ('Escavação de ovo de dino', TIME '09:00', 8),
    ('Escavação de ovo de dino', TIME '15:00', 8),
    ('Pintura rupestre',         TIME '10:30', 12),
    ('Pintura rupestre',         TIME '16:00', 12),
    ('Trilha dos mamutes',       TIME '08:00', 10),
    ('Arco e flecha',            TIME '11:00', 6),
    ('Arco e flecha',            TIME '14:30', 6),
    ('Fogueira & histórias',     TIME '20:00', 30),
    ('Banho de lama',            TIME '17:00', 6),
    ('Mini-caçadores',           TIME '09:30', 8),
    ('Mini-caçadores',           TIME '14:00', 8)
) AS v(nome, hora, vagas) ON v.nome = a.nome
CROSS JOIN generate_series(current_date + 1, current_date + 30, interval '1 day') AS d;

-- Sessões esgotadas de propósito (para testar o "esgotada" e o botão "Avisar")
UPDATE sessao_atividade s
SET vagas_ocupadas = s.vagas_totais
FROM atividade a
WHERE s.atividade_id = a.id
  AND a.nome = 'Mini-caçadores'
  AND s.inicio IN (((current_date + 1)::timestamp + TIME '09:30') AT TIME ZONE 'America/Sao_Paulo',
                   ((current_date + 3)::timestamp + TIME '09:30') AT TIME ZONE 'America/Sao_Paulo');

-- ---------------------------------------------------------------------
-- O Banquete: cardápio e horários de mesa
-- ---------------------------------------------------------------------
INSERT INTO item_cardapio (nome, descricao, preco_centavos, categoria, tags, ordem) VALUES
('Costela de Mamute',        'Costela bovina assada por 10 horas na brasa, com farofa de milho.',        8900, 'NA_FOGUEIRA', ARRAY['Carro-chefe', 'Pra dividir'], 1),
('Espeto do Caçador',        'Cubos de carne e legumes grelhados no espeto de madeira.',                 4900, 'NA_FOGUEIRA', ARRAY['Sem glúten'], 2),
('Coxa de Pterodáctilo',     'Coxa de frango defumada com molho barbecue da tribo.',                     5900, 'NA_FOGUEIRA', ARRAY['Pra dividir'], 3),
('Peixe do Rio Gelado',      'Truta na brasa com ervas e limão siciliano.',                              7200, 'NA_FOGUEIRA', ARRAY['Sem glúten'], 4),
('Pão de Pedra',             'Pão rústico assado na pedra com manteiga defumada.',                       2400, 'ENTRADAS',    ARRAY['Pra dividir'], 5),
('Ovos da Tribo',            'Ovos de codorna caramelizados na brasa.',                                  2800, 'ENTRADAS',    ARRAY['Sem glúten'], 6),
('Mini-Mamute',              'Mini hambúrguer de carne com batatas rústicas.',                           3200, 'MENU_KIDS',   ARRAY['Kids'], 7),
('Dino-Nuggets',             'Nuggets de frango em formato de dinossauro, com purê.',                    2900, 'MENU_KIDS',   ARRAY['Kids'], 8),
('Raízes da Tribo',          'Mandioca, batata-doce e cará assados com ervas.',                          3800, 'DA_HORTA',    ARRAY['Vegano', 'Sem glúten'], 9),
('Salada do Vale',           'Folhas frescas, frutas e castanhas.',                                      3200, 'DA_HORTA',    ARRAY['Vegano'], 10),
('Vulcão de Chocolate',      'Bolinho quente de chocolate com sorvete de baunilha.',                     2800, 'SOBREMESAS',  ARRAY['Pra dividir'], 11),
('Fruta na Brasa',           'Abacaxi grelhado com mel e canela.',                                       2200, 'SOBREMESAS',  ARRAY['Sem glúten', 'Vegano'], 12),
('Poção Verde',              'Suco de abacaxi com hortelã e gengibre.',                                  1800, 'POCOES',      ARRAY['Vegano'], 13),
('Poção do Pântano',         'Limonada com espinafre, maçã e limão.',                                    1900, 'POCOES',      ARRAY['Vegano', 'Kids'], 14);

INSERT INTO horario_mesa (horario, capacidade_pessoas) VALUES
(TIME '19:00', 40), (TIME '19:30', 40), (TIME '20:00', 40),
(TIME '20:30', 40), (TIME '21:00', 40), (TIME '21:30', 40);

-- ---------------------------------------------------------------------
-- Usuária de teste: Marina Souza (senha: Uga!Buga2026)
-- Saldo de 450 ossos: 520 ganhos - 70 resgatados (nível Xamã, por acumulado >= 500).
-- ---------------------------------------------------------------------
INSERT INTO usuario (nome, sobrenome, email, senha_hash, celular, pais, tipo_documento, numero_documento,
                     data_nascimento, aceita_ofertas, saldo_ossos)
VALUES ('Marina', 'Souza', 'marina.souza@example.com',
        '$2a$10$ngYwxpxYkGlUig5H1Zg5qewFVUZ35D7sQpb2yUfEYBJPXIi7Lm/g6',
        '+55 11 98888-7777', 'BR', 'CPF', '12345678909', DATE '1989-04-12', true, 450);

INSERT INTO membro_tribo (usuario_id, nome, tipo, data_nascimento, observacoes)
SELECT u.id, v.nome, v.tipo, v.nascimento, v.obs
FROM usuario u
CROSS JOIN (VALUES
    ('Rafael', 'ADULTO',  DATE '1987-09-03',                     NULL),
    ('Theo',   'CRIANCA', (current_date - interval '9 years')::date, 'Alergia a amendoim'),
    ('Lia',    'CRIANCA', (current_date - interval '4 years')::date, NULL)
) AS v(nome, tipo, nascimento, obs)
WHERE u.email = 'marina.souza@example.com';

INSERT INTO movimento_ossos (usuario_id, quantidade, motivo)
SELECT id, 520, 'GANHO_ESTADIA' FROM usuario WHERE email = 'marina.souza@example.com';
INSERT INTO movimento_ossos (usuario_id, quantidade, motivo)
SELECT id, -70, 'RESGATE' FROM usuario WHERE email = 'marina.souza@example.com';
