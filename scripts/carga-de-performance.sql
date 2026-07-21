-- =============================================================================
-- Massa de dados para medir o Extrato de Liquidacao (PBI-36)
-- =============================================================================
--
-- Gera 100 mil operacoes liquidadas, distribuidas entre varios cedentes, as
-- duas moedas e dois anos de datas. Reproduzivel: roda do zero, sempre com os
-- mesmos volumes, sem depender de dump.
--
--   docker compose exec -T db psql -U postgres -d credit-engine \
--       -f /dev/stdin < scripts/carga-de-performance.sql
--
-- Para remover tudo depois:  \i scripts/carga-de-performance-limpar.sql
--
-- NAO rodar contra base com dados reais: usa faixas de id proprias e apaga o
-- que gerou pelo marcador em chave_idempotencia.
-- =============================================================================

\timing on

-- ---------------------------------------------------------------- parametros
\set QTD_OPERACOES 100000
\set QTD_CEDENTES 200

BEGIN;

-- ------------------------------------------------------------------ cedentes
--
-- Documento com 14 digitos gerado a partir do indice. Nao sao CNPJs validos —
-- a validacao de digito verificador ainda nao existe (PBI-32 a deixou para o
-- dominio), e para medir plano de execucao o que importa e' a cardinalidade.
INSERT INTO cedente (documento, razao_social, ativo)
SELECT LPAD((90000000000000 + i)::text, 14, '0'),
       'Cedente de Carga ' || i,
       TRUE
  FROM generate_series(1, :QTD_CEDENTES) AS i
ON CONFLICT (documento) DO NOTHING;

-- ----------------------------------------------------------------- operacoes
--
-- Metade em moeda unica, metade cross-currency. A constraint
-- ck_operacao_cambio_coerente exige cotacao exatamente nas cross-currency,
-- entao o CASE precisa casar com a escolha de moeda.
INSERT INTO operacao (cedente_id, moeda_titulo_id, moeda_liquidacao_id, taxa_cambio_id,
                      valor_face_total, valor_presente_total, valor_liquidacao,
                      status, version, criado_em)
SELECT c.id,
       brl.id,
       CASE WHEN i % 2 = 0 THEN usd.id ELSE brl.id END,
       CASE WHEN i % 2 = 0 THEN cambio.id ELSE NULL END,
       face.valor,
       ROUND(face.valor * 0.96, 2),
       CASE WHEN i % 2 = 0 THEN ROUND(face.valor * 0.96 * 0.185, 2)
            ELSE ROUND(face.valor * 0.96, 2) END,
       'LIQUIDADA',
       0,
       -- Espalha por dois anos: filtro de periodo sobre datas todas iguais
       -- mediria um plano que nao acontece na pratica.
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + (i % 730) * INTERVAL '1 day'
  FROM generate_series(1, :QTD_OPERACOES) AS i
  CROSS JOIN LATERAL (SELECT ROUND((1000 + (i % 9000))::numeric, 2) AS valor) AS face
  JOIN LATERAL (SELECT id FROM moeda WHERE codigo = 'BRL') AS brl ON TRUE
  JOIN LATERAL (SELECT id FROM moeda WHERE codigo = 'USD') AS usd ON TRUE
  JOIN LATERAL (SELECT id FROM taxa_cambio
                 WHERE moeda_origem_id = brl.id AND moeda_destino_id = usd.id
                 LIMIT 1) AS cambio ON TRUE
  JOIN LATERAL (SELECT id FROM cedente
                 WHERE documento = LPAD((90000000000000 + (i % :QTD_CEDENTES) + 1)::text, 14, '0')
               ) AS c ON TRUE;

-- ---------------------------------------------------------------- recebiveis
--
-- Um por operacao. O extrato nao le esta tabela, mas a operacao sem recebivel
-- seria estado que o sistema nunca produz, e plano medido sobre dado
-- impossivel nao vale.
INSERT INTO recebivel (operacao_id, tipo_recebivel_id, parametro_precificacao_id,
                       numero_documento, sacado_documento, valor_face, data_vencimento,
                       convencao_aplicada, expoente_aplicado, taxa_base_aplicada,
                       spread_aplicado, valor_presente)
SELECT o.id, tipo.id, param.id,
       'CARGA-' || o.id,
       '52998224725',
       o.valor_face_total,
       DATE '2026-09-04',
       'ACT_30', 1.5333333333, 0.010000, 0.015000,
       o.valor_presente_total
  FROM operacao o
  JOIN LATERAL (SELECT id FROM tipo_recebivel WHERE codigo = 'DUPLICATA_MERCANTIL') AS tipo ON TRUE
  JOIN LATERAL (SELECT id FROM parametro_precificacao ORDER BY vigencia_inicio LIMIT 1) AS param ON TRUE
 WHERE NOT EXISTS (SELECT 1 FROM recebivel r WHERE r.operacao_id = o.id);

-- --------------------------------------------------------------- liquidacoes
--
-- O marcador 'carga-perf-' na chave e' o que permite limpar depois sem tocar
-- em dado de teste funcional.
INSERT INTO liquidacao (operacao_id, chave_idempotencia, valor_liquidado,
                        cotacao_aplicada, liquidado_em, liquidado_por)
SELECT o.id,
       'carga-perf-' || o.id,
       o.valor_liquidacao,
       CASE WHEN o.taxa_cambio_id IS NOT NULL THEN 0.185000 ELSE NULL END,
       o.criado_em + INTERVAL '2 hours',
       'carga.performance'
  FROM operacao o
 WHERE NOT EXISTS (SELECT 1 FROM liquidacao l WHERE l.operacao_id = o.id);

COMMIT;

-- O planejador decide por estatistica: sem ANALYZE ele opera com estimativas
-- de uma tabela que ainda estava vazia, e o plano medido nao seria o plano de
-- producao.
ANALYZE cedente;
ANALYZE operacao;
ANALYZE recebivel;
ANALYZE liquidacao;

SELECT 'operacao'   AS tabela, COUNT(*) FROM operacao
UNION ALL SELECT 'liquidacao', COUNT(*) FROM liquidacao
UNION ALL SELECT 'recebivel',  COUNT(*) FROM recebivel
UNION ALL SELECT 'cedente',    COUNT(*) FROM cedente;
