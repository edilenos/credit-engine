# Análise de performance do Extrato de Liquidação

O enunciado fala em "grandes volumes de dados". Este documento transforma isso em número: massa gerada por script, plano de execução antes e depois, e tempo medido sobre HTTP real.

**Resumo:** com 100 mil liquidações, o p95 do extrato ficou entre **24 e 55 ms** — a meta era 1 s. Um `Seq Scan` foi eliminado por índice, outro por reescrita da consulta, e um terceiro permanece de propósito, documentado abaixo.

## Como reproduzir

```bash
docker compose up -d db
docker compose exec -T db psql -U postgres -d credit-engine -f /dev/stdin < scripts/carga-de-performance.sql
cd apps/api && ./mvnw spring-boot:run    # Flyway aplica a V5
# medições...
docker compose exec -T db psql -U postgres -d credit-engine -f /dev/stdin < scripts/carga-de-performance-limpar.sql
```

A massa: **100 mil operações liquidadas**, 200 cedentes, as duas moedas (metade cross-currency), datas espalhadas por dois anos. O espalhamento importa — medir filtro de período sobre datas todas iguais produziria um plano que não acontece na prática.

O script roda `ANALYZE` ao final. Sem isso o planejador decide com estatísticas de uma tabela que estava vazia, e o plano medido não seria o de produção.

## Ambiente da medição

| | |
|---|---|
| Postgres | 17.5 em contêiner, porta 55432 |
| API | Spring Boot 4.1 local, JVM 21 |
| Volume | 100.000 liquidações / 100.000 operações / 100.000 recebíveis / 201 cedentes |
| Método | 50 requisições HTTP por cenário, `curl -w %{time_total}` |

> Máquina de desenvolvimento, não ambiente dimensionado. Os números servem para comparar antes e depois e para mostrar ordem de grandeza — não como capacidade de produção.

## O que foi medido, e o que mudou

### 1. Ordenar por valor sem filtro — resolvido com índice

Consulta legítima ("maiores liquidações"), e a lista branca de ordenação expõe `VALOR_LIQUIDADO` justamente para permiti-la.

**Antes:**
```
->  Parallel Seq Scan on liquidacao l (actual rows=50000 loops=2)
Execution Time: 45.467 ms
```

**Depois** (`ix_liquidacao_valor_desc`, migração `V5`):
```
->  Index Scan using ix_liquidacao_valor_desc on liquidacao l (actual rows=20 loops=1)
->  Index Only Scan using operacao_pkey on operacao o (actual rows=1 loops=20)
Execution Time: 0.206 ms
```

**45,5 ms → 0,21 ms.** O `LIMIT 20` passa a ler 20 linhas em vez de 100 mil.

### 2. O `COUNT` juntava tabelas que não precisava — resolvido sem índice

Este foi o achado que mais rendeu, e não é índice: é a consulta.

O `COUNT` roda em **toda** requisição do extrato e não tem `LIMIT` para escapar cedo. Ele juntava `operacao`, `cedente` e `moeda` mesmo quando nenhum filtro usava essas tabelas. Como todos os FKs envolvidos são `NOT NULL`, cada linha de `liquidacao` casa com exatamente uma linha de cada — **os joins não podiam alterar a contagem**.

**Antes:**
```
->  Parallel Seq Scan on liquidacao l  (+ Seq Scan em operacao, cedente, moeda)
Execution Time: 90.067 ms
```

**Depois** (joins montados conforme os filtros ativos):
```
->  Index Only Scan using ix_liquidacao_liquidado_em on liquidacao l
Execution Time: 8.132 ms
```

**90 ms → 8,1 ms**, sem criar índice nenhum. O `Index Only Scan` aparece porque, sem joins, o Postgres conta pelo índice e nem toca na heap.

### 3. Filtros por período e por cedente — já estavam cobertos

Medidos antes de decidir qualquer índice:

| Consulta | Plano | Tempo |
|---|---|---|
| Período (3 meses) | `Index Scan Backward using ix_liquidacao_liquidado_em` | 3,2 ms |
| Cedente | `Bitmap Index Scan using ix_operacao_cedente_criado` | 7,2 ms |
| Período + cedente + moeda | idem, combinados | 2,7 ms |

Os índices vinham da migração `V2`. **Nenhum índice novo foi criado para esses casos** — índice que não muda plano só custa escrita e espaço.

### 4. Ordenar por razão social sem filtro — limitação aceita

```
->  Parallel Seq Scan on liquidacao l (actual rows=50000 loops=2)
Execution Time: 64.738 ms
```

Fica assim de propósito. A ordenação acontece sobre o **resultado do join**, e nenhum índice em `cedente` a evita — o Postgres precisa materializar o join antes de ordenar. Resolver exigiria desnormalizar a razão social para dentro de `liquidacao`, trocando uma consulta rara por duplicação permanente e um ponto de inconsistência.

Ainda assim são 65 ms sobre 100 mil linhas, folgadamente dentro da meta de 1 s. **Se este caso passar a ser frequente, a resposta certa é a coluna desnormalizada** — não outro índice.

## Resultado ponta a ponta

50 requisições HTTP por cenário, com a massa de 100 mil liquidações:

| Cenário | p50 | **p95** | máx |
|---|---|---|---|
| Sem filtro, página 1 | 33 ms | **50 ms** | 182 ms |
| Período (3 meses) | 27 ms | **41 ms** | 55 ms |
| Período + cedente + moeda | 15 ms | **24 ms** | 31 ms |
| Ordenado por valor, sem filtro | 29 ms | **42 ms** | 54 ms |
| Página profunda (offset 4000) | 45 ms | **55 ms** | 70 ms |

## Critérios atendidos

| # | Critério | Resultado |
|---|---|---|
| **D2** | p95 do extrato < 1 s com 100 mil liquidações e filtros combinados | ✅ **24 ms** no cenário combinado — 40× de folga |
| **D3** | Consultas do extrato usam índice, sem `Seq Scan` na tabela grande | ✅ nos quatro caminhos principais; ⚠️ a ordenação por razão social mantém `Seq Scan`, com justificativa acima |

## O que este exercício não cobre

- **Página profunda de verdade.** `OFFSET 4000` custou 55 ms; `OFFSET 500000` degrada linearmente, porque o Postgres descarta as linhas puladas. A resposta seria paginação por cursor (`WHERE (liquidado_em, id) < (?, ?)`), que muda o contrato da API e não estava no escopo. Com o teto de 100 por página, chegar lá exige 5.000 requisições.
- **Concorrência.** As medições são sequenciais. Não dizem nada sobre o comportamento com N clientes simultâneos disputando o pool.
- **Escrita.** O índice novo custa manutenção em cada `INSERT` de liquidação. Com uma liquidação por operação e volume de mesa, é irrelevante — mas é custo, não almoço grátis.
