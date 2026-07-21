# =============================================================================
# Frontend — build multi-stage com saida standalone
# =============================================================================

# ------------------------------------------------------------- dependencias
FROM node:22-alpine AS deps

WORKDIR /app

# Camada propria para as dependencias: enquanto o lockfile nao mudar, ela e'
# reaproveitada e a instalacao nao roda de novo.
COPY package.json pnpm-lock.yaml ./

# Corepack respeita o campo packageManager do package.json — a mesma versao de
# pnpm que roda na maquina do dev, sem fixa-la em dois lugares.
RUN corepack enable && pnpm install --frozen-lockfile

# -------------------------------------------------------------------- build
FROM node:22-alpine AS build

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# ATENCAO: NEXT_PUBLIC_* e' substituida em TEMPO DE BUILD, nao em runtime.
# Definir esta variavel no `environment` do Compose nao tem efeito nenhum —
# quando o container sobe, o valor ja esta dentro do bundle.
#
# E o valor precisa ser alcancavel pelo NAVEGADOR, nao pelo container. Quem
# chama a API e' o browser do operador, entao `http://api:8081` — que
# funcionaria de container para container — daria erro de DNS na maquina dele.
# Por isso o default aponta para a porta publicada no host.
ARG NEXT_PUBLIC_API_URL=http://localhost:8081
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}

RUN corepack enable && pnpm build

# ------------------------------------------------------------------ runtime
FROM node:22-alpine AS runtime

RUN addgroup -S credit && adduser -S -G credit -H -s /sbin/nologin credit

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# O standalone traz o server.js e o recorte de node_modules. Os estaticos
# ficam de fora dele de proposito (a ideia e' servi-los por CDN), entao vao
# copiados a mao — sem isso a aplicacao sobe sem CSS nem JS de cliente.
COPY --from=build --chown=credit:credit /app/.next/standalone ./
COPY --from=build --chown=credit:credit /app/.next/static ./.next/static

USER credit

EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
    CMD wget -q -O /dev/null http://localhost:3000/ || exit 1

CMD ["node", "server.js"]
