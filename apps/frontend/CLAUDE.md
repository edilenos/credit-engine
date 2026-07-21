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

Not the Boot default 8080. The base URL comes from `NEXT_PUBLIC_API_URL` — never hardcode a host. `next.config.ts` is currently empty; no dev proxy is configured yet (PBI-25 decides proxy vs. direct call).

## Current state

Untouched `create-next-app` boilerplate — `app/page.tsx` still renders the Next.js splash. No API client, no layer structure yet. PBI-25 clears the boilerplate and establishes the architecture; PBI-26 builds the first screen.

## Architecture

The spec grades separation of presentation from business/state logic (§4.3). Structure established in PBI-25:

| Directory | Holds | Never |
|---|---|---|
| `components/` | Pure presentation | `fetch`, business rules |
| `features/` | Hooks and per-domain state (simulação, transações) | Direct DOM/markup concerns |
| `services/` | HTTP client, DTO mapping, uniform error and timeout handling | UI state |
| `types/` | Types mirroring the API contracts | — |

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
