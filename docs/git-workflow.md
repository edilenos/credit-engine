# Fluxo de trabalho com Git

Convenções de versionamento deste repositório. **Este é o documento definitivo** — extraído da seção 10 de [`backlog.md`](backlog.md) pelo PBI-04; aquela seção ficou congelada como registro da decisão inicial.

O enunciado avalia o uso do Git como entregável de primeira classe (§6 e §8.3), então estas regras não são estilo: são parte da entrega.

---

## 1. A regra que mais importa

> **Todo merge em `master` é feito por *rebase* ou *squash*. Merge commit é proibido.**

O histórico de `master` é linear, sem exceção. O §6 🟡 do enunciado cita "commits de merge desnecessários ou poluídos" como falha explícita.

Entre as duas opções permitidas:

- **Rebase** quando o PR tem commits atômicos que contam uma história — preserva a granularidade.
- **Squash** quando o PR é um único assunto ou tem commits de correção que não agregam ao histórico.

## 2. Fluxo escolhido: GitHub Flow

`master` sempre integrável; branch curta por unidade de trabalho; PR para integrar; sem branches de release ou develop.

Cabe ao projeto porque a entrega é única, o time é de uma pessoa e não há necessidade de manter versões em paralelo — que é o que justificaria Git Flow. A justificativa formal de estratégia de branching é entregável do nível 🟣 Especialista e está declaradamente fora de escopo (ver backlog §3.2).

## 3. Convenção de idioma

Decisão deliberada, registrada para que a mistura não pareça descuido:

| Artefato | Idioma | Motivo |
|---|---|---|
| Conteúdo dos documentos | Português | Enunciado em português, avaliação brasileira |
| Nomes de arquivo | **Inglês**, kebab-case, ASCII puro | Convenção dominante em repositório público; evita acento em path, que causa atrito de encoding no Git em Windows |
| Nomes de branch | **Português** | O próprio enunciado exemplifica assim (`feature/calculo-desagio`, §6 🟢) |
| Mensagens de commit | Português, **sem acento no assunto** | Corpo pode ter acento; o assunto fica ASCII por compatibilidade de terminal e de ferramenta |

## 4. Branches

Formato: `<tipo>/<descricao-em-kebab-case>`

| Tipo | Uso | Exemplo deste projeto |
|---|---|---|
| `feature` | Funcionalidade nova | `feature/calculo-desagio` |
| `fix` | Correção de defeito | `fix/arredondamento-cambio` |
| `chore` | Infraestrutura, build, configuração | `chore/saneamento-de-segredos` |
| `docs` | Documentação | `docs/convencoes-de-git` |
| `ci` | Pipeline | `ci/pipeline-inicial` |
| `test` | Testes sem mudança de comportamento | `test/concorrencia-liquidacao` |
| `perf` | Performance | `perf/indices-extrato` |
| `refactor` | Refatoração sem mudança de comportamento | `refactor/resolver-de-strategy` |

**Nunca commitar direto no `master`.** O backlog atribui o nome da branch a cada PBI — use o que está lá em vez de inventar.

## 5. Commits

Conventional Commits obrigatórios: `<tipo>(<escopo opcional>): <descrição no imperativo>`

```
feat(precificacao): adiciona strategy de spread por tipo de recebivel
fix(cambio): corrige arredondamento na conversao cross currency
test(liquidacao): cobre concorrencia com optimistic locking
docs(readme): documenta decisoes de precisao decimal
chore: adiciona docker compose com postgres para desenvolvimento
```

**Regras:**

- Um commit resolve uma coisa. Cada commit compila e passa nos testes isoladamente.
- Assunto no imperativo, minúsculo, sem ponto final, até ~72 caracteres.
- Corpo explica **por que**, não o que — o diff já mostra o que.
- Mensagens genéricas como `finalizado`, `ajustes` ou `wip` são citadas nominalmente pelo enunciado como falha.
- **Sem trailers de atribuição de IA.** A transparência sobre uso de IA que o §2 exige é atendida pelo [`AI_USAGE.md`](../AI_USAGE.md), que é o lugar certo; trailer em toda mensagem polui um histórico que precisa contar uma história.

## 6. Pull Requests

- **Um PR por PBI**, ou por grupo coeso de PBIs pequenos da mesma etapa.
- Descrição preenchida com o [template](../.github/pull_request_template.md): o que mudou, PBI relacionado, decisões que valem revisão, como verificar.
- CI verde é pré-requisito para merge (a partir do PBI-05).
- Abrir PR mesmo trabalhando sozinho — exigência explícita do §6 🟡.
- Desvio do backlog (branch diferente da atribuída, PBIs agrupados) deve ser **declarado no corpo do PR**, não feito em silêncio.

## 7. Tags

Tag **anotada** (`git tag -a`), nunca leve, seguindo SemVer. A entrega final recebe `v1.0.0` no PBI-45.

```bash
git tag -a v1.0.0 -m "Entrega do desafio SRM Credit Engine"
git push origin v1.0.0
```

## 8. Reescrita de histórico

`rebase -i` para organizar commits antes do merge — squash de correções pontuais, reordenação lógica — é esperado no nível 🔴 Sênior. Vale enquanto a branch **não foi mergeada**.

**Ao empurrar histórico reescrito, use `--force-with-lease`, nunca `--force`.** O `--with-lease` aborta se o remoto tiver mudado desde o último fetch, o que evita sobrescrever trabalho que você não viu.

```bash
git push --force-with-lease origin <branch>
```

### Procedimento de recuperação: merge commit indevido em `master`

Já aconteceu neste projeto, no PR #1. Registro do procedimento porque a política acima só vale se houver como aplicá-la depois do erro:

```bash
git fetch origin
git log --oneline --graph origin/master        # identificar o merge commit
git checkout master
git reset --hard <sha-do-ultimo-commit-linear> # tip da branch mergeada
git push --force-with-lease origin master
```

Funciona sem conflito quando os commits da branch já estão lineares abaixo do merge — que é o caso de todo PR que não divergiu. O PR permanece marcado como *merged* no GitHub e seus commits seguem alcançáveis a partir do `master`; só o commit de merge desaparece.

> Reescrever `master` publicado é aceitável aqui por ser repositório de entrega individual, sem colaboradores. **Não é procedimento para repositório compartilhado** — ali a resposta seria `git revert`.

## 9. Como a política é garantida

Documentar não impede. O merge commit do PR #1 aconteceu porque "Create a merge commit" é o botão **default** do GitHub.

Enforcement recomendado nas configurações do repositório:

```bash
gh api -X PATCH repos/:owner/:repo -f allow_merge_commit=false
gh api -X PATCH repos/:owner/:repo -f delete_branch_on_merge=true
```

Com `allow_merge_commit=false`, restam apenas rebase e squash na interface — a convenção deixa de depender de memória.

A partir do PBI-39, git hooks (`core.hooksPath`) rodam formatação no `pre-commit` e testes no `pre-push`, e o pipeline de CI (PBI-05 e PBI-40) bloqueia merge com teste ou lint vermelho.
