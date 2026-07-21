package br.com.srm.creditengine.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import br.com.srm.creditengine.persistencia.repositorio.LiquidacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.OperacaoRepositorio;

/**
 * Observabilidade (PBI-34).
 *
 * <p>Metrica de negocio vale mais que metrica de infraestrutura numa avaliacao:
 * "quantas liquidacoes falharam por conflito" responde a uma pergunta que
 * alguem faz as tres da manha; uso de CPU nao. Estes testes cobrem as de
 * negocio — as de JVM e HTTP vem do Actuator sem codigo nosso.
 *
 * <p><b>Sem {@code @Transactional}:</b> contador so anda com transacao que
 * comita, e a limpeza precisa acontecer de verdade entre os casos.
 *
 * <p><b>{@code @AutoConfigureMetrics} e' obrigatorio.</b> O Boot desliga a
 * exportacao de metricas em teste por padrao, e sem a anotacao
 * {@code /actuator/prometheus} responde 404 — enquanto funciona no servidor de
 * verdade, que e' a combinacao mais confusa possivel. No Boot 4 a anotacao
 * mudou de nome e de pacote: era {@code @AutoConfigureObservability}, agora e'
 * {@code org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@DisplayName("Observabilidade")
class ObservabilidadeTest {

    private static final String CEDENTE = "11222333000181";

    @Autowired private MockMvc mvc;
    @Autowired private MeterRegistry registro;
    @Autowired private OperacaoRepositorio operacoes;
    @Autowired private LiquidacaoRepositorio liquidacoes;

    @AfterEach
    void limpar() {
        liquidacoes.deleteAll();
        operacoes.deleteAll();
    }

    private double contador(String nome, String... tags) {
        var busca = registro.find(nome);
        for (int i = 0; i < tags.length; i += 2) {
            busca = busca.tag(tags[i], tags[i + 1]);
        }
        var contador = busca.counter();
        return contador == null ? 0 : contador.count();
    }

    private Long registrarOperacao() throws Exception {
        String local = mvc.perform(post("/api/v1/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentoCedente":"%s","registradoPor":"ana.mesa",
                                 "moedaTitulo":"BRL","moedaLiquidacao":"BRL",
                                 "dataOperacao":"2026-07-20",
                                 "titulos":[{"tipoRecebivel":"DUPLICATA_MERCANTIL",
                                             "numeroDocumento":"D-1","documentoSacado":"52998224725",
                                             "valorFace":"100000.00","dataVencimento":"2026-09-04"}]}
                                """.formatted(CEDENTE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        String caminho = URI.create(local).getPath();
        return Long.valueOf(caminho.substring(caminho.lastIndexOf('/') + 1));
    }

    private void liquidar(Long id, String chave, int statusEsperado) throws Exception {
        mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chaveIdempotencia":"%s","liquidadoPor":"bruno"}
                                """.formatted(chave)))
                .andExpect(status().is(statusEsperado));
    }

    @Nested
    @DisplayName("Exposicao do Actuator")
    class Exposicao {

        @Test
        @DisplayName("prometheus, health e metrics respondem")
        void endpointsNecessariosRespondem() throws Exception {
            mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
            mvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("endpoints que despejam configuracao ou memoria nao sao expostos")
        void endpointsSensiveisNaoExpostos() throws Exception {
            // Com exposure "*" entrariam todos. env e configprops despejam a
            // configuracao efetiva — inclusive valores vindos de variaveis de
            // ambiente — e heapdump entrega a memoria do processo.
            for (String endpoint : new String[] {
                    "env", "beans", "configprops", "heapdump", "threaddump", "loggers"}) {
                mvc.perform(get("/actuator/" + endpoint))
                        .andExpect(status().isNotFound());
            }
        }
    }

    @Nested
    @DisplayName("Metricas de negocio")
    class Metricas {

        @Test
        @DisplayName("registrar operacao incrementa o contador")
        void operacaoIncrementaContador() throws Exception {
            double antes = contador("creditengine.operacoes.registradas");

            registrarOperacao();

            assertThat(contador("creditengine.operacoes.registradas")).isEqualTo(antes + 1);
        }

        @Test
        @DisplayName("os tres desfechos de liquidacao sao contados separadamente")
        void desfechosSaoDistinguidos() throws Exception {
            double concluidaAntes = contador("creditengine.liquidacoes", "resultado", "concluida");
            double repeticaoAntes = contador("creditengine.liquidacoes", "resultado", "repeticao");
            double conflitoAntes = contador("creditengine.liquidacoes", "resultado", "conflito");

            Long id = registrarOperacao();
            liquidar(id, "chave-a", 201);   // concluida
            liquidar(id, "chave-a", 200);   // repeticao: mesma chave
            liquidar(id, "chave-b", 409);   // conflito: ja liquidada

            assertThat(contador("creditengine.liquidacoes", "resultado", "concluida"))
                    .isEqualTo(concluidaAntes + 1);
            assertThat(contador("creditengine.liquidacoes", "resultado", "repeticao"))
                    .as("repeticao subindo e cliente com retry ativo: o sistema funcionando")
                    .isEqualTo(repeticaoAntes + 1);
            assertThat(contador("creditengine.liquidacoes", "resultado", "conflito"))
                    .as("conflito subindo e duas mesas no mesmo titulo: incidente. "
                            + "Somados, os dois seriam indistinguiveis")
                    .isEqualTo(conflitoAntes + 1);
        }

        @Test
        @DisplayName("o timer de precificacao registra amostras")
        void timerDePrecificacaoRegistra() throws Exception {
            long antes = registro.find("creditengine.precificacao.duracao").timer().count();

            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moedaTitulo":"BRL","moedaLiquidacao":"BRL",
                                     "dataOperacao":"2026-07-20",
                                     "titulos":[{"tipoRecebivel":"DUPLICATA_MERCANTIL",
                                                 "valorFace":"1000.00",
                                                 "dataVencimento":"2026-09-04"}]}
                                    """))
                    .andExpect(status().isOk());

            assertThat(registro.find("creditengine.precificacao.duracao").timer().count())
                    .isEqualTo(antes + 1);
        }

        @Test
        @DisplayName("lote recusado nao entra no timer de precificacao")
        void loteRecusadoNaoEntraNoTimer() throws Exception {
            long antes = registro.find("creditengine.precificacao.duracao").timer().count();

            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moedaTitulo":"BRL","moedaLiquidacao":"BRL",
                                     "dataOperacao":"2026-07-20",
                                     "titulos":[{"tipoRecebivel":"CHEQUE_PRE_DATADO",
                                                 "valorFace":"1000.00",
                                                 "dataVencimento":"2028-01-01"}]}
                                    """))
                    .andExpect(status().isUnprocessableEntity());

            assertThat(registro.find("creditengine.precificacao.duracao").timer().count())
                    .as("recusa medida como precificacao rapida puxaria o percentil "
                            + "para baixo e mascararia lentidao real")
                    .isEqualTo(antes);
        }

        @Test
        @DisplayName("o circuit breaker do provedor de cotacao e observavel")
        void circuitBreakerEhObservavel() throws Exception {
            String corpo = mvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(corpo)
                    .as("vem do resilience4j assim que ha um MeterRegistry; "
                            + "contador proprio seria duplicacao")
                    .contains("resilience4j_circuitbreaker_state")
                    .contains("provedorDeCotacao");
        }

        @Test
        @DisplayName("nenhuma metrica tem identificador em tag")
        void semIdentificadorEmTag() throws Exception {
            registrarOperacao();

            String corpo = mvc.perform(get("/actuator/prometheus"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(corpo)
                    .as("tag com id, CNPJ ou chave gera cardinalidade ilimitada e "
                            + "ainda publica dado de cliente numa base menos protegida")
                    .doesNotContain(CEDENTE)
                    .doesNotContain("52998224725");
        }
    }
}
