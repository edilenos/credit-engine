<!--
Convenções em docs/git-workflow.md.
Merge por rebase ou squash — merge commit e' proibido.
-->

## O que mudou

<!-- Uma ou duas frases. O diff mostra o que; explique por que. -->

**PBI:** <!-- PBI-XX, ou "nenhum" com justificativa -->

## Decisões que valem revisão

<!--
Escolhas que um revisor questionaria, com o motivo. Exemplos do projeto:
por que a porta do banco e' 55432, por que o relatorio usa SQL nativo em vez
de JPA, por que o lote e' sincrono. Se nao houver nenhuma, escreva "nenhuma".
-->

## Como verificar

```bash
# Comandos exatos, e o resultado esperado.
```

## Desvios do backlog

<!--
Branch diferente da atribuida ao PBI, PBIs agrupados, criterio de aceite nao
atendido. Desvio declarado e' decisao; desvio silencioso e' omissao.
Se nao houver, escreva "nenhum".
-->

## Definition of Done

- [ ] Todos os critérios de aceite do PBI verificados
- [ ] Testes cobrindo os critérios de aceite, passando
- [ ] CI verde (build, testes e, a partir do PBI-40, lint)
- [ ] Sem `TODO`, código comentado ou `System.out.println`
- [ ] `ddl-auto: validate` passando, se houve mudança de schema
- [ ] Documentação afetada atualizada (`README.md`, `docs/`, `CLAUDE.md`)
- [ ] `AI_USAGE.md` atualizado, se houve uso relevante de IA
- [ ] Commits atômicos seguindo Conventional Commits, cada um compilando sozinho
- [ ] Nenhuma credencial no diff — repositório é público
