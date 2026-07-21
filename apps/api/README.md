# API — SRM Credit Engine

Backend em Spring Boot 4.1 / Java 21 que precifica recebíveis, aplica câmbio e registra a cessão de forma auditável.

O README da raiz do repositório cobre o projeto inteiro; este documento trata só da API.

## Documentação interativa

Com a aplicação no ar:

| Recurso | URL |
|---|---|
| **Swagger UI** | **http://localhost:8081/swagger-ui.html** |
| Contrato OpenAPI (JSON) | http://localhost:8081/v3/api-docs |

A porta é **8081**, não a 8080 padrão do Spring Boot.

O contrato documenta os códigos de erro (`400`, `404`, `409`, `413`, `422`, `500`), não só os de sucesso. Eles são acrescentados a partir de um único ponto — `ConfiguracaoOpenApi` — porque nascem do `@RestControllerAdvice` global e não das assinaturas dos controllers, que é tudo o que o springdoc consegue inspecionar sozinho.

## Como rodar

O banco vem do Docker Compose, na raiz do repositório:

```bash
docker compose up -d db     # Postgres 17.5 na porta 55432
cd apps/api
./mvnw spring-boot:run      # http://localhost:8081
```

| Comando | O que faz |
|---|---|
| `./mvnw compile` | Compila |
| `./mvnw test` | Suíte completa (precisa do banco no ar) |
| `./mvnw test -Dtest=NomeDaClasse` | Uma classe |
| `./mvnw test -Dtest=NomeDaClasse#metodo` | Um teste |
| `./mvnw spring-boot:run` | Sobe a aplicação |

> **A suíte roda contra um Postgres real**, não embarcado. Sem `docker compose up -d db`, ela falha no `flywayInitializer` com erro de conexão.

### Configuração

Credenciais locais ficam em `application-local.yaml` (gitignorado), carregado por `spring.config.import`. Em CI e Docker as variáveis de ambiente assumem: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

| Variável | Padrão | Para quê |
|---|---|---|
| `SERVER_PORT` | `8081` | Porta da API |
| `APP_CORS_ORIGINS` | `http://localhost:3000` | Origens do SPA. Lista explícita, **nunca** `*` |
| `COTACAO_URL_BASE` | host inexistente | Provedor de cotação. O padrão inválido é proposital: torna o circuit breaker observável logo após subir |

## Observabilidade

Com a aplicação no ar:

| Recurso | URL |
|---|---|
| Métricas Prometheus | http://localhost:8081/actuator/prometheus |
| Health | http://localhost:8081/actuator/health |

A exposição é **lista explícita**, nunca `*` — apenas `health`, `info`, `prometheus` e `metrics`. Com curinga entrariam `env` e `configprops`, que despejam a configuração efetiva incluindo valores vindos de variáveis de ambiente, e `heapdump`, que entrega a memória do processo.

### Métricas de negócio

| Métrica | Responde a |
|---|---|
| `creditengine_operacoes_registradas_total` | volume de cessões |
| `creditengine_liquidacoes_total{resultado}` | `concluida`, `conflito`, `repeticao` |
| `creditengine_precificacao_duracao_seconds` | latência do cálculo, com p50/p95/p99 |
| `resilience4j_circuitbreaker_state` | saúde do provedor de cotação |

**`conflito` e `repeticao` são contadores separados de propósito.** Conflito subindo significa duas mesas operando o mesmo título — incidente. Repetição subindo significa cliente com retry ativo — o sistema funcionando como desenhado. Somados, seriam indistinguíveis.

Nenhuma métrica leva id, CNPJ ou chave de idempotência em tag: cada valor distinto cria uma série temporal, e cardinalidade ilimitada derruba o Prometheus além de publicar dado de cliente numa base menos protegida que o banco.

O timer mede só a precificação efetiva. Lote recusado não entra — recusa medida como cálculo rápido puxaria o percentil para baixo e mascararia lentidão real.

### Logs

JSON no formato **ECS**, com o `correlationId` em toda linha da requisição, vindo do `MDC`. `LOG_FORMATO` aceita `ecs`, `gelf` ou `logstash`; `LOG_NIVEL` e `LOG_NIVEL_APP` ajustam verbosidade.

O formato é **nativo do Boot 4** — `logstash-logback-encoder` seria dependência morta.

Logs não carregam documento de cedente ou sacado, chave de idempotência, nem valores monetários. Quem guarda isso é a trilha de auditoria, no banco, com o controle de acesso certo.

> Em teste, `/actuator/prometheus` só responde com **`@AutoConfigureMetrics`** — o Boot desliga a exportação de métricas por padrão nos testes. No Boot 4 essa anotação mudou de nome e de pacote: era `@AutoConfigureObservability`.

## Arquitetura

Três camadas, com uma exceção deliberada.

```
aplicacao/    controllers, DTOs, tratamento de exceção, filtros
negocio/      regras, Strategies, serviços transacionais
persistencia/ entidades JPA e repositórios
```

O fluxo é `aplicacao → negocio → persistencia`. A camada de aplicação **não** alcança a persistência direto — com uma exceção, abaixo.

### `relatorio/` — a exceção de duas camadas, deliberada e isolada

O §3.6 do enunciado autoriza a rota de relatório a pular a camada de negócio, e o §3.5 trata como diferencial usar SQL otimizado no lugar do ORM. As duas decisões precisam ser **legíveis**, então o relatório inteiro — controller, consulta e projeções — mora em `relatorio/`, fisicamente separado de `negocio/` e `persistencia/`.

```
relatorio/
  ExtratoDeLiquidacaoController  →  ConsultaDeExtrato  →  JdbcClient
```

Não há serviço no meio porque **não há regra a aplicar**: o extrato lê, projeta e pagina. Uma camada de repasse existiria no diagrama e não no comportamento. Há teste verificando que o controller depende só da consulta — se um serviço de domínio entrar aqui um dia, o teste falha e a decisão volta a ser discutida em vez de erodir em silêncio.

**Por que não JPA.** A consulta cruza quatro tabelas e não corresponde a agregado nenhum. Com JPA seriam duas saídas ruins: uma entidade que existe só para o relatório, ou carregar `Liquidacao` e navegar associações LAZY linha a linha — o N+1 justamente sobre a consulta que o enunciado descreve como "grandes volumes".

**Segurança da ordenação.** Filtros entram como parâmetro nomeado. A coluna de ordenação **não pode**: identificador não se vincula em SQL, `ORDER BY ?` não existe. Sobraria concatenar entrada de usuário — então ela vem de um enum, e valor fora da lista responde `422` em vez de cair num padrão silencioso.

```
GET /api/v1/relatorios/extrato-liquidacao?ordenarPor=x;%20DROP%20TABLE%20liquidacao
→ 422 "Ordenacao invalida. Valores aceitos: LIQUIDADO_EM, VALOR_LIQUIDADO, CEDENTE, OPERACAO"
```

**Paginação.** `tamanho` é limitado a 100 — pedir `1000000` devolve 100, não a tabela inteira. `totalDeItens` é o total do filtro, não da página, e o `ORDER BY` desempata por `l.id` para que liquidações no mesmo instante não troquem de posição entre páginas.

### Duas famílias de Strategy

| Família | Resolve | Implementações |
|---|---|---|
| Spread por tipo de recebível | prêmio de risco | Duplicata 1,5% a.m., Cheque 2,5% a.m. |
| Convenção de contagem | prazo → expoente | `ACT_30`, `COMERCIAL_30_360`, `ACT_360`, `ACT_365`, `TAXA_DIARIA`, `BUS_252` |

As duas resolvem por `List<…>` injetada, sem `if` nem `switch`. Tipo desconhecido levanta erro de domínio em vez de assumir zero — spread nulo produz preço plausível e errado, que é o pior tipo de falha.

> ⚠️ **A periodicidade da taxa precisa bater com a unidade da convenção.** Somar 1,5% a.m. com um expoente anual não lança exceção: produz preço errado por ordem de grandeza. Toda taxa carrega sua `Periodicidade`, toda convenção declara o que produz, e o motor valida o par antes de calcular.

### Precisão decimal

`BigDecimal` fim a fim, `NUMERIC` no banco, `HALF_EVEN`, escala explícita em cada fronteira. Política centralizada em `PrecisaoDecimal`.

Expoente fracionário é o caso normal, não a exceção — quatro das seis convenções o produzem. `BigDecimal.pow()` só aceita `int`, então a potenciação usa `ch.obermuhlner:big-math`.

### Liquidação: três defesas contra duplicidade

Chave de idempotência, `@Version` em `Operacao`, e `UNIQUE (operacao_id)`. A redundância é o ponto: a checagem de status sozinha é *time-of-check to time-of-use*, e a constraint cobre o caso de o lock sumir num refactor. Provado com oito threads concorrentes contra o banco real.

## Versões à frente do material de treino

Verifique contra o POM e os jars antes de escrever imports:

- **Starters por feature** — `spring-boot-starter-webmvc`, não `-web`. Suporte de teste dividido por starter, não um `spring-boot-starter-test`.
- **Jackson 3** — `tools.jackson.databind`, não `com.fasterxml`. O springdoc traz Jackson 2 junto, para uso interno do swagger-core; pacotes diferentes, sem colisão.
- **`springdoc-openapi` linha 3.0.x** — a 2.8.x é a do Boot 3, resolve e compila, e falha na autoconfiguração.
- **`resilience4j-spring-boot4`** — não `-spring-boot3`, mesma armadilha.
- **`spring-boot-starter-aspectj`** — não `-aop`; sem ele as anotações do Resilience4j viram no-op silencioso.
- **`TestRestTemplate` saiu** para o módulo `spring-boot-resttestclient` e mudou de pacote.
