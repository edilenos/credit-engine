# Frontend — SRM Credit Engine

[![CI](https://github.com/edilenos/credit-engine/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/edilenos/credit-engine/actions/workflows/ci.yml)

SPA em Next.js 16 / React 19 que consome a API de precificação e liquidação.

O README da raiz do repositório cobre o projeto inteiro; este documento trata só do frontend.

## Como rodar

### Em container, junto com a stack

Da raiz do repositório, sem precisar de Node instalado:

```bash
cp .env.example .env
docker compose up --build   # http://localhost:3000
```

A imagem usa a saída `standalone` do Next: o runtime leva o `server.js` gerado e o recorte de `node_modules` que a aplicação de fato usa, sem `pnpm install`. Os estáticos são copiados à mão porque o `standalone` os deixa de fora de propósito — sem isso a aplicação sobe sem CSS nem JS de cliente. Roda como usuário `credit`, não root.

> ⚠️ **`NEXT_PUBLIC_API_URL` é de tempo de *build*, não de runtime.** Ela é substituída dentro do bundle quando a imagem é construída; defini-la no `environment` do Compose não tem efeito nenhum. Mudou? `docker compose up --build`.
>
> E o valor precisa ser alcançável pelo **navegador**. Quem chama a API é o browser do operador, então `http://api:8081` — que resolveria de container para container — daria erro de DNS na máquina dele. Por isso o padrão é a porta publicada no host.

### Localmente

O gerenciador é **pnpm**, fixado em `packageManager` no `package.json`. Com Corepack habilitado (`corepack enable`), a versão correta é usada automaticamente.

```bash
pnpm install
cp .env.example .env.local   # ajuste a URL da API se necessário
pnpm dev                     # http://localhost:3000
```

A API precisa estar no ar em `http://localhost:8081` — **não** na 8080 padrão do Spring Boot. Instruções de como subi-la ficam no README da raiz.

| Comando | O que faz |
|---|---|
| `pnpm dev` | Servidor de desenvolvimento na porta 3000 |
| `pnpm build` | Build de produção |
| `pnpm lint` | ESLint (flat config) |

### Configuração

`NEXT_PUBLIC_API_URL` é obrigatória e **não tem valor padrão no código**. Um fallback para `http://localhost:8081` faria a aplicação funcionar na máquina de quem escreveu e falhar em qualquer outra, com o sintoma aparecendo longe da causa. Sem padrão, a configuração ausente se anuncia na primeira requisição, com mensagem dizendo o que fazer.

## Arquitetura

O enunciado avalia a separação entre apresentação e lógica de estado (§4.3). A regra que a sustenta é simples: **componente não busca dado**.

```
app/          rotas e layout (App Router)
components/   apresentação pura — sem fetch, sem regra
features/     hooks e estado por domínio (simulação, transações)
services/     cliente HTTP, chamadas por domínio, erro e timeout
types/        tipos espelhando os contratos da API
lib/          utilitários transversais (formatação pt-BR)
```

O fluxo é sempre no mesmo sentido:

```
componente  →  hook (features/)  →  service  →  http-client  →  API
```

Um componente que precise de dado recebe por prop ou consome um hook. Nenhum deles importa `http-client` diretamente, e é isso que mantém a apresentação testável sem rede.

### Tratamento de erro fica em um lugar só

`services/http-client.ts` é o único ponto que fala com a rede. Ele aplica timeout, converte qualquer falha em erro tipado e traduz status HTTP em mensagem exibível:

| Situação | Tipo | O que a tela mostra |
|---|---|---|
| Regra de negócio recusou (422) | `ErroDaApi` | A mensagem de domínio da API, já em português |
| Payload inválido (400) | `ErroDaApi` | Os campos inválidos, com `camposInvalidos` para marcar o formulário |
| Falha interna (5xx) | `ErroDaApi` | Mensagem genérica — detalhe de erro interno não ajuda o operador e pode vazar estrutura |
| Rede, CORS ou timeout | `ErroDeConexao` | Orientação acionável, não código de status |

Nenhuma mensagem carrega stacktrace ou status cru. A tradução acontece uma vez; repeti-la por tela garantiria que uma delas vazasse `Error 500` para a mesa.

### Decimais chegam como número JSON

A API calcula em `BigDecimal`, mas o Jackson serializa como número JSON — verificado no corpo cru (`"valorPresente":96284.58`). `JSON.parse` transforma isso em `double`, então a precisão arbitrária do backend termina na borda HTTP.

Na prática isso é exato para exibição nesta aplicação: `double` guarda ~15 dígitos significativos e a coluna é `NUMERIC(19,2)`. A divergência só apareceria em valores acima da casa de 1e13.

A consequência para o código é uma regra: **o cliente não faz aritmética com dinheiro**. Totais, deságios e conversões vêm calculados da API; aqui só se formata. Somar no navegador reintroduziria exatamente o erro que o backend teve o trabalho de evitar.

### Sem biblioteca de estado global — decisão registrada

O enunciado pede "Gerenciamento de Estado Global **(se necessário)**". O condicional é convite a decidir, não a ignorar.

**Análise.** São duas telas, e elas não compartilham estado mutável:

- o **Painel do Operador** tem estado efêmero de formulário, que morre com a tela;
- o **Grid de Transações** tem estado de filtro e paginação, que pertence à URL — requisito do próprio grid, para o link ser compartilhável e sobreviver a reload;
- o que sobra são dados de servidor (moedas, tipos de recebível), que é problema de *cache de server state*, não de client state.

**Decisão: nenhuma biblioteca de estado global.** Estado de servidor resolvido no cliente HTTP ou em hook de fetch com cache; estado de UI local ao componente; estado de filtro na URL. Introduzir Redux ou Zustand aqui seria a violação de KISS que o §8.2 avalia — e a arquitetura ficaria com uma camada que não resolve problema nenhum deste sistema.

**Gatilho de revisão:** uma terceira tela compartilhando estado mutável com as outras reabre a decisão. Se isso acontecer, o certo é reescrever esta seção, não contrariá-la em silêncio.

## Versões à frente do material de treino

Três pontos onde este projeto difere do que a maioria dos tutoriais mostra:

- **Next.js 16** — `middleware` passou a se chamar **`proxy`** (arquivo `proxy.ts`). A funcionalidade é a mesma, o nome não.
- **Tailwind CSS 4** — configuração é CSS-first. **Não existe `tailwind.config.js`**; tokens de tema vão em `@theme` dentro de `app/globals.css`, e o plugin PostCSS é `@tailwindcss/postcss`.
- **ESLint flat config** — `eslint.config.mjs` com `defineConfig`, não `.eslintrc`.

A documentação da versão instalada está em `node_modules/next/dist/docs/` e é a fonte a consultar antes de escrever código.

## Convenções de apresentação

- Moeda e data em **pt-BR**, com a moeda sempre indicada. A formatação é centralizada em `lib/format.ts`.
- Datas da API são `LocalDate` (sem hora e sem fuso). A conversão para exibição é **textual** — `new Date("2026-09-04")` seria interpretada como UTC meia-noite e mostraria o dia anterior em qualquer fuso a oeste de Greenwich, o que inclui o Brasil inteiro.
- Toda validação feita aqui existe também no servidor. O cliente não é fronteira de confiança, e o enunciado trata validação de entrada como requisito de segurança.
