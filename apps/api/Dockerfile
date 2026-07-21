# =============================================================================
# API — build multi-stage
# =============================================================================
#
# A imagem final leva JRE, o jar e nada mais: sem Maven, sem codigo-fonte, sem
# .m2. Alem do tamanho, e' reducao de superficie — compilador dentro de
# container de producao e' ferramenta a disposicao de quem entrar nele.
# =============================================================================

# ------------------------------------------------------------------- build
# Usa o wrapper, e nao o Maven da imagem: o wrapper fixa a versao 3.9.16, e
# build que difere do que roda no CI e na maquina do dev deixa de ser
# reproduzivel exatamente quando alguem precisa investigar diferenca.
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# O wrapper e o POM entram antes do codigo. Enquanto as dependencias nao
# mudarem, esta camada e' reaproveitada e o `go-offline` nao roda de novo —
# a diferenca entre um rebuild de segundos e um de minutos.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/

# -DskipTests de proposito: os testes precisam de Postgres real, que nao existe
# durante o build da imagem. Quem roda a suite e' o CI (PBI-40), contra o banco
# do Compose. Build de imagem que "passa nos testes" pulando os que importam
# seria pior do que nao rodar nenhum.
RUN ./mvnw -B -q clean package -DskipTests

# ----------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario proprio, sem shell de login e sem home utilizavel. Container rodando
# como root significa que uma falha na aplicacao vira root no container — e,
# com um escape, algo pior.
RUN addgroup -S credit && adduser -S -G credit -H -s /sbin/nologin credit

WORKDIR /app

COPY --from=build --chown=credit:credit /build/target/*.jar app.jar

USER credit

EXPOSE 8081

# O healthcheck usa o Actuator, que ja esta exposto (PBI-34). wget vem do
# busybox da imagem alpine — sem curl, e sem instalar nada so para isto.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null http://localhost:8081/actuator/health || exit 1

# -XX:MaxRAMPercentage faz a JVM enxergar o limite do container em vez da
# memoria da maquina inteira. Sem isso, um limite de memoria no orquestrador
# vira OOMKill em vez de GC.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
