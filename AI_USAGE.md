# AI_USAGE.md

Registro do uso de IA neste desafio, conforme exigido pelo §2 do enunciado.

**Documento vivo.** É atualizado a cada PBI concluído, não reconstruído no fim — o Definition of Done em [`docs/backlog.md`](docs/backlog.md) traz "`AI_USAGE.md` atualizado" como item de fechamento. Reconstrução tardia se percebe pela falta de detalhe.

---

## 1. Como a IA foi usada

Ferramenta: **Claude Code** (Claude Opus 4.8), em sessão de terminal com acesso ao repositório.

Modelo de trabalho adotado: a IA gera, investiga e propõe; eu decido, reviso e aceito ou rejeito. Duas regras práticas saíram das falhas descritas na seção 3 e valem para tudo que veio depois:

1. **Nada que a IA afirme sobre versão de biblioteca, API de framework ou estado do ambiente entra sem verificação mecânica.** Consulta ao Maven Central, leitura do POM, `netstat`, log do servidor. Memória de modelo não conta como evidência.
2. **Número gerado em prosa é suspeito até derivar de uma fonte única.** Ver o caso 3.4.

O stack escolhido agrava o ponto 1 de propósito: **Spring Boot 4.1** e **Next.js 16** estão à frente do material de treino disponível. Boot 4 renomeou starters, reorganizou pacotes de autoconfiguração e migrou para Jackson 3; Next 16 tem breaking changes próprios. Isso torna o projeto um bom laboratório para observar onde a IA erra com confiança.

---

## 2. Prompts estratégicos

| Objetivo | O que pedi | Resultado |
|---|---|---|
| **Contexto de projeto** | Analisar o repositório e gerar um `CLAUDE.md` com comandos, arquitetura e convenções | Útil, mas produziu uma afirmação factualmente errada — ver caso 3.1 |
| **Decomposição do escopo** | Quebrar o enunciado em backlog executável, com PBIs detalhados e entrega por etapas | O melhor resultado da sessão: 45 PBIs em 9 etapas, com matriz de rastreabilidade requisito → PBI |
| **Avaliação de proposta minha** | "Faz sentido implementar as convenções de contagem de dias por Strategy? Avalie antes de concordar" | A IA validou a ideia e acrescentou o acoplamento de unidade que eu não tinha visto — ver seção 5 |
| **Questionamento de escopo** | "O processamento em lote precisa de mensageria para atender o requisito?" | A IA argumentou **contra** implementar, mostrando que mensageria brigaria com o requisito ACID do §3.3 |
| **Auditoria do próprio trabalho** | "Reavalie se o backlog atende o enunciado" | Encontrou três lacunas reais e um erro grave de estimativa que a própria IA tinha introduzido |
| **Spikes de compatibilidade** | Verificar springdoc, Resilience4j e big-math contra Spring Boot 4.1 | Ver seção 4 — o caso em que a verificação evitou o erro |

O prompt mais produtivo foi o de **auditoria**: pedir que a IA reavaliasse criticamente o que ela mesma tinha produzido, em vez de aceitar a primeira versão. Foi o que revelou o erro de estimativa do caso 3.4.

---

## 3. Onde a IA errou

### 3.1 Código inseguro: senha real como valor padrão em arquivo versionado

**O que aconteceu.** O `application.yaml` gerado com assistência de IA trazia a senha real do banco como *default* do placeholder:

```yaml
password: ${DB_PASSWORD:<senha real do banco>}   # fallback hardcoded, redigido aqui
```

O desenho pretendido era outro: ler credencial do ambiente, com import opcional de `application-local.yaml` (fora do versionamento) para desenvolvimento. O default hardcoded duplicava exatamente o segredo que o arquivo ignorado existia para proteger.

**O agravante.** O `CLAUDE.md` — também gerado por IA — afirmava o contrário:

> "`application.yaml` carrega nenhum segredo: lê `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`, com default de senha vazia."

Ou seja: a IA escreveu o código inseguro **e** documentou que ele era seguro. Quem confiasse na documentação não olharia o arquivo.

**Como detectei.** Numa varredura do repositório antes do primeiro commit, comparando o que a documentação afirmava com o conteúdo real dos arquivos. Não foi sorte: foi desconfiar de uma afirmação de segurança feita por quem escreveu o código.

**Como corrigi.** Default vazio (`${DB_PASSWORD:}`), `.gitignore` na raiz, `application-local.yaml.example` versionado como referência, e rotação da senha. Commit `483f81f`.

**Impacto real: zero.** O arquivo nunca chegou a ser commitado — `git log --all -S` confirma zero ocorrências no histórico. O repositório também só se tornou público depois da correção.

**Errei na avaliação de risco.** Classifiquei a credencial como "Postgres em localhost, exposição efetiva zero". Depois descobri (caso 3.2) que a porta 5433 é um **túnel SSH**, então aquela senha era de um banco remoto, não local. A conclusão certa era mais severa do que a que tirei na hora.

### 3.2 Diagnóstico errado, sustentado por um falso positivo

**O sintoma.** Depois de containerizar o banco, a suíte falhava com `FATAL: password authentication failed for user "postgres"`.

**Minha primeira hipótese, errada.** Que o `spring.config.import` nunca tivesse funcionado, e que só parecia funcionar porque a senha hardcoded do caso 3.1 mascarava a falha. Hipótese plausível — e falsa.

**O falso positivo que a sustentou.** Testei a senha com `psql` de dentro do container e obteve sucesso:

```
docker exec -e PGPASSWORD=... psql -h 127.0.0.1 -U postgres -d credit-engine   # "auth ok"
```

Conclusão tirada: a senha está certa, logo o problema é do Spring. **Errado.** O `pg_hba.conf` gerado pela imagem usa `trust` para conexões de loopback — aquele teste não validou senha nenhuma. Um teste que não podia falhar foi lido como confirmação.

**Meu próprio erro de ferramenta.** Verifiquei quem ocupava a porta com:

```bash
netstat -ano | grep "LISTENING.*:5433"     # nunca casa
```

O `netstat` imprime o **endereço antes do estado** (`TCP 0.0.0.0:5433 ... LISTENING`), então o padrão exige `LISTENING` antes de `:5433` e jamais casa. O comando devolveu "nada escutando" durante várias iterações, escondendo justamente a evidência decisiva.

**O que fechou o diagnóstico.** Olhar o log do servidor em vez de inferir:

```bash
docker logs credit-engine-db | grep -i authentication   # zero tentativas
```

Nenhuma tentativa de autenticação chegava ao container. Logo, a conexão ia para outro lugar.

**A causa raiz.** Um túnel SSH dentro da distro WSL faz bind em `127.0.0.1:5433` **e** `:5434`. No Windows, `localhost` resolve para o bind mais específico, então um container publicado em `0.0.0.0:5433` fica sombreado: a aplicação conecta silenciosamente no banco errado. O sintoma é indistinguível de erro de credencial.

**Correção.** Porta 55432 no host, fora da faixa de conflito, com a justificativa registrada no `docker-compose.yml` e no PBI-03. Commit `865e3ae`.

**Lição.** Duas hipóteses erradas seguidas custaram várias iterações, e as duas foram reforçadas por evidência mal interpretada. O que resolveu não foi raciocinar melhor — foi trocar inferência por observação direta na fonte (o log do servidor).

### 3.3 Alucinação de API por versão de framework

Spring Boot 4 renomeou e repackageou coisas que praticamente todo material de treino ainda mostra do jeito do Boot 3. As armadilhas ativas neste projeto:

| A IA tende a sugerir | O correto no Boot 4.1 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` (único) | Dividido por feature: `-webmvc-test`, `-data-jpa-test`, `-flyway-test`, ... |
| `com.fasterxml.jackson.databind` | `tools.jackson.databind` (Jackson 3) |
| Pacotes de autoconfiguração antigos | Reorganizados: `org.springframework.boot.flyway.autoconfigure`, `...webmvc.error` |

**Mitigação adotada:** conferir contra o POM e os jars antes de escrever qualquer import, e registrar as armadilhas no `apps/api/CLAUDE.md` para que não voltem a cada sessão. O mesmo vale para Next.js 16, onde o scaffolding traz um `AGENTS.md` determinando a leitura de `node_modules/next/dist/docs/` antes de escrever código.

### 3.4 Deriva aritmética: três vezes o mesmo erro

O backlog carrega estimativa de esforço por etapa e por PBI. **Três vezes** essas duas fontes divergiram:

| Ocorrência | Sintoma |
|---|---|
| 1ª | Etapas somavam 5,5 dias; a soma dos PBIs dava outra coisa |
| 2ª | A tabela de cenários trazia prazos que não derivavam das horas por etapa |
| 3ª | Etapas 3 e 7 declaradas acima da soma dos próprios PBIs |

A terceira só apareceu porque conferi com script em vez de ler. O padrão é claro: **LLM produz número plausível em prosa com a mesma fluência com que produz texto**, e números plausíveis não somam.

**Correção estrutural, não pontual.** Declarei no backlog que as horas por etapa são *rollup* do campo `Tamanho` de cada PBI — fonte única — e incluí o `awk` que recalcula tudo. Corrigir o número pela quarta vez não teria resolvido; remover a possibilidade de divergir, sim.

### 3.5 A IA vazou a senha dentro do documento que descreve o vazamento

Aconteceu enquanto este arquivo era escrito, e é o caso mais ilustrativo da sessão.

Ao redigir o caso 3.1, a IA reproduziu o trecho problemático **com a senha real em texto claro**, para "documentar fielmente" o que havia acontecido:

```yaml
password: ${DB_PASSWORD:<senha real, aqui redigida>}
```

O `AI_USAGE.md` é entregável de repositório **público**. Se tivesse sido commitado assim, o documento que narra a correção de um vazamento teria consumado o vazamento — e desta vez de verdade, porque a credencial original nunca chegou a ser publicada, ao contrário do que teria acontecido aqui. Agravante: o caso 3.2 já havia estabelecido que aquela senha é de um banco **remoto**, alcançado por túnel SSH, não de um Postgres local.

**Detectado** na varredura de segredos que faço antes de cada commit — a mesma rotina que pegou o caso 3.1. **Corrigido** substituindo o literal por um marcador antes de qualquer `git add`. Impacto: zero, o arquivo nunca foi commitado com o valor.

**Por que importa.** A IA tinha acabado de escrever, no mesmo arquivo, que "o repositório é público, então a regra de segredos vem primeiro" — e violou a regra três parágrafos depois. Não é distração: é a ausência de um modelo persistente de *o que não pode sair daqui*. Cada trecho é gerado localmente coerente, sem checar contra uma restrição global.

Reforça a regra da seção 7: varredura de segredo antes de todo commit, mecânica, **inclusive em arquivos de documentação** — que é onde a guarda tende a baixar.

---

## 4. Onde a verificação evitou o erro

Nem todo caso é de falha consumada. Antes de escrever código de API, rodei um spike de compatibilidade das bibliotecas contra o Spring Boot 4.1 — e ele desarmou duas armadilhas que teriam custado horas de depuração:

**Resilience4j.** Existem **dois artefatos, na mesma versão 2.4.0**:

```
io.github.resilience4j:resilience4j-spring-boot3:2.4.0
io.github.resilience4j:resilience4j-spring-boot4:2.4.0
```

Tutoriais e material de treino apontam para o `-spring-boot3`. O artefato errado **resolve, baixa e compila** — falha só na autoconfiguração, em runtime. É o pior tipo de armadilha: nomes quase idênticos, mesma versão, falha tardia.

**springdoc-openapi.** A linha `2.8.x` é do Boot 3; a `3.0.x` é a do Boot 4. Confirmei lendo o POM pai da versão 3.0.3, que declara `spring-boot-starter-parent` **4.0.5** e depende de artefatos com nome Boot 4 (`spring-boot-tomcat`, `spring-boot-health`).

Registrei ainda um risco residual: o springdoc 3.0.3 foi construído contra Boot **4.0.5** e o projeto está em **4.1.0**, uma *minor* à frente. Dentro da mesma *major* deveria funcionar, mas isso será confirmado com `dependency:tree` e smoke test, não presumido.

**Por que isso importa mais que os erros.** Os casos da seção 3 mostram a IA falhando. Este mostra o método funcionando: a resposta de memória teria sido `-spring-boot3` com alta confiança, e a verificação custou dois minutos de consulta ao Maven Central.

---

## 5. Onde a IA agregou de verdade

Para não pintar um quadro só de falhas — dois casos em que o resultado foi melhor do que eu teria produzido sozinho no mesmo tempo.

**Decomposição do escopo.** Transformar o enunciado em backlog de 45 PBIs com critérios de aceite, dependências, prioridade MoSCoW e matriz de rastreabilidade levaria horas manualmente. E a matriz revelou algo que leitura corrida não revela: o exercício MoSCoW mostrou que **40 dos 45 PBIs são obrigatórios**, o que provou que "cortar escopo" não era uma alternativa real e mudou a decisão de entrega.

**Profundidade de domínio na precificação.** Eu propus tratar as convenções de contagem de dias (taxa diária, base 252, 30/360, base 365) por Strategy, já que FIDCs usam várias e o enunciado não fixa nenhuma. A IA validou a ideia e acrescentou o que eu não tinha visto: **a periodicidade da taxa precisa casar com a unidade que a convenção produz**. Combinar spread de 1,5% a.m. com base 252 (anual) não lança exceção — produz um preço plausível e errado por ordem de grandeza. Virou requisito de validação explícito e o risco R7 do backlog.

Também corrigiu minha nomenclatura: eu tratava "base 360" e "30/360" como sinônimos, e são convenções diferentes.

---

## 6. Análise crítica

**Onde economizou tempo.** Estruturação e amplitude: decompor escopo, gerar documentação, varrer o repositório procurando inconsistência entre o que está escrito e o que existe, e verificar compatibilidade de biblioteca mecanicamente. Também é boa interlocutora para *rejeitar* escopo — o caso da mensageria, em que argumentou contra implementar e a favor de documentar a decisão.

**Onde atrapalhou.** Três padrões distintos, com causas distintas:

1. **Confiança uniforme.** A IA afirma com o mesmo tom o que sabe e o que supõe. A senha insegura e a documentação que a contradizia vieram no mesmo pacote, redigidas com a mesma segurança.
2. **Aritmética em prosa.** Números gerados junto com texto não fecham, e não fecham repetidamente. Precisam de fonte única e verificação por script.
3. **Diagnóstico enviesado.** Formada a hipótese, a IA interpreta evidência ambígua a favor dela. O `psql` que "autenticou" via `trust` foi lido como confirmação, não questionado.

**A conclusão que levo.** A IA é boa produzindo *estrutura* e ruim garantindo *consistência* — inclusive consigo mesma. O ganho real não veio de aceitar o que ela produziu, veio de usá-la e depois **auditá-la**, de preferência pedindo que auditasse o próprio trabalho. O prompt que mais rendeu na sessão inteira foi "reavalie criticamente se isto atende o enunciado", e ele encontrou erro que a geração original não tinha visto.

Sobre a exigência de autoria do §2: todo trecho aqui descrito foi revisado, e as decisões de arquitetura — nível de entrega, escopo, convenções de contagem, recusa de mensageria, política de precisão decimal — foram tomadas por mim, com a IA como interlocutora. Os erros das seções 3.1 a 3.4 estão documentados porque foram encontrados por revisão, e é essa revisão que a autoria significa.

---

## 7. Regras de trabalho adotadas

Derivadas dos casos acima, valendo para o resto da entrega:

- Verificar versão e coordenada de biblioteca no Maven Central ou no POM — nunca aceitar de memória.
- Antes de escrever import de Spring Boot ou Next, conferir contra o POM, o lockfile e a documentação vendorizada.
- Número que aparece em documento precisa derivar de fonte única, com script que recalcula.
- Diante de falha de ambiente, observar na fonte (log do servidor, `netstat` com o padrão certo) antes de formar hipótese.
- Desconfiar de teste que não pode falhar.
- Afirmação de segurança feita por quem escreveu o código merece verificação independente.
- Varredura mecânica de segredo antes de **todo** commit, inclusive em documentação — ver caso 3.5.
