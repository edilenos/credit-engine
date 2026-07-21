-- =============================================================================
-- V5 — Indice para a ordenacao por valor no Extrato de Liquidacao (PBI-36)
-- =============================================================================
--
-- Medido, nao suposto. Com 100 mil liquidacoes, EXPLAIN ANALYZE mostrou onde o
-- extrato varria a tabela grande e onde nao varria:
--
--   filtro por periodo             ->  ix_liquidacao_liquidado_em, 1-5 ms   OK
--   filtro por cedente             ->  ix_operacao_cedente_criado, 2,7 ms   OK
--   ordenar por valor, sem filtro  ->  Parallel Seq Scan,          45 ms    <-
--   ordenar por cedente, sem filtro->  Parallel Seq Scan,          62 ms    <-
--
-- Os dois primeiros ja estavam cobertos por indices que existiam desde a V2, e
-- por isso NAO ha indice novo para eles: indice que nao muda plano so custa
-- escrita e espaco.
--
-- O terceiro caso e' o que este indice resolve. "Maiores liquidacoes do
-- periodo" e' consulta de gestao legitima, e a lista branca de ordenacao expoe
-- VALOR_LIQUIDADO justamente para permiti-la.
--
-- O quarto caso — ordenar por razao social sem filtro — continua com Seq Scan
-- e fica assim de proposito: a ordenacao acontece sobre o resultado do join, e
-- nenhum indice em `cedente` a evita. Resolver exigiria desnormalizar a razao
-- social para dentro de `liquidacao`, o que trocaria uma consulta rara por
-- duplicacao permanente. A limitacao esta registrada em docs/performance.md.
-- =============================================================================

-- DESC nas duas colunas casa com o ORDER BY do extrato, que ordena decrescente
-- e desempata por id. Indice ascendente tambem serve para o Postgres, que
-- percorre para tras, mas o descendente evita o passo de reversao e deixa a
-- intencao explicita.
CREATE INDEX ix_liquidacao_valor_desc
    ON liquidacao (valor_liquidado DESC, id DESC);

COMMENT ON INDEX ix_liquidacao_valor_desc IS
    'Ordenacao por valor no Extrato de Liquidacao sem filtro seletivo. Sem ele, LIMIT 20 sobre 100 mil linhas faz Parallel Seq Scan (~45 ms). Ver docs/performance.md.';
