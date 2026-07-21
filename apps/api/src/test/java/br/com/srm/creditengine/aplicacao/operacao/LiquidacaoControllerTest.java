package br.com.srm.creditengine.aplicacao.operacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.srm.creditengine.persistencia.entidade.Operacao;
import br.com.srm.creditengine.persistencia.repositorio.LiquidacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.OperacaoRepositorio;

/**
 * Liquidacao com lock otimista e idempotencia (PBI-28).
 *
 * <p><b>Sem {@code @Transactional} na classe.</b> Idempotencia so significa
 * alguma coisa entre transacoes distintas: num teste transacional a segunda
 * chamada enxergaria a primeira pelo contexto de persistencia, e o teste
 * passaria sem provar nada sobre o banco.
 *
 * <p>O teste de concorrencia com threads e' o PBI-29. Aqui se prova o
 * comportamento sequencial: estados que recusam, chave repetida que devolve o
 * original, e a versao subindo quando a operacao muda.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Liquidacao")
class LiquidacaoControllerTest {

    private static final String CEDENTE = "11222333000181";

    @Autowired private MockMvc mvc;
    @Autowired private OperacaoRepositorio operacoes;
    @Autowired private LiquidacaoRepositorio liquidacoes;
    @Autowired private TransactionTemplate emTransacao;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void limpar() {
        liquidacoes.deleteAll();
        operacoes.deleteAll();
    }

    /** Registra uma operacao e devolve o id, usando o proprio endpoint. */
    private Long registrarOperacao(String moedaLiquidacao) throws Exception {
        String corpo = """
                {
                  "documentoCedente": "%s",
                  "moedaTitulo": "BRL",
                  "moedaLiquidacao": "%s",
                  "dataOperacao": "2026-07-20",
                  "titulos": [{"tipoRecebivel":"DUPLICATA_MERCANTIL","numeroDocumento":"DUP-1",
                               "documentoSacado":"52998224725","valorFace":"100000.00",
                               "dataVencimento":"2026-09-04"}]
                }
                """.formatted(CEDENTE, moedaLiquidacao);

        String local = mvc.perform(post("/api/v1/operacoes")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        String caminho = URI.create(local).getPath();
        return Long.valueOf(caminho.substring(caminho.lastIndexOf('/') + 1));
    }

    private static String pedido(String chave) {
        return """
                {"chaveIdempotencia":"%s","liquidadoPor":"operador.teste"}
                """.formatted(chave);
    }

    @Nested
    @DisplayName("Liquidacao bem-sucedida")
    class BemSucedida {

        @Test
        @DisplayName("devolve 201 com o comprovante e marca a operacao LIQUIDADA")
        void liquidaEMarcaOperacao() throws Exception {
            Long id = registrarOperacao("BRL");

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-001")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.operacaoId").value(id))
                    .andExpect(jsonPath("$.valorLiquidado").value(96284.58))
                    .andExpect(jsonPath("$.liquidadoPor").value("operador.teste"))
                    // DEFAULT now() so chega a resposta com @Generated: sem ele
                    // o comprovante sairia sem horario.
                    .andExpect(jsonPath("$.liquidadoEm").exists());

            mvc.perform(get("/api/v1/operacoes/{id}", id))
                    .andExpect(jsonPath("$.status").value("LIQUIDADA"));
        }

        @Test
        @DisplayName("cross-currency congela a cotacao no comprovante")
        void crossCurrencyCongelaCotacao() throws Exception {
            Long id = registrarOperacao("USD");

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-usd")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.valorLiquidado").value(17812.65))
                    .andExpect(jsonPath("$.cotacaoAplicada").value(0.185));
        }

        @Test
        @DisplayName("a versao da operacao sobe: e o que o lock otimista compara")
        void versaoSobeAoLiquidar() throws Exception {
            Long id = registrarOperacao("BRL");

            Long versaoInicial = emTransacao.execute(
                    st -> operacoes.findById(id).map(Operacao::getVersion).orElseThrow());

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-versao")))
                    .andExpect(status().isCreated());

            Long versaoFinal = emTransacao.execute(
                    st -> operacoes.findById(id).map(Operacao::getVersion).orElseThrow());

            assertThat(versaoFinal)
                    .as("sem incremento de versao, o UPDATE concorrente nao teria "
                            + "como detectar que perdeu a corrida")
                    .isGreaterThan(versaoInicial);
        }
    }

    @Nested
    @DisplayName("Idempotencia")
    class Idempotencia {

        @Test
        @DisplayName("mesma chave devolve o comprovante original, com 200 em vez de 201")
        void mesmaChaveDevolveOOriginal() throws Exception {
            Long id = registrarOperacao("BRL");

            String primeira = mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-repetida")))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String segunda = mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-repetida")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(segunda)
                    .as("retry precisa devolver o resultado original, nao um novo")
                    .isEqualTo(primeira);
        }

        @Test
        @DisplayName("o retry nao cria uma segunda liquidacao")
        void retryNaoDuplicaLinha() throws Exception {
            Long id = registrarOperacao("BRL");

            for (int tentativa = 0; tentativa < 3; tentativa++) {
                mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(pedido("chave-insistente")))
                        .andExpect(status().is2xxSuccessful());
            }

            assertThat(liquidacoes.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("chave ja usada em outra operacao e colisao, nao replay")
        void chaveDeOutraOperacaoEhConflito() throws Exception {
            Long primeira = registrarOperacao("BRL");
            Long segunda = registrarOperacao("BRL");

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", primeira)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-compartilhada")))
                    .andExpect(status().isCreated());

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", segunda)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-compartilhada")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString(
                                    String.valueOf(primeira))));

            mvc.perform(get("/api/v1/operacoes/{id}", segunda))
                    .andExpect(jsonPath("$.status")
                            .value("PENDENTE"));
        }
    }

    @Nested
    @DisplayName("Estados que recusam liquidacao")
    class EstadosQueRecusam {

        @Test
        @DisplayName("operacao ja liquidada devolve 409, nunca 500")
        void jaLiquidadaDevolve409() throws Exception {
            Long id = registrarOperacao("BRL");

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-primeira")))
                    .andExpect(status().isCreated());

            // Chave diferente: nao e' replay, e' uma segunda tentativa de
            // liquidar a mesma operacao.
            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-segunda")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString("ja foi liquidada")));

            assertThat(liquidacoes.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("operacao cancelada nao pode ser liquidada")
        void canceladaNaoLiquida() throws Exception {
            Long id = registrarOperacao("BRL");

            emTransacao.executeWithoutResult(st ->
                    operacoes.findById(id).orElseThrow().cancelar());

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-cancelada")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString("CANCELADA")));

            assertThat(liquidacoes.count()).isZero();
        }

        @Test
        @DisplayName("operacao inexistente devolve 404")
        void inexistenteDevolve404() throws Exception {
            mvc.perform(post("/api/v1/operacoes/999999/liquidacao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-fantasma")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("chave de idempotencia ausente devolve 400 por campo")
        void chaveAusenteDevolve400() throws Exception {
            Long id = registrarOperacao("BRL");

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"liquidadoPor":"operador.teste"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.chaveIdempotencia").exists());
        }
    }

    @Nested
    @DisplayName("A constraint do banco como ultima defesa")
    class ConstraintDoBanco {

        @Test
        @DisplayName("UNIQUE (operacao_id) barra a segunda liquidacao e vira 409, nao 500")
        void uniqueBarraSegundaLiquidacao() throws Exception {
            Long id = registrarOperacao("BRL");

            // Insere a liquidacao por fora, SEM mudar o status da operacao.
            // Constroi de proposito o estado que as duas primeiras defesas nao
            // enxergam: a chave e' outra, entao nao ha replay, e o status
            // continua PENDENTE, entao a checagem de estado passa. So resta a
            // constraint — que e' exatamente a razao de ela existir.
            jdbc.update("""
                    INSERT INTO liquidacao (operacao_id, chave_idempotencia, valor_liquidado,
                                            cotacao_aplicada, liquidado_por)
                    VALUES (?, 'inserida-por-fora', 1.00, NULL, 'bypass')
                    """, id);

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-nova")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Conflito de concorrencia"));

            assertThat(liquidacoes.count())
                    .as("a segunda insercao foi recusada pelo banco")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("a resposta de conflito nao vaza nome de constraint nem de tabela")
        void respostaDeConflitoNaoVazaSchema() throws Exception {
            Long id = registrarOperacao("BRL");
            jdbc.update("""
                    INSERT INTO liquidacao (operacao_id, chave_idempotencia, valor_liquidado,
                                            cotacao_aplicada, liquidado_por)
                    VALUES (?, 'outra-chave-por-fora', 1.00, NULL, 'bypass')
                    """, id);

            String corpo = mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("mais-uma-chave")))
                    .andExpect(status().isConflict())
                    .andReturn().getResponse().getContentAsString();

            assertThat(corpo)
                    .as("mensagem de erro nao e' lugar de expor schema")
                    .doesNotContain("uk_liquidacao")
                    .doesNotContain("constraint")
                    .doesNotContain("ConstraintViolation");
        }
    }

    @Nested
    @DisplayName("Consulta do comprovante")
    class Consulta {

        @Test
        @DisplayName("GET devolve o comprovante depois de liquidar")
        void consultaComprovante() throws Exception {
            Long id = registrarOperacao("BRL");

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido("chave-consulta")))
                    .andExpect(status().isCreated());

            // Fora da transacao de escrita: pega LazyInitializationException no
            // mapeamento, se houver.
            mvc.perform(get("/api/v1/operacoes/{id}/liquidacao", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chaveIdempotencia").value("chave-consulta"))
                    .andExpect(jsonPath("$.operacaoId").value(id));
        }

        @Test
        @DisplayName("operacao ainda pendente nao tem comprovante")
        void pendenteNaoTemComprovante() throws Exception {
            Long id = registrarOperacao("BRL");

            mvc.perform(get("/api/v1/operacoes/{id}/liquidacao", id))
                    .andExpect(status().isNotFound());
        }
    }
}
