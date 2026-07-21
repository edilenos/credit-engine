@AGENTS.md

# CLAUDE.md — `apps/frontend`

Next.js SPA of the SRM Credit Engine. This file covers the frontend only; repo-wide context (backlog, git conventions, scope, delivery state) lives in the root `CLAUDE.md`, and the backend has its own under `apps/api/`.

**The `@AGENTS.md` import above is binding, not decorative.** This Next.js has breaking changes against training data — read the relevant guide in `node_modules/next/dist/docs/` before writing code, and heed deprecation notices. Each hallucination caught is meant to be recorded in `AI_USAGE.md` (repo root, PBI-06); the spec grades that write-up.

## Commands

Run from this directory (`apps/frontend/`). pnpm is pinned via `packageManager` (`pnpm@10.29.2`); don't introduce npm/yarn lockfiles — they are gitignored deliberately.

| Task | Command |
|---|---|
| Dev server | `pnpm dev` (port 3000) |
| Production build | `pnpm build` |
| Lint | `pnpm lint` |

**No test runner is configured yet.**

## Versions are ahead of training data

- **Next.js 16** — see the `@AGENTS.md` rule above.
- **Tailwind CSS 4** — PostCSS plugin via `@tailwindcss/postcss`; there is **no `tailwind.config.js`**. Configuration is CSS-first.
- **ESLint flat config** — `eslint.config.mjs` using `defineConfig` with `eslint-config-next/core-web-vitals` and `eslint-config-next/typescript`. Not `.eslintrc`.

## The API is on 8081

Not the Boot default 8080. The base URL comes from `NEXT_PUBLIC_API_URL`, read in `services/config.ts` — never hardcode a host.

**Decided in PBI-25: direct call, no dev proxy.** `next.config.ts` stays empty. The browser talks to `http://localhost:8081` and CORS is configured on the API side (`ConfiguracaoDeCors`, origins from `APP_CORS_ORIGINS`, no wildcard). A Next rewrite would have hidden the cross-origin problem in dev and let it surface in production, where the two really are separate origins.

**There is no default for `NEXT_PUBLIC_API_URL`** — `urlDaApi()` throws `ConfiguracaoAusenteError` when it is missing. `.env.example` is committed (un-ignored explicitly in `.gitignore`); `.env.local` is not.

## Current state

Layers in place (PBI-25) and the **Painel do Operador** working at `/` (PBI-26): form → debounce → `useSimulacao` → API, with the rate composition rendered, verified against a running API.

Reference data for the selects comes from `GET /api/v1/cadastros/{tipos-recebivel,moedas}` — added in PBI-26 because no PBI had covered it, and hardcoding the list in the frontend would contradict the reason the spread lives in the DB.

> ⚠️ **There is still no test runner, and it is now a real gap.** `pnpm build` and `pnpm lint` are the only automated checks, and neither exercises behaviour. The debounce, the stale-response guard and the validation gate are all verified **only by reading the code** — no PBI in the backlog covers frontend tests. Money-input logic (`lib/moeda-digitada.ts`) was checked with a throwaway `node --experimental-strip-types` script, not a committed test.

## Architecture

The spec grades separation of presentation from business/state logic (§4.3). Structure established in PBI-25:

| Directory | Holds | Never |
|---|---|---|
| `components/` | Pure presentation | `fetch`, business rules |
| `features/` | Hooks and per-domain state (simulação, transações) | Direct DOM/markup concerns |
| `services/` | HTTP client, per-domain calls, uniform error and timeout handling | UI state |
| `types/` | Types mirroring the API contracts | — |
| `lib/` | Cross-cutting utilities (pt-BR formatting) | Anything domain-specific |

The dependency direction is one-way: `componente → hook (features/) → service → http-client → API`. A component never imports `http-client`.

### Decimals arrive as JSON numbers, not strings

Verified against the raw body: the API emits `"valorPresente":96284.58`. Jackson serializes `BigDecimal` as a JSON number, so `JSON.parse` yields a `double` and the backend's arbitrary precision ends at the HTTP boundary. Exact for display here (~15 significant digits against `NUMERIC(19,2)`), lossy only above ~1e13.

**The rule this imposes: the client does no money arithmetic.** Totals, deságios and conversions come computed from the API; this side only formats. Summing in the browser would reintroduce precisely the error the backend avoided.

Switching the API to string serialization would close the gap for real — it is a Jackson config change plus updating the PBI-24 contract tests. Not done, deliberately, and not silently: it is written down here and in `apps/frontend/README.md`.

### No global state library — this is a recorded decision

The spec asks for "Gerenciamento de Estado Global **(se necessário)**". The analysis (PBI-25): the two screens share no mutable state; the operator panel holds ephemeral form state; the grid's filter and page state belongs in the URL (a PBI-37 requirement, so it survives reload and stays shareable); what remains is server-state caching, not client state.

**Decision: no Redux/Zustand/Context store.** Adding one here would be the KISS violation the spec grades in §8.2. The decision and its rationale go in the README. **Reversal trigger:** a third screen sharing mutable state with the others reopens it — at which point rewrite the decision rather than quietly contradicting it.

## Screen requirements that are graded

**Painel do Operador (PBI-26)** — form for face value, due date, receivable type, title currency and settlement currency; simulation refreshes on every valid change without a page reload. Debounce so it is not one request per keystroke. **A stale in-flight response must not overwrite a newer result** — handle the request race. Show the composition (base rate, spread, quote applied), not just the total, so the operator can verify it.

**Grid de Transações (PBI-37)** — **server-side pagination is explicitly graded**; slicing an array client-side does not satisfy it. Changing page must issue a new request. Filters (period, originator, currency) are combinable and reset to page one. Filters and page live in the URL. Loading, empty and error states are all handled explicitly. Same stale-response rule as above.

## Presentation conventions

- Money and dates render in **pt-BR**, with the currency always indicated — formatting centralized, not repeated per component.
- Error messages surface in Portuguese, actionable, never a stacktrace or a raw status code.
- Decimals arrive from the API as exact values; do not round-trip them through `Number` where precision matters for display of large amounts.
- Every validation done here must also exist server-side — the spec frames input validation as a security requirement, and the client is not a trust boundary.
