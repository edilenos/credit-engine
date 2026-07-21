# Critérios de aceite não-funcionais

Exigidos pela §5.2 do enunciado, que pede critérios **planejados e definidos** nas quatro dimensões.

Este documento é a versão **definitiva** — a §8 do `backlog.md` era o planejamento, com metas especuladas antes de existir código. Aqui cada critério traz o número medido, o método, e o veredito. Os que não foram atendidos estão declarados como tal.

## Como ler o veredito

| Símbolo | Significa |
|---|---|
| ✅ | Atendido, com evidência reproduzível |
| ⚠️ | Parcialmente atendido — a condição está descrita |
| ❌ | **Não atendido** |
| 🔍 | Verificado por inspeção, não por execução — a limitação está dita |

Ambiente das medições: Postgres 17.5 em contêiner, API local na JVM 21, máquina de desenvolvimento. Servem para ordem de grandeza e comparação, não como capacidade de produção.

---

## 1. Usabilidade

| # | Critério | Método | Veredito |
|---|---|---|---|
| **U1** | Simulação responde em menos de 500 ms percebidos, com indicação visual | 50 requisições HTTP, lote de 1 título (o caso do painel) | ✅ **p50 22 ms · p95 39 ms** |
| **U2** | Erros em português, acionáveis, sem jargão nem stacktrace | Testes de contrato em todos os caminhos de erro | ✅ |
| **U3** | Moeda e data em pt-BR, com a moeda sempre indicada | Formatação centralizada em `lib/format.ts` | 🔍 |
| **U4** | Filtros e paginação na URL — estado compartilhável e resistente a reload | `useFiltroNaUrl` lê `useSearchParams` e escreve com `router.replace` | 🔍 |
| **U5** | Formulário valida antes de submeter e aponta o campo | Portão de validação em `PainelDoOperador` | 🔍 |

**Sobre o U2.** Nenhuma resposta de erro carrega stacktrace, nome de classe, SQL ou nome de tabela. Isso vale especialmente para as mensagens de infraestrutura, que trazem nome de constraint e de coluna: são **logadas** inteiras e respondidas com texto fixo. Há teste para cada caminho, e cada resposta traz `correlationId`, que liga a reclamação do cliente à linha de log.

**Sobre os três 🔍.** Não há test runner no frontend — `pnpm build` e `pnpm lint` são as únicas verificações automatizadas. U3, U4 e U5 foram verificados por leitura de código e por exercício manual das requisições que a tela emite, **não** por automação de navegador. É a lacuna mais relevante deste projeto e está declarada também no `apps/frontend/CLAUDE.md`.

---

## 2. Segurança

| # | Critério | Método | Veredito |
|---|---|---|---|
| **S1** | Nenhum segredo versionado, em nenhum ponto do histórico | `git log -p --all` com varredura por padrão de credencial, 93 commits | ✅ zero ocorrências |
| **S2** | Toda entrada validada no servidor | Testes de contrato com payload malicioso | ✅ |
| **S3** | Erros não expõem stacktrace, SQL, tabela nem versão de biblioteca | Testes sobre o handler global | ✅ |
| **S4** | Todo SQL parametrizado, inclusive o nativo do relatório | Revisão + teste de injeção | ✅ |
| **S5** | Contêineres da aplicação não rodam como root | `docker inspect` + `ps` dentro do contêiner | ✅ |
| **S6** | CORS restrito à origem do frontend, sem `*` | Configuração + teste de preflight | ✅ |
| **S7** | Coleções sempre paginadas, com teto de página | Teste de contrato | ✅ |

**S1 — o histórico está limpo, e isso teve custo.** A primeira versão do `application.yaml` chegou a ter a senha real do Postgres como *default* de `${DB_PASSWORD:…}`. Foi detectada **antes do primeiro commit**, então nunca entrou no histórico. O incidente está no `AI_USAGE.md`, e o hook de pre-commit passou a varrer o diff por padrão de credencial.

**S4 — a ordenação é o ponto que não pode ser parâmetro.** Filtros entram como parâmetro nomeado. Coluna de ordenação **não pode**: identificador não se vincula, `ORDER BY ?` não existe em SQL. Por isso vem de um enum, e valor fora da lista responde `422`:

```
?ordenarPor=x;%20DROP%20TABLE%20liquidacao
→ 422, e teste confirma que a tabela continua intacta
```

**S5 — verificado no processo, não no `Config.User`.** Os contêineres de API e frontend rodam como `credit`. O do Postgres mantém root como usuário padrão do `exec` — desenho da imagem oficial, que precisa de root para o `initdb` e derruba privilégio via `gosu`; `ps` confirma que o processo roda como `postgres`. A CI verifica isso automaticamente a cada execução.

**S7 — `tamanho=1000000` devolve 100**, não a tabela inteira. Limite que barra o uso legítimo não é limite, é bug: há teste garantindo que o lote cheio de 500 continua passando.

---

## 3. Desempenho

| # | Critério | Método | Veredito |
|---|---|---|---|
| **D1** | p95 da simulação abaixo de 200 ms | 50 requisições HTTP | ⚠️ **39 ms** no painel · **1.066 ms** em lote de 500 |
| **D2** | p95 do extrato abaixo de 1 s com 100 mil liquidações e filtros combinados | 50 requisições por cenário | ✅ **24 ms** — 40× de folga |
| **D3** | Extrato usa índice, sem `Seq Scan` na tabela grande | `EXPLAIN ANALYZE` documentado | ⚠️ nos quatro caminhos principais; um caso justificado |
| **D4** | Pool de conexões dimensionado e observável | `/actuator/prometheus` | ✅ |
| **D5** | Nenhuma consulta N+1 no caminho de leitura | Contagem de SQL por requisição | ❌ **N+1 na precificação em lote** |

### D1 — atendido no caso real, não no pior caso

| Cenário | p50 | **p95** | máx |
|---|---|---|---|
| 1 título (o que o painel envia) | 22 ms | **39 ms** | 54 ms |
| 500 títulos (teto do lote) | 923 ms | **1.066 ms** | 1.120 ms |

O critério foi escrito sem qualificar o tamanho do lote. **No caso que um usuário experimenta — o painel, que envia um título — ele é atendido com folga de 5×.** No pior caso permitido pela API, não é.

A causa é conhecida e está medida no D5 abaixo: metade do custo do lote grande é uma consulta redundante por título. A outra metade é aritmética legítima — 500 potenciações decimais em precisão arbitrária, que é o preço de não usar `double`.

### D3 — um `Seq Scan` mantido de propósito

Ordenar por razão social sem filtro continua varrendo (64,7 ms). A ordenação acontece sobre o **resultado do join**, e nenhum índice em `cedente` a evita; resolver exigiria desnormalizar a coluna para dentro de `liquidacao`, trocando uma consulta rara por duplicação permanente. Está dentro da meta de tempo, e a limitação está escrita em `performance.md` com o gatilho que a reabriria.

### D4 — o pool é observável

```
hikaricp_connections{pool="credit-engine-pool"}                10.0
hikaricp_connections_active / _idle / _pending
hikaricp_connections_acquire_seconds{count,sum,max}
```

O `10.0` bate com `maximum-pool-size: 10`, dimensionado explicitamente e não deixado no padrão.

### ❌ D5 — há um N+1, e ele é real

Contagem de SQL por requisição, com `logging.level.org.hibernate.SQL=DEBUG`:

| Títulos no lote | Queries |
|---:|---:|
| 1 | 8 |
| 5 | 16 |
| 20 | 46 |

**Duas queries por título.** Uma é o `INSERT` do recebível — inevitável, é o dado sendo gravado. A outra é a consulta do parâmetro de precificação vigente:

```
20 × select pp1_0 … from parametro_precificacao
20 × insert into recebivel
 2 × select m1_0  … from moeda
 1 × select tr1_0 … from tipo_recebivel
```

`MotorDePrecificacao.parametroVigenteEm` é chamado **uma vez por título**, e todos os títulos do lote compartilham a mesma `dataOperacao` — logo, o mesmo parâmetro vigente. São 499 consultas redundantes num lote de 500.

**Por que isso não foi corrigido aqui:** este é um PBI de documentação, e a correção é de código de produção, no `negocio/precificacao`. Fazê-la numa branch `docs/` produziria um commit cujo tipo mente sobre o conteúdo, num projeto onde a higiene do histórico é avaliada. O critério fica declarado como não atendido, que é o que a §5.2 pede de um critério de aceite honesto.

**Escopo da falha:** o N+1 está no caminho de **escrita em lote** (cessão) e na simulação de lote. O caminho de leitura — que é o que o critério menciona textualmente — está limpo: `GET /operacoes/{id}` faz **1 query** graças ao `EntityGraph`, e o extrato não passa pelo Hibernate.

---

## 4. Escalabilidade

| # | Critério | Método | Veredito |
|---|---|---|---|
| **E1** | API stateless — escala horizontal sem sessão sticky | Busca por `HttpSession`, `@SessionAttributes`, escopo de sessão | ✅ zero ocorrências |
| **E2** | Optimistic locking permite concorrência sem lock pessimista global | 8 threads contra banco real | ✅ |
| **E3** | Nenhum endpoint retorna coleção ilimitada | Revisão de contrato + teste | ✅ |
| **E4** | Migrações versionadas permitem subir instância nova sem passo manual | `compose down -v && up` | ✅ |
| **E5** | Cotação e taxa base são append-only | Modelo de dados | ✅ |

**E2 — provado, não afirmado.** Oito threads disputando a mesma operação: **exatamente uma** liquidação criada, sete com `409`. E as sete receberam *"conflito de concorrência"*, não *"já foi liquidada"* — ou seja, todas passaram pela checagem de status e colidiram no commit. Quem resolveu foi o `@Version` e a constraint, não a validação de estado. Um teste verde onde as threads serializam não provaria nada.

**E3 — com uma exceção deliberada.** `GET /api/v1/cadastros/{tipos-recebivel,moedas}` não é paginado. São os produtos e as moedas que o fundo opera — coleções limitadas por natureza, não dados transacionais. A regra existe para o que cresce com o uso.

**E4 — verificado do zero.** `docker compose down -v` seguido de `up`: as cinco migrações aplicadas em banco vazio, com a ordem `db healthy → api → frontend`.

**E5 — o que isso compra.** Cotação nunca é sobrescrita; é acrescentada com nova `vigencia_inicio`. É o que mantém a precificação de ontem reproduzível — e é a mesma razão por que cada parâmetro é gravado duas vezes na operação, como FK (linhagem) e como valor (imutabilidade).

---

## Resumo

**19 de 22 critérios atendidos**, três com ressalva declarada:

| | Critério | Situação |
|---|---|---|
| ⚠️ | **D1** | Atendido no painel (39 ms); não atendido em lote de 500 (1.066 ms) |
| ⚠️ | **D3** | Um `Seq Scan` mantido, com justificativa e gatilho de revisão |
| ❌ | **D5** | N+1 na precificação em lote — medido, quantificado, não corrigido |

Além disso, **três critérios de usabilidade (U3, U4, U5) foram verificados por inspeção**, não por automação: não há test runner no frontend. É a lacuna mais relevante do projeto.

O D5 e o D1-em-lote são o mesmo problema visto de dois ângulos, e a correção é pequena: elevar a busca do parâmetro para fora do laço, já que o lote inteiro compartilha a data da operação.
