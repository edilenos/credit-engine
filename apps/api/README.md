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

## Arquitetura

Três camadas, com uma exceção deliberada.

```
aplicacao/    controllers, DTOs, tratamento de exceção, filtros
negocio/      regras, Strategies, serviços transacionais
persistencia/ entidades JPA e repositórios
```

O fluxo é `aplicacao → negocio → persistencia`. A camada de aplicação **não** alcança a persistência direto — exceto nas rotas de relatório, exceção que o §3.6 do enunciado autoriza e que o Extrato de Liquidação (PBI-35) vai exercer com SQL nativo em pacote próprio.

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
