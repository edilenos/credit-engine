-- =============================================================================
-- V4 — Calendario de dias nao uteis, cobertura 2025-2030
--
-- Alimenta a convencao de contagem BUS_252 (dias uteis sobre 252), usada por
-- ativos indexados ao CDI.
--
-- E' um calendario BANCARIO, nao a lista de feriados civis. Carnaval e Corpus
-- Christi sao ponto facultativo na lei federal, mas sao dias sem compensacao
-- bancaria, e e' isso que importa para contagem de dias uteis em renda fixa —
-- e' a mesma base que a ANBIMA usa.
--
-- LIMITACAO DECLARADA: so feriados NACIONAIS, e so nos anos abaixo. Feriado
-- municipal e estadual estao fora de escopo. A cobertura precisa ser estendida
-- antes de 2031, e o codigo falha explicitamente para datas fora dela em vez
-- de contar dias uteis a menos em silencio.
--
-- Os feriados moveis foram calculados pelo Computus gregoriano, nao
-- consultados de memoria: Sexta-feira Santa = Pascoa-2, Carnaval = Pascoa-48 e
-- -47, Corpus Christi = Pascoa+60.
-- =============================================================================


-- ------------------------------------------------- feriados de data fixa
--
-- Gerados por produto cartesiano entre os anos e as datas, em vez de 54 linhas
-- repetidas: menos superficie para erro de digitacao.
INSERT INTO feriado (data, descricao, abrangencia)
SELECT make_date(anos.ano, datas.mes, datas.dia), datas.descricao, 'NACIONAL'
  FROM generate_series(2025, 2030) AS anos(ano)
 CROSS JOIN (VALUES
        ( 1,  1, 'Confraternizacao Universal'),
        ( 4, 21, 'Tiradentes'),
        ( 5,  1, 'Dia do Trabalho'),
        ( 9,  7, 'Independencia do Brasil'),
        (10, 12, 'Nossa Senhora Aparecida'),
        (11,  2, 'Finados'),
        (11, 15, 'Proclamacao da Republica'),
        (11, 20, 'Dia Nacional de Zumbi e da Consciencia Negra'),
        (12, 25, 'Natal')
       ) AS datas(mes, dia, descricao);


-- --------------------------------------------- feriados moveis (Pascoa)
INSERT INTO feriado (data, descricao, abrangencia) VALUES
    (DATE '2025-03-03', 'Carnaval (segunda)',   'NACIONAL'),
    (DATE '2025-03-04', 'Carnaval (terca)',     'NACIONAL'),
    (DATE '2025-04-18', 'Sexta-feira Santa',    'NACIONAL'),
    (DATE '2025-06-19', 'Corpus Christi',       'NACIONAL'),

    (DATE '2026-02-16', 'Carnaval (segunda)',   'NACIONAL'),
    (DATE '2026-02-17', 'Carnaval (terca)',     'NACIONAL'),
    (DATE '2026-04-03', 'Sexta-feira Santa',    'NACIONAL'),
    (DATE '2026-06-04', 'Corpus Christi',       'NACIONAL'),

    (DATE '2027-02-08', 'Carnaval (segunda)',   'NACIONAL'),
    (DATE '2027-02-09', 'Carnaval (terca)',     'NACIONAL'),
    (DATE '2027-03-26', 'Sexta-feira Santa',    'NACIONAL'),
    (DATE '2027-05-27', 'Corpus Christi',       'NACIONAL'),

    (DATE '2028-02-28', 'Carnaval (segunda)',   'NACIONAL'),
    (DATE '2028-02-29', 'Carnaval (terca)',     'NACIONAL'),
    (DATE '2028-04-14', 'Sexta-feira Santa',    'NACIONAL'),
    (DATE '2028-06-15', 'Corpus Christi',       'NACIONAL'),

    (DATE '2029-02-12', 'Carnaval (segunda)',   'NACIONAL'),
    (DATE '2029-02-13', 'Carnaval (terca)',     'NACIONAL'),
    (DATE '2029-03-30', 'Sexta-feira Santa',    'NACIONAL'),
    (DATE '2029-05-31', 'Corpus Christi',       'NACIONAL'),

    (DATE '2030-03-04', 'Carnaval (segunda)',   'NACIONAL'),
    (DATE '2030-03-05', 'Carnaval (terca)',     'NACIONAL'),
    (DATE '2030-04-19', 'Sexta-feira Santa',    'NACIONAL'),
    (DATE '2030-06-20', 'Corpus Christi',       'NACIONAL');
