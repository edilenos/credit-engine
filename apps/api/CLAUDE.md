# CLAUDE.md — `apps/api`

Spring Boot backend of the SRM Credit Engine. This file covers the API only; the repo-wide context (backlog, git conventions, scope, delivery state) lives in the root `CLAUDE.md`, and the frontend has its own under `apps/frontend/`.

## Commands

Run from this directory (`apps/api/`).

| Task | Command |
|---|---|
| Compile | `./mvnw compile` |
| Full test suite | `./mvnw test` |
| Single test class | `./mvnw test -Dtest=CreditEngineApplicationTests` |
| Single test method | `./mvnw test -Dtest=CreditEngineApplicationTests#contextLoads` |
| Package jar | `./mvnw package` |
| Run app | `./mvnw spring-boot:run` |

JDK 21 is required and `JAVA_HOME` on this machine already points at it (`C:\dev\java\jdk-21.0.11+10`) — no prefix needed. If a build fails with `release version 21 not supported`, `JAVA_HOME` has regressed to JDK 17; check `java -version` and prefix that invocation with `JAVA_HOME="C:/dev/java/jdk-21.0.11+10"` rather than editing the POM.

**The app listens on 8081, not the Boot default 8080** — `server.port` is `${SERVER_PORT:8081}`, so override via the `SERVER_PORT` env var rather than editing the YAML. Anything talking to the API (frontend client, Swagger URLs, compose port mappings, smoke tests) must target 8081.

**`./mvnw verify` is the command that matters** — it runs Spotless, Checkstyle and the full suite, which is exactly what CI and the pre-push hook execute. That identity is deliberate: a local command that differs from the pipeline is how people learn to ignore the local result. `./mvnw spotless:apply` fixes formatting.

The lint config is deliberately lean, and the reasoning is in `pom.xml` and `checkstyle.xml`: `google-java-format` was **measured and rejected** — it would have rewritten 147 files and 19k lines.

## Code style
- Javadoc only on public APIs/services.

## Current state

**Complete.** Pricing with both Strategy families, FX applied last, batch cession, settlement with three redundant defenses, audit trail, native-SQL report, global exception handling, OpenAPI, structured logs and business metrics. **299 tests**, five Flyway migrations.

Two known defects are open and declared — see the root `CLAUDE.md`. The one that touches this subtree: **`GET /api/v1/cambio/taxas` returns `500`**, because `CotacaoResponse.de()` walks a LAZY `Moeda` after the transaction closed. Its tests pass because they are `@Transactional`, which keeps the session open for the whole test.

`./mvnw test` runs **against a real Postgres 17.5**, not an embedded database. The database comes from Docker Compose (PBI-03, done): run `docker compose up -d db` from the repo root first, or the suite fails at `flywayInitializer` with a connection error.

> ⚠️ **The host port is 55432, not 5433.** On this machine an SSH tunnel inside the Ubuntu WSL distro binds `127.0.0.1:5433` **and** `:5434`, and Windows resolves `localhost` to the more specific bind — so a container published on `0.0.0.0:5433` is shadowed by the tunnel and the JVM silently talks to the wrong database. It manifests as `FATAL: password authentication failed`, which reads like a credentials bug and is not one. If the suite ever fails that way again, check `netstat -ano | grep ":<port>"` for two listeners before touching any password.

## Spring Boot 4.1.0 — do not apply Boot 3 habits

This is ahead of most training data, which still shows the Boot 3 shape. Verify against the actual POM and jars before writing imports or adding dependencies. Each hallucination caught here is meant to be recorded in `AI_USAGE.md` (repo root, PBI-06) — the spec grades that write-up.

- Starters are feature-scoped: `spring-boot-starter-webmvc`, **not** `spring-boot-starter-web`.
- Test support is split per starter rather than one `spring-boot-starter-test`: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-flyway-test`, `-validation-test`, `-actuator-test`. All five are already on the POM.
- Autoconfiguration moved into per-feature packages — `org.springframework.boot.flyway.autoconfigure`, `org.springframework.boot.jdbc.autoconfigure`, `org.springframework.boot.webmvc.error` (the last matters for the global exception handler in PBI-31).
- Jackson 3 is in use: `tools.jackson.databind`, not `com.fasterxml.jackson.databind`.

**Two third-party libraries were the open compatibility risks (R1, R2), and both are now settled by evidence** — no fallback was needed. What matters is *which line* of each:

| Library | Use | The trap |
|---|---|---|
| `springdoc-openapi` **3.0.x** | OpenAPI/Swagger | The 2.8.x line is Boot 3's. It resolves and compiles, then fails at autoconfiguration |
| `resilience4j-spring-boot4` | Retry + circuit breaker | `-spring-boot3` exists at the same version. Same failure mode |

Both verified against a running server, not assumed. `springdoc` 3.0.3 was built against Boot 4.0.5 and works on 4.1.0.

## Configuration and schema

`application.yaml` reads `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` from the environment. The real local password belongs **only** in `application-local.yaml` (this directory), pulled in via `spring.config.import: optional:file:./application-local.yaml`. That file is gitignored and must stay that way — the spec requires a public repository.

Imported config outranks the importing document in Spring Boot, so the local file wins over the defaults; the import is `optional:`, so CI and Docker fall back to environment variables with the file absent.

> The default is empty (`${DB_PASSWORD:}`) and must stay that way. An earlier version hardcoded the real password here; the incident is written up in `AI_USAGE.md` §3.1, and the pre-commit hook now scans every diff for credential patterns.

Boot 4.1.0 has **no native `.env` support** (verified against the jar — there is no dotenv property source), which is why this uses `spring.config.import`.

Flyway owns the schema against `classpath:db/migration` — `V1` cadastros, `V2` núcleo transacional, `V3` seed. `ddl-auto` is `validate` — Hibernate only checks; it never generates. A mapping that disagrees with a migration fails startup, which is the intended behavior.

> **Adding a migration? Regenerate `docs/schema.sql`.** It is a `pg_dump` of the migrated schema, kept because §7.2 of the spec asks for consolidated DDL in `/docs`. It does not regenerate itself, so it silently goes stale. The exact command is in that file's header; run `pg_dump` **inside the container** so its version matches the server's.

## Domain rules that constrain this code

Formula: `Valor Presente = Valor Face / (1 + Taxa Base + Spread)^Expoente`. Full glossary in `docs/backlog.md` §2.

### Two Strategy families, not one

The spec names Strategy explicitly and grades it. There are **two** independent families, and the pricing engine consumes both:

1. **Spread by receivable type** (PBI-18) — Duplicata Mercantil 1.5% a.m., Cheque Pré-datado 2.5% a.m.
2. **Day-count convention** (PBI-19/20) — `ACT_30` (default), `COMERCIAL_30_360`, `ACT_360`, `ACT_365`, `TAXA_DIARIA`, `BUS_252`.

Both resolve polymorphically from an injected `List<…>`. **No `if`/`switch` on type** in the resolver, and an unknown type raises a domain error rather than silently defaulting to zero. Adding a type or a convention must mean adding one class and touching nothing else.

The numeric spread lives in the DB (`tipo_recebivel.spread`) so it can change without a deploy; the *rule* for deriving it lives in the Strategy. That split is what keeps the pattern from being a `Map` in disguise.

### ⚠️ Rate periodicity must match the convention's output unit

`(1 + taxa)^expoente` is only valid when the exponent is in the same capitalization unit as the rate. Combining a **1.5% a.m.** spread with a **base-252 (annual)** convention throws no exception — it produces a plausible price wrong by orders of magnitude. Silent, so it is the system's most dangerous failure mode (risk R7).

The design that prevents it: every rate carries its own `periodicidade` (`DIARIA`/`MENSAL`/`ANUAL`), every convention declares the unit it produces, and the engine **validates the pair before calculating**, rejecting a mismatch as a business error. `ACT_30` is the default precisely because it matches the spec's monthly-quoted spreads.

### Decimal precision

**Never use floating point for money.** It is a named evaluation criterion: `BigDecimal` end to end, `NUMERIC`/`DECIMAL` columns (money `NUMERIC(19,2)`, rates `NUMERIC(9,6)`, FX `NUMERIC(19,6)`), explicit scale and `RoundingMode` at every boundary. Policy is centralized in one place (PBI-17): scale 2 for money, 6 for rates, `HALF_EVEN`, one shared `MathContext`, rounding only at output boundaries.

Two traps live in the same expression:

- `BigDecimal.divide(x)` **without** scale and `RoundingMode` throws `ArithmeticException` on a repeating decimal — and `VF / (1+i)^n` produces them routinely. Always the three-arg overload.
- `pow(int)` **without** a `MathContext` returns an exact result with exploding scale (`1.025^24` has 72 places). Always pass the `MathContext`.

**Fractional exponents:** `BigDecimal.pow()` accepts only `int`, but four of the six conventions produce fractional exponents — the normal case, not the exception. The chosen answer is `ch.obermuhlner:big-math` (`BigDecimalMath.pow(BigDecimal, BigDecimal, MathContext)`); verify the current coordinates on Maven Central rather than trusting a suggested version. Do not fall back to `Math.pow`/`double` — indefensible under a criterion literally named decimal precision. Do not round the term to whole months either: that is a price change of roughly 1% of face value, not a simplification.

### FX, persistence and settlement

**FX is applied last.** For cross-currency operations, convert only after the present value is computed — ordering changes the rounding, and a test must demonstrate that converting first yields a different number. The applied quote is written onto the operation for audit.

**Quotes and base rates are append-only**, versioned by `vigencia_inicio`. A quote is never overwritten: yesterday's pricing must stay reproducible.

**Every calculation parameter is stored twice — as FK and as value.** The FK gives lineage (which record was used), the frozen value gives immutability (what it was worth). Recalculating later with current parameters would give a different answer; audit needs the number as of the operation.

**Settlements are ACID or nothing**, with three deliberately redundant defenses (PBI-28): `@Version` optimistic locking on `Operacao`, a `UNIQUE (operacao_id)` constraint on `liquidacao`, and an idempotency key. Concurrency is proven by a real multi-threaded test against a real database (PBI-29) — optimistic locking cannot be demonstrated with a mock.

**Batch processing is synchronous and transactional by decision.** "Lote" in the spec means a collection in one payload, not a batch job. Messaging would fight the ACID requirement (dual-write), and the optimistic-locking requirement itself implies a synchronous single-database model. The rejection is argued in the README (PBI-43) with the conditions that would reverse it.

## Architecture

**Three layers** — application / business / persistence — with one deliberate exception: **reporting routes skip the business layer** and go application → persistence directly.

The "Extrato de Liquidação" report (PBI-35) is where two graded points converge, and both must be *visible in the code*: it uses `JdbcClient` with hand-written parameterized SQL rather than JPQL/Criteria, and it lives in its own package physically separate from the JPA domain repositories, so the two-layer exception is legible to a reviewer. Sort parameters are whitelisted against a column allowlist; no endpoint returns an unpaginated collection.

`open-in-view` is `false` — no accidental lazy loading in the web layer. `@Transactional` belongs on the domain service, never on the controller.

Mandated and easy to forget, since nothing in the current scaffolding hints at them:

- **OpenAPI/Swagger documentation**, with correct HTTP verbs and semantic status codes — error codes documented, not just success.
- **Global exception handling** via `@RestControllerAdvice` + `ProblemDetail` (RFC 7807), with a correlation id on every error — not per-endpoint try/catch. No response leaks a stacktrace, SQL, or table name.
- **Robust input validation**, framed by the spec as a *security* requirement: everything validated client-side must be validated server-side too, plus a max batch size.
- **Unit tests covering the pricing strategies** specifically, with expected values computed by hand and shown in the test — a test that merely echoes the implementation proves nothing.
- **An exchange-rate endpoint** for manual/mocked updates; FX rates are stored data, not constants.
- **Structured JSON logging + business metrics** on `/actuator/prometheus` (settlements by outcome, pricing timer, breaker trips). Business metrics read better than CPU graphs here. Expose only the actuator endpoints actually needed — never `*`.
