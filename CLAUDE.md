# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Take-home technical challenge for **SRM Asset**: the "SRM Credit Engine", a multi-currency credit-assignment (cessão de crédito) platform for a FIDC. The fund buys receivables (duplicatas, cheques, contratos) from originators at a discount; the system prices them, applies FX when the operation is cross-currency, and records the settlement auditably.

Spec: `docs/README_case_dev_srm.md`. It is in Portuguese, and deliverables (README, PR descriptions, commit bodies, `docs/`) should be too unless the user says otherwise.

**Target level is settled: 🔴 Sênior**, cumulative (Júnior + Pleno + Sênior). See "Scope" below — the Especialista/Staff items are deliberately excluded, and that exclusion is documented rather than silent.

## Context files: one fact lives in one place

Three `CLAUDE.md` files, split by what they govern. **This file points; it does not repeat.** When adding context, put it in the narrowest file that covers it — duplicating a fact here and in an app file guarantees they drift.

| File | Governs |
|---|---|
| **this file** | Project identity, the backlog, layout, git state and conventions, scope, non-code deliverables |
| **`apps/api/CLAUDE.md`** | Maven/JDK commands, Boot 4.1 pitfalls, datasource and Flyway contract, `BigDecimal` traps, the two Strategy families, three-layer architecture and the report bypass |
| **`apps/frontend/CLAUDE.md`** | pnpm commands, Next 16 / Tailwind 4 / flat-ESLint pitfalls, layer structure, the no-global-state decision, graded screen requirements |

`apps/frontend/CLAUDE.md` opens with `@AGENTS.md`, a binding import from the Next scaffolding — keep it.

## `docs/backlog.md` is the plan of record — read it before starting work

The spec has been decomposed into an executable backlog: **45 PBIs across 9 stages**, each with a story, acceptance criteria, MoSCoW priority, an assigned branch name, and suggested commit messages. It also carries the domain glossary, the preliminary data model, non-functional acceptance criteria, risks, and the DoR/DoD.

When picking up a task, find its PBI first — the branch name and commit messages are already chosen there, and the acceptance criteria are what "done" means. Do not invent a parallel plan.

**The backlog is a source that drains into `docs/` as work proceeds.** Several sections are marked *fonte e destino*: a later PBI extracts them into their own file, after which **the backlog copy freezes and the extracted file is authoritative**. Editing the frozen backlog section instead of the extracted file is the failure mode to avoid.

| Backlog section | Extracted by | Becomes authoritative at |
|---|---|---|
| §4 Modelo de domínio | PBI-07 | `docs/data-model.md` |
| §8 Critérios não-funcionais | PBI-42 | `docs/acceptance-criteria.md` |
| §10 Convenções de Git | PBI-04 | `docs/git-workflow.md` |

Other planned docs: `docs/schema.sql` (PBI-12), `docs/c4-context.md` + `docs/c4-container.md` (PBI-41), `docs/performance.md` (PBI-36).

### Scope decision — settled: full Sênior, nothing cut

**Scenario A: all 45 PBIs, ~78.5 h ≈ 9.8 working days**, against a nominal 3–4 days (24–32 h). Decided before the first commit, invoking the spec's §9.2 clause that the deadline is *"ajustável conforme complexidade entregue"*. Backlog §6.1 carries the arithmetic.

Why the choice was effectively binary: the MoSCoW pass found **40 of 45 PBIs are Must**, and the five cuttable ones (PBI-16, 19, 20, 30, 36) total **9 h — 11% of the work**. Cutting everything cuttable saves barely a day. Dropping the Sênior-only items too still leaves ~62 h, nearly 8 days. The base functional scope of §3 and §4 is what costs; the tier extras are a small fraction.

> Effort figures are a rollup of each PBI's `Tamanho` field — never a separately typed number. Backlog §6.1 carries the one-line `awk` that recomputes them; run it after adding or resizing a PBI rather than editing the stage table by hand. Three separate arithmetic drifts have already been caught this way.

**Consequences for day-to-day work:**

- **Nothing is cut by default.** The five non-Must PBIs are in.
- The cut order (PBI-20 → 36 → 16 → 30 → 19) is **contingency only**, if something overruns during execution. Never cut server-side pagination, the Strategy tests, optimistic locking, Docker Compose or CI.
- **The README must open by declaring scope and schedule** (PBI-43): which scenario, what it cost, why. Nearly ten days against a nominal 3–4 is 2.5× — the clause authorizes it, but authorization is not the same as a favourable reading. Stating it up front beats letting the reviewer do the arithmetic unprompted.

## Layout

Git root is this directory. Both apps live under `apps/` in a **single git repository** — there is no nested `.git`, no submodule, and no root-level build file tying them together:

```
credit-engine/                    <- git root; CLAUDE.md is the only file here for now
  apps/
    api/                          <- Spring Boot 4.1 / Java 21; see its own CLAUDE.md
    frontend/                     <- Next.js 16 / React 19 / pnpm; see its own CLAUDE.md
  docs/
    README_case_dev_srm.md        <- the challenge spec (input, not a deliverable)
    backlog.md                    <- the plan of record
  .github/modernize/java-upgrade/ <- tooling scaffolding; ignore it
```

The root is deliberately near-empty: `README.md` (PBI-43) will be the project's own front page, so the challenge spec lives in `docs/` to keep that unambiguous.

`.github/modernize/java-upgrade/` hides itself via a nested `.gitignore` containing `**/*`. `.github/` itself is **not** ignored — the CI workflow the spec grades belongs at `.github/workflows/` and will be tracked normally.

`.gitignore` lives at `apps/api/.gitignore` and `apps/frontend/.gitignore`, scoped to their own subtrees. **There is still no root `.gitignore`** (PBI-01 adds it) — anything added at the root or in a new sibling directory is untracked-but-not-ignored and will get swept up by `git add -A`.

> A second, older `CLAUDE.md` sits one level up at `Teste SRM Asset/CLAUDE.md`, **outside this git repo**. It predates the scaffolding and asserts there is no source code or stack yet. It is stale; trust this file.

## Current state

Scaffolding on both sides plus a working API datasource. Effectively all of Stage 0 onward is still open. Per-app detail is in the two app files; repo-wide gaps:

- No Docker Compose anywhere. No CI workflow.
- No `README.md` — the original one-line stub was deleted; PBI-43 writes it from scratch.
- No `AI_USAGE.md`.

### Git state

`HEAD` (`d85d829`) contains **only** the original one-line `README.md` stub. Everything else — both apps, the backlog, these context files — is staged in the index at the correct `apps/…` paths but not yet committed. The earlier mis-staging at old root-level paths has been resolved.

Nothing has been committed for real yet, so **PBI-01 must land before the first commit**.

### ⚠️ Blocking issue — a real credential is staged

`apps/api/src/main/resources/application.yaml:16` hardcodes the real local Postgres password as the *default* of `${DB_PASSWORD:…}`, duplicating the exact value the gitignored `application-local.yaml` exists to protect. The spec requires a **public** repository, so anything committed is published.

It is staged but not committed, so nothing has leaked. Before any commit: revert to an empty/placeholder default and rotate the password. This is PBI-01 and risk R5. Mechanics of the config layering are in `apps/api/CLAUDE.md`.

This incident is the **first required entry in `AI_USAGE.md`** (PBI-06): AI-assisted code wrote the secret, and a project context file compounded it by asserting the file held no secrets. The spec explicitly asks for cases where AI produced insecure code and how it was corrected — this is one, and writing it up honestly is graded.

> Once PBI-01 lands, replace this section with a one-line pointer to the `AI_USAGE.md` entry. This file ships in a public repo; a standing description of where the vulnerability was serves no purpose after the fix, and the honest narrative belongs in the deliverable the spec asked for.

## Both stacks are ahead of training data

Spring Boot 4.1 renamed starters, repackaged autoconfiguration and moved to Jackson 3; Next.js 16, Tailwind 4 and flat ESLint config likewise differ from what tutorials show. This is risk R3. Specifics live in each app's `CLAUDE.md` — **read the relevant one before writing code in that subtree**, and verify against the actual POM, lockfile and vendored docs rather than recalling an API.

Each hallucination caught is meant to become an `AI_USAGE.md` entry. The spec grades that write-up, so these are material, not annoyances.

## Scope

**In scope** — Júnior + Pleno + Sênior, cumulative. The Sênior tier adds: git hooks, semver tag, interactive rebase, C4 L1/L2 diagrams, structured logs and metrics, CI running tests *and* lint, retry/circuit breaker on the external call, optimistic locking with a concurrency test.

**Deliberately out of scope** (🟣 Especialista/Staff), excluded on purpose and declared in the backlog rather than silently omitted: formal ADRs, the 1M transactions/minute design, the EDA proposal, IaC, the staged `git revert`/`cherry-pick` crisis, and the formal branching-strategy justification. Don't pull these in without the user asking.

**Cut line if time runs short**, in order: PBI-20 (`BUS_252` and the holiday calendar — the only expensive convention; cutting it leaves the other five intact), then PBI-36's bulk data generation, then PBI-16 and PBI-30, then PBI-19. **Never** cut server-side pagination, the Strategy tests, optimistic locking, Docker Compose or CI — all explicitly graded.

## Git conventions (graded)

The spec evaluates git history as a first-class deliverable. Canonical version is backlog §10 until PBI-04 extracts it to `docs/git-workflow.md`.

- Never commit directly to `master` — branch per PBI. The backlog assigns the branch name; use it.
- Branch naming: `<tipo>/<descricao-em-kebab-case>` in **Portuguese** (`feature/calculo-desagio`), types `feature|fix|chore|docs|ci|test|perf|refactor`.
- **Conventional Commits are mandatory**, in Portuguese, **unaccented** in the subject: `feat(precificacao): adiciona strategy de spread por tipo de recebivel`.
- Filenames in **English**, kebab-case, pure ASCII — accented paths cause encoding friction with Git on Windows. Contents stay Portuguese. The mix is a deliberate decision, documented so it doesn't read as sloppiness.
- Atomic commits, each compiling on its own. "finalizado", "ajustes", "wip" are explicitly named failure modes.
- One PR per PBI (or per cohesive group of small ones), template filled in, merged by squash or rebase — **never a convenience merge commit**. History stays linear. Green CI is a merge prerequisite.
- Final delivery gets an **annotated** `v1.0.0` tag (PBI-45).

## Required non-code deliverables

- `AI_USAGE.md` — mandated by the spec. Must cover strategic prompts, **places where AI hallucinated or produced insecure code and how it was corrected**, and a critical take on where AI helped vs. hindered. Append as work happens; reconstructing it at the end shows. Two entries are already owed: the hardcoded password, and Boot 4 / Next 16 API hallucinations as they occur.
- ER diagram + consolidated DDL script in `/docs`.
- `README.md` — does not exist yet. Setup, architecture, *justified* decisions, and the scope/schedule declaration from §6.1.
- Non-functional acceptance criteria across usability, security, performance, scalability, each with a stated verification method — performance ones need measured numbers, not adjectives.
