# SRM Credit Engine

[![CI](https://github.com/edilenos/credit-engine/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/edilenos/credit-engine/actions/workflows/ci.yml)

Plataforma de **cessão de crédito multi-moeda** para FIDC. O fundo adquire recebíveis — duplicatas, cheques — com deságio: paga hoje um valor menor que o de face, e recebe o valor cheio no vencimento. O sistema precifica esse desconto, aplica câmbio quando a liquidação é em outra moeda, e registra a operação de forma auditável.

```
Valor Presente = Valor Face / (1 + Taxa Base + Spread) ^ Expoente
```

Três coisas nessa fórmula concentram as decisões deste projeto: **o spread varia por tipo de recebível** (Strategy), **o expoente depende da convenção de contagem de dias** (outra Strategy), e **nada disso pode passar por ponto flutuante**.

---

## Antes de tudo: escopo e prazo

O enunciado dá **3 a 4 dias úteis (24–32 h)** e permite ajuste "conforme complexidade entregue" (§9.2). Este projeto entrega o **nível Sênior completo — os 45 itens do backlog, estimados em ~78,5 h ≈ 9,8 dias de esforço**.

**Por que não cortei.** O exercício MoSCoW classificou **40 dos 45 itens como Must** — quase tudo aqui é explicitamente graduado. Os cinco cortáveis somam 9 h, ou 11% do trabalho: cortar tudo que dava para cortar economizaria pouco mais de um dia. O custo está no escopo funcional base do §3 e §4, não nos extras de nível.

**O que isso custou de verdade.** O trabalho aconteceu em **2 dias corridos de execução intensiva assistida por IA**, não em 9,8 dias de pessoa. A estimativa de 78,5 h é de esforço humano equivalente e serve para dimensionar o escopo, não para descrever o calendário. O detalhamento honesto de onde a IA ajudou e onde atrapalhou está em [`AI_USAGE.md`](AI_USAGE.md).

Nada foi cortado. O que ficou **deliberadamente de fora** é o nível 🟣 Especialista, declarado em vez de omitido: ADRs formais, design para 1M transações/minuto, proposta de EDA, IaC, e a justificativa formal de estratégia de branching.

---

## Subindo o projeto

Você não precisa de Java, Node nem Postgres instalados.

```bash
git clone https://github.com/edilenos/credit-engine.git
cd credit-engine
cp .env.example .env          # preencha DB_PASSWORD
docker compose up --build
```

| | |
|---|---|
| Frontend | http://localhost:3000 |
| API | http://localhost:8081 |
| **Swagger UI** | **http://localhost:8081/swagger-ui.html** |
| Métricas | http://localhost:8081/actuator/prometheus |

A porta da API é **8081**, não a 8080 padrão do Spring Boot.

A subida é encadeada por *health check*: `db` saudável → `api` → `frontend`. É `condition: service_healthy` e não `service_started` porque o Flyway roda na subida e falharia contra um Postgres que aceitou a conexão TCP mas ainda está no `initdb`.

<details>
<summary><b>Rodando localmente, sem contêiner</b></summary>

```bash
docker compose up -d db       # só o banco

cd apps/api && ./mvnw spring-boot:run       # :8081
cd apps/frontend && pnpm install && pnpm dev # :3000
```

O frontend precisa de `.env.local` com `NEXT_PUBLIC_API_URL=http://localhost:8081` (veja `.env.example` de `apps/frontend/`). A API lê `application-local.yaml` — gitignorado — ou as variáveis `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.

</details>

### Testes

```bash
docker compose up -d db                 # a suíte roda contra Postgres real
cd apps/api && ./mvnw verify            # lint + 299 testes
```

`verify` roda Spotless, Checkstyle e a suíte — **exatamente o que a CI e o hook de pre-push executam**. Essa identidade é proposital: comando local que difere do pipeline é como se aprende a ignorar o resultado local.

O teste de concorrência (`ConcorrenciaNaLiquidacaoTest`) sobe um servidor real com `RANDOM_PORT` e dispara 8 threads contra a mesma operação. Roda junto com os demais.

**Git hooks** ficam em `.githooks/`. Ative uma vez: `git config core.hooksPath .githooks`.

---

## Por que esta stack, para dinheiro

O enunciado trata tipagem forte e frameworks maduros como diferencial (§3). A justificativa não é "gosto de Java" — é o que cada escolha compra **neste domínio**.

### `BigDecimal` e `NUMERIC`, nunca ponto flutuante

`0.1 + 0.2` em `double` é `0.30000000000000004`. Num sistema que soma milhares de recebíveis e depois converte moeda, esse erro não fica pequeno: ele acumula com sinal sistemático.

Java tem **`BigDecimal` na biblioteca padrão** — decimal de precisão arbitrária, com escala e modo de arredondamento explícitos — e o PostgreSQL tem **`NUMERIC`**, que é decimal exato, não binário. O tipo atravessa o sistema inteiro sem conversão: coluna → entidade → cálculo → resposta.

A política está num único ponto, [`PrecisaoDecimal`](apps/api/src/main/java/br/com/srm/creditengine/dominio/PrecisaoDecimal.java): escala 2 para moeda, 6 para taxas, `HALF_EVEN` sempre.

**Por que `HALF_EVEN`.** O arredondamento comum (`HALF_UP`) tem viés: metade dos empates sempre sobe. Num volume grande de operações, isso é dinheiro criado do nada, sistematicamente a favor de um lado. `HALF_EVEN` — "arredondamento bancário" — alterna, e o viés tende a zero. É o padrão em contabilidade e é o que o IEEE 754 usa como default.

**Duas armadilhas na mesma expressão**, ambas resolvidas e comentadas no código:

- `BigDecimal.divide(x)` **sem** escala e `RoundingMode` lança `ArithmeticException` em dízima — e `VF / (1+i)^n` produz dízimas o tempo todo;
- `pow(int)` **sem** `MathContext` devolve resultado exato com escala explodindo: `1,025^24` tem 72 casas.

### Tipagem estática que pega erro de unidade

O modo de falha mais perigoso deste domínio é **silencioso**: somar um spread de 1,5% **ao mês** com um expoente em **base anual** não lança exceção nenhuma. Produz um preço plausível, errado por ordem de grandeza.

O sistema previne isso no tipo: toda taxa carrega sua `Periodicidade`, toda convenção declara a unidade que produz, e o motor **valida o par antes de calcular** — recusando a combinação como erro de negócio. Um sistema de tipos fraco não teria onde pendurar essa checagem.

### Maturidade do ecossistema onde importa

Transação declarativa (`@Transactional`), *optimistic locking* (`@Version`), migração versionada (Flyway) e pool de conexões instrumentado não são código que se escreve num take-home — são infraestrutura resolvida há vinte anos. O que o projeto agrega é usá-los **corretamente**, e o §3.3 do enunciado (ACID, sem liquidação pela metade) é exatamente onde isso se prova.

---

## Arquitetura

📐 **[C4 — Contexto](docs/c4-context.md)** · **[C4 — Contêineres](docs/c4-container.md)** (Mermaid, renderizam no GitHub)

Três camadas, com **uma exceção deliberada**:

```
aplicacao/  →  negocio/  →  persistencia/          fluxo normal
relatorio/  ────────────→  JdbcClient              exceção do §3.6
```

O §3.6 autoriza a rota de relatório a pular a camada de negócio; o §3.5 trata SQL otimizado como diferencial. As duas decisões precisavam ser **legíveis sem ler o código inteiro**, então o relatório inteiro mora em pacote próprio — e há [teste verificando que o controller depende só da consulta](apps/api/src/test/java/br/com/srm/creditengine/relatorio/ExtratoDeLiquidacaoTest.java). Se um serviço de domínio entrar ali um dia, o teste falha e a decisão volta a ser discutida em vez de erodir em silêncio.

**Não há serviço no meio porque não há regra a aplicar**: o extrato lê, projeta e pagina. Uma camada de repasse existiria no diagrama e não no comportamento.

### Onde SOLID, DRY e KISS estão no código

Princípio citado em abstrato não vale nada. Onde eles aparecem:

| Princípio | Onde | O que aconteceria sem |
|---|---|---|
| **OCP** | [`EstrategiaDeSpread`](apps/api/src/main/java/br/com/srm/creditengine/negocio/precificacao/EstrategiaDeSpread.java) e [`ConvencaoDeContagem`](apps/api/src/main/java/br/com/srm/creditengine/negocio/precificacao/ConvencaoDeContagem.java) | Produto novo entra criando **uma classe**. Há teste que registra um produto inédito via `@TestConfiguration` e confirma que o resolver o enxerga sem ser alterado |
| **DIP** | [`ResolvedorDeSpread`](apps/api/src/main/java/br/com/srm/creditengine/negocio/precificacao/ResolvedorDeSpread.java) recebe `List<EstrategiaDeSpread>` por injeção | Sem `if` nem `switch` sobre tipo. Tipo desconhecido **levanta erro**, não assume spread zero — spread nulo produz preço plausível e errado |
| **SRP** | [`CalculadoraDeValorPresente`](apps/api/src/main/java/br/com/srm/creditengine/negocio/precificacao/CalculadoraDeValorPresente.java) só aplica a fórmula; `MotorDePrecificacao` só compõe os parâmetros | A fórmula virou função pura, testável sem Spring e sem banco. Antes, conferir uma divisão custava segundos de startup |
| **DRY** | A cessão usa o **mesmo** `PrecificadorDeOperacao` da simulação | Duas implementações divergiriam, e o operador veria um número na simulação e contrataria outro |
| **KISS** | Lote síncrono (abaixo) e ausência de estado global no frontend | Duas camadas inteiras que este sistema não precisa |

> **DRY tem limite, e ele foi respeitado.** `ValidacaoDeEntradaTest` e os testes de contrato repetem estruturas de payload de propósito: teste que compartilha helper com o código que testa passa a provar que os dois concordam, não que o comportamento está certo.

### As duas famílias de Strategy

| Família | Resolve | Implementações |
|---|---|---|
| **Spread por tipo** | prêmio de risco | Duplicata 1,5% a.m. · Cheque 2,5% a.m. |
| **Convenção de contagem** | prazo → expoente | `ACT_30` · `COMERCIAL_30_360` · `ACT_360` · `ACT_365` · `TAXA_DIARIA` · `BUS_252` |

O **valor** do spread mora no banco (`tipo_recebivel.spread`), para mudar sem deploy. A **regra** de derivá-lo mora na Strategy. É essa divisão que impede o padrão de virar um `Map` disfarçado: a regra do cheque não é "2,5%", é "2,5% **e** recusa prazo acima de 180 dias", porque cheque tem horizonte de apresentação e prescrição.

**Por que `ACT_30` é o padrão.** Não existe convenção única em FIDC — cada contrato define a sua. As seis implementadas cobrem o que o mercado usa: taxa diária, base 252 para indexado ao CDI, 30/360 comercial, 365/366. `ACT_30` é o default porque **casa com a unidade dos spreads do enunciado**, que são cotados ao mês. Escolher outra como padrão criaria a incompatibilidade de unidade descrita acima, silenciosamente.

### `ch.obermuhlner:big-math`

`BigDecimal.pow()` aceita apenas expoente `int`. **Quatro das seis convenções produzem expoente fracionário** — 46 dias sob `ACT_30` dá expoente 1,5333. É o caso normal, não a exceção.

As alternativas eram arredondar o prazo para meses inteiros — o que muda o preço em ~1,1% do valor de face, mudança de negócio disfarçada de simplificação — ou usar `Math.pow` com `double`, indefensável sob um critério chamado "precisão decimal". A biblioteca faz potenciação decimal em precisão arbitrária e determinística.

---

## Decisão: o lote é síncrono e transacional

O enunciado menciona "lote de recebíveis" uma vez (§1), no sentido de **coleção num payload** — não de *batch job*. Não há menção a fila, mensageria, processamento assíncrono ou agendamento.

Mensageria foi considerada e **rejeitada**, por três razões:

**1. Brigaria com o §3.3.** O enunciado exige ACID e que nenhuma liquidação fique pela metade. Um broker introduz *dual-write*: gravar no banco e publicar na fila não são atômicos entre si. Recuperar a garantia exigiria *transactional outbox*, consumidores idempotentes e tratamento de entrega duplicada — todo esse aparato para voltar ao ponto onde **um único `@Transactional` já chega de graça**.

**2. O requisito de optimistic locking revela o modelo esperado.** `@Version` é controle de concorrência síncrono, de banco único. Se o enunciado supusesse fila, a resposta correta para concorrência seria particionamento por chave com consumidor único — não lock otimista.

**3. A simulação em tempo real (§4.1) é request/response por definição.**

**O que reverteria a decisão:** lote grande o bastante para estourar o timeout HTTP; necessidade de desacoplar ingestão de precificação sob pico de carga; ou sistemas externos precisando reagir à liquidação. Nenhuma dessas condições está no escopo atual.

> No frontend, a decisão irmã é **não adotar biblioteca de estado global** — as duas telas não compartilham estado mutável, e o estado do grid é *endereço*, não *client state*. A análise completa está no [README do frontend](apps/frontend/README.md#sem-biblioteca-de-estado-global--decisão-registrada).

---

## Modelo de dados

📊 **[Diagrama ER e dicionário de dados](docs/data-model.md)** · **[DDL consolidado](docs/schema.sql)**

Três decisões que moldam o esquema:

**Todo parâmetro de cálculo é gravado duas vezes** — como FK e como valor. A FK dá linhagem (qual registro foi usado); o valor congelado dá imutabilidade (quanto ele valia). Reprecificar amanhã com os parâmetros de hoje daria outro número, e a auditoria precisa reconstruir exatamente o que foi contratado.

**Cotações e taxas base são append-only**, versionadas por `vigencia_inicio`. Uma cotação nunca é sobrescrita: a precificação de ontem tem que continuar reproduzível.

**A trilha de auditoria é imutável por *trigger***, não por convenção. Trilha que pode ser alterada não é trilha. Em produção a resposta melhor seria `REVOKE UPDATE, DELETE` para o papel da aplicação; aqui a aplicação conecta como superusuário, então o trigger é o que efetivamente segura.

---

## API

Documentação interativa completa no **[Swagger UI](http://localhost:8081/swagger-ui.html)**, com os códigos de erro documentados — não só os de sucesso.

| Método | Rota | O que faz |
|---|---|---|
| `POST` | `/api/v1/simulacoes` | Precifica um lote **sem gravar nada**. Responde `200`, não `201` |
| `POST` | `/api/v1/operacoes` | Registra a cessão. `201` com `Location` |
| `GET` | `/api/v1/operacoes/{id}` | Operação com o lote e os parâmetros congelados |
| `POST` | `/api/v1/operacoes/{id}/liquidacao` | Liquida. `201` na primeira vez, `200` ao repetir a chave |
| `GET` | `/api/v1/relatorios/extrato-liquidacao` | Extrato paginado, filtros combináveis |
| `GET` `POST` | `/api/v1/cambio/taxas` | Cotação vigente, histórico e registro manual |
| `GET` | `/api/v1/cadastros/{tipos-recebivel,moedas}` | Dados de referência |

Erros seguem **RFC 7807** e trazem `correlationId`, que corresponde ao cabeçalho `X-Correlation-Id` e à linha de log do servidor — é o que liga uma reclamação de cliente ao log exato.

**Liquidação tem três defesas contra duplicidade**, deliberadamente redundantes: chave de idempotência, `@Version` e `UNIQUE (operacao_id)`. A redundância é o ponto — a checagem de status sozinha é *time-of-check to time-of-use*: entre ler `PENDENTE` e commitar, outra transação pode ter liquidado.

---

## Qualidade e observabilidade

**299 testes**, contra Postgres real. Não há banco embarcado: *optimistic locking* não se demonstra com mock.

| | |
|---|---|
| Precisão decimal | Valores esperados calculados **fora da aplicação** antes de virarem asserção |
| Concorrência | 8 threads reais; as 7 perdedoras recebem conflito de **concorrência**, não de estado — prova que a corrida aconteceu |
| Contrato OpenAPI | Teste de referências órfãs: contrato gerado quebra em silêncio e continua respondendo `200` |
| Performance | [`docs/performance.md`](docs/performance.md) — `EXPLAIN ANALYZE` antes e depois, com 100 mil liquidações |

**Logs em JSON** (formato ECS, nativo do Boot 4) com `correlationId` em toda linha da requisição. **Métricas de negócio** em `/actuator/prometheus`: operações registradas, liquidações por desfecho (`concluida` / `conflito` / `repeticao`), timer da precificação, estado do circuit breaker.

`conflito` e `repeticao` são contadores **separados** de propósito: conflito subindo é duas mesas no mesmo título — incidente. Repetição subindo é cliente com retry — o sistema funcionando. Somados, seriam indistinguíveis.

---

## Limitações declaradas

Preferi declarar a esconder.

| Limitação | Detalhe |
|---|---|
| **Sem autenticação** | Fora do escopo. Consequência real: `registradoPor` e `liquidadoPor` são campos **obrigatórios** nas requisições, não valores de sessão |
| **Calendário de feriados** | 78 feriados **nacionais**, de 2025 a 2030, com Páscoa calculada por Computus. Sem feriados estaduais ou municipais. Data fora da cobertura levanta erro em vez de assumir dia útil |
| **Provedor de cotação mockado** | A URL padrão aponta para host inexistente **de propósito** — torna o circuit breaker observável logo após subir |
| **Sem testes no frontend** | Não há test runner. `pnpm build` e `pnpm lint` são as únicas verificações automatizadas; debounce, guarda de resposta obsoleta e portão de validação estão verificados por leitura de código |
| **N+1 na precificação em lote** | Medido e quantificado: 2 queries por título, uma delas redundante. Declarado como critério **não atendido** em [`acceptance-criteria.md`](docs/acceptance-criteria.md#-d5--há-um-n1-e-ele-é-real) |
| **Paginação por offset** | `OFFSET` degrada linearmente em página muito profunda. Cursor mudaria o contrato da API e ficou fora do escopo |

📋 **[Critérios de aceite não-funcionais](docs/acceptance-criteria.md)** — 19 de 22 atendidos, com o número medido e o veredito de cada um.

---

## Documentação

| Documento | O que traz |
|---|---|
| [`AI_USAGE.md`](AI_USAGE.md) | Onde a IA ajudou, onde alucinou, e o que foi corrigido |
| [`docs/backlog.md`](docs/backlog.md) | Os 45 itens, com critérios de aceite e rastreabilidade ao enunciado |
| [`docs/acceptance-criteria.md`](docs/acceptance-criteria.md) | Critérios não-funcionais medidos |
| [`docs/performance.md`](docs/performance.md) | Análise com 100 mil liquidações |
| [`docs/data-model.md`](docs/data-model.md) · [`docs/schema.sql`](docs/schema.sql) | ER e DDL consolidado |
| [`docs/c4-context.md`](docs/c4-context.md) · [`docs/c4-container.md`](docs/c4-container.md) | Arquitetura em dois níveis |
| [`docs/git-workflow.md`](docs/git-workflow.md) | Convenções de branch, commit e PR |
| [`apps/api/README.md`](apps/api/README.md) · [`apps/frontend/README.md`](apps/frontend/README.md) | Detalhe por aplicação |

---

## Stack

**Backend** — Java 21 · Spring Boot 4.1 · PostgreSQL 17.5 · Flyway · springdoc-openapi 3.0 · Resilience4j · Micrometer
**Frontend** — Next.js 16 · React 19 · TypeScript · Tailwind CSS 4 · pnpm
**Infra** — Docker Compose · GitHub Actions · Spotless · Checkstyle

> Boot 4 e Next 16 são recentes o bastante para estarem à frente de boa parte do material disponível — starters renomeados, Jackson 3, `middleware` virando `proxy`. As armadilhas encontradas estão registradas no `AI_USAGE.md` e nos READMEs de cada app.
