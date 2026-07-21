-- =============================================================================
-- Remove a massa gerada por carga-de-performance.sql (PBI-36)
-- =============================================================================
--
-- Apaga apenas o que a carga criou, identificado pelo marcador na chave de
-- idempotencia e pelo prefixo do documento do cedente. Dado de teste funcional
-- e o seed da V3 continuam intactos.
--
--   docker compose exec -T db psql -U postgres -d credit-engine \
--       -f /dev/stdin < scripts/carga-de-performance-limpar.sql
-- =============================================================================

\timing on

BEGIN;

-- A trilha e' append-only por trigger; desligar e' o unico jeito de limpar
-- massa sintetica. Em producao ninguem faz isso — e' o proposito da tabela.
ALTER TABLE evento_auditoria DISABLE TRIGGER tg_evento_auditoria_imutavel;

CREATE TEMP TABLE operacoes_da_carga AS
SELECT operacao_id AS id FROM liquidacao WHERE chave_idempotencia LIKE 'carga-perf-%';

DELETE FROM evento_auditoria
 WHERE entidade = 'OPERACAO' AND entidade_id IN (SELECT id FROM operacoes_da_carga);

DELETE FROM liquidacao WHERE chave_idempotencia LIKE 'carga-perf-%';
DELETE FROM recebivel  WHERE operacao_id IN (SELECT id FROM operacoes_da_carga);
DELETE FROM operacao   WHERE id IN (SELECT id FROM operacoes_da_carga);
DELETE FROM cedente    WHERE documento LIKE '9%' AND razao_social LIKE 'Cedente de Carga %';

ALTER TABLE evento_auditoria ENABLE TRIGGER tg_evento_auditoria_imutavel;

COMMIT;

ANALYZE operacao;
ANALYZE liquidacao;

SELECT 'operacao' AS tabela, COUNT(*) FROM operacao
UNION ALL SELECT 'liquidacao', COUNT(*) FROM liquidacao;
