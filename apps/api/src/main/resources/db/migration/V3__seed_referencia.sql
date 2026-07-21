-- =============================================================================
-- V3 — Seed de referencia
--
-- Separado do schema de proposito: migracao de estrutura e migracao de dado
-- evoluem por motivos diferentes e devem poder ser lidas em separado.
--
-- Timestamps sao fixos, nunca now(): migracao precisa ser reproduzivel, e
-- valor dependente do relogio muda o resultado a cada execucao.
--
-- Os dados marcados como DEMONSTRACAO existem para que o sistema recem-subido
-- ja permita simular sem cadastro manual. Em producao, a forma de exclui-los
-- e' mover este bloco para uma location propria do Flyway (ex. db/demo) e
-- ativa-la so em desenvolvimento via spring.flyway.locations.
-- =============================================================================


-- --------------------------------------------------- moedas (§3.1: BRL e USD)
INSERT INTO moeda (codigo, nome, escala_padrao) VALUES
    ('BRL', 'Real Brasileiro',             2),
    ('USD', 'Dolar dos Estados Unidos',    2);


-- ------------------------------------- tipos de recebivel (§3.2 do enunciado)
--
-- O spread numerico mora aqui, para ajuste sem deploy. A REGRA de derivacao
-- mora na Strategy correspondente (PBI-18) — e' essa separacao que impede o
-- padrao de virar um Map disfarcado.
--
-- periodicidade e convencao_contagem precisam ser coerentes entre si: ACT_30
-- produz expoente em MESES, entao casa com spread MENSAL. Combinar MENSAL com
-- BUS_252 (que produz expoente ANUAL) nao lanca erro e produz preco errado por
-- ordem de grandeza — e' o risco R7 do backlog.
INSERT INTO tipo_recebivel (codigo, nome, spread, periodicidade, convencao_contagem) VALUES
    ('DUPLICATA_MERCANTIL', 'Duplicata Mercantil', 0.015000, 'MENSAL', 'ACT_30'),
    ('CHEQUE_PRE_DATADO',   'Cheque Pre-datado',   0.025000, 'MENSAL', 'ACT_30');


-- ------------------------------------------------ taxa base vigente do fundo
--
-- Custo de oportunidade do fundo, 1% a.m. Com o spread de duplicata (1,5%),
-- fecha os 2,5% a.m. usados no exemplo trabalhado da documentacao.
INSERT INTO parametro_precificacao (taxa_base, periodicidade, vigencia_inicio) VALUES
    (0.010000, 'MENSAL', TIMESTAMPTZ '2026-01-01 00:00:00+00');


-- ------------------------------------------------------- DEMONSTRACAO: cedente
--
-- CNPJ com digitos verificadores validos, para nao quebrar a validacao de
-- documento quando ela entrar (PBI-32).
INSERT INTO cedente (documento, razao_social, ativo) VALUES
    ('11222333000181', 'Cedente Exemplo Ltda', TRUE);


-- ------------------------------------------------------ DEMONSTRACAO: cambio
--
-- Cotacoes ficticias, apenas para permitir simular operacao cross-currency
-- logo apos subir o sistema. Em uso real, as cotacoes entram pelo endpoint de
-- atualizacao (PBI-14) ou pelo provedor mockado (PBI-15).
--
-- Os dois sentidos sao gravados: a conversao inversa nao e' derivada por
-- 1/cotacao, para nao introduzir arredondamento no caminho do dinheiro.
INSERT INTO taxa_cambio (moeda_origem_id, moeda_destino_id, cotacao, vigencia_inicio, fonte)
SELECT o.id, d.id, v.cotacao, TIMESTAMPTZ '2026-01-01 00:00:00+00', 'MANUAL'
  FROM (VALUES ('BRL', 'USD', 0.185000),
               ('USD', 'BRL', 5.400000)) AS v(origem, destino, cotacao)
  JOIN moeda o ON o.codigo = v.origem
  JOIN moeda d ON d.codigo = v.destino;
