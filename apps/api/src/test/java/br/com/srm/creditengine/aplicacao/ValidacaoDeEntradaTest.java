package br.com.srm.creditengine.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validacao de entrada (PBI-32).
 *
 * <p>O enunciado enquadra validacao como requisito de <b>seguranca</b>, nao de
 * usabilidade: a API nao pode depender da boa-fe do cliente. Boa parte das
 * regras entrou junto com os endpoints (PBIs 24, 27, 28); aqui se prova o que
 * a auditoria deste PBI encontrou em falta — o teto de corpo, o estouro do
 * total do lote — e se fixa o comportamento das demais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Validacao de entrada")
class ValidacaoDeEntradaTest {

    private static final String CEDENTE = "11222333000181";

    @Autowired
    private MockMvc mvc;

    private static String simulacaoCom(String titulos) {
        return """
                {"moedaTitulo":"BRL","moedaLiquidacao":"BRL",
                 "dataOperacao":"2026-07-20","titulos":[%s]}
                """.formatted(titulos);
    }

    private static String titulo(String valorFace) {
        return """
                {"tipoRecebivel":"DUPLICATA_MERCANTIL","valorFace":"%s",
                 "dataVencimento":"2026-09-04"}""".formatted(valorFace);
    }

    private static String cessaoCom(String titulos) {
        return """
                {"documentoCedente":"%s","registradoPor":"operador","moedaTitulo":"BRL",
                 "moedaLiquidacao":"BRL","dataOperacao":"2026-07-20","titulos":[%s]}
                """.formatted(CEDENTE, titulos);
    }

    private static String tituloDeCessao(String valorFace) {
        return """
                {"tipoRecebivel":"DUPLICATA_MERCANTIL","numeroDocumento":"D-1",
                 "documentoSacado":"52998224725","valorFace":"%s",
                 "dataVencimento":"2026-09-04"}""".formatted(valorFace);
    }

    @Nested
    @DisplayName("O total do lote precisa caber no que o sistema representa")
    class TotalDoLote {

        private static final String NO_LIMITE = "99999999999999999.99";

        @Test
        @DisplayName("dois titulos no teto estouram a soma e sao recusados com 422")
        void somaQueEstouraEhRecusada() throws Exception {
            // Cada titulo cabe em NUMERIC(19,2); a soma nao. Antes deste PBI o
            // estouro so aparecia no INSERT, virava DataIntegrityViolation e o
            // tratamento generico respondia 409 "a operacao foi alterada por
            // outra requisicao" — mandando o cliente repetir para sempre algo
            // que jamais funcionaria.
            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cessaoCom(tituloDeCessao(NO_LIMITE) + ","
                                    + tituloDeCessao(NO_LIMITE))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString("excede o maximo")));
        }

        @Test
        @DisplayName("a simulacao recusa o mesmo lote, ainda que nao grave nada")
        void simulacaoRecusaOMesmoLote() throws Exception {
            // Simulacao que preve um total impossivel de efetivar e' pior que
            // recusa: o operador so descobriria ao tentar registrar.
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(simulacaoCom(titulo(NO_LIMITE) + "," + titulo(NO_LIMITE))))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("um titulo sozinho no teto continua valido")
        void tetoIsoladoContinuaValido() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(simulacaoCom(titulo(NO_LIMITE))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Teto de corpo da requisicao")
    class TamanhoDoCorpo {

        @Test
        @DisplayName("corpo acima do limite e recusado com 413 antes de ser lido")
        void corpoGrandeDemaisEhRecusado() throws Exception {
            // @Size(max = 500) na lista so roda depois de o Jackson materializar
            // o corpo inteiro: protege o dominio, nao a memoria.
            String recheio = "x".repeat(2 * 1024 * 1024);
            String corpoEnorme = """
                    {"moedaTitulo":"BRL","moedaLiquidacao":"BRL","preenchimento":"%s","titulos":[]}
                    """.formatted(recheio);

            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoEnorme))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.status").value(413));
        }

        @Test
        @DisplayName("um lote cheio, dentro do limite, continua passando")
        void loteCheioPassa() throws Exception {
            String lote = IntStream.range(0, 500)
                    .mapToObj(i -> titulo("1000.00"))
                    .collect(Collectors.joining(","));

            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(simulacaoCom(lote)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("um item acima do teto do lote e recusado com 400")
        void acimaDoTetoDoLote() throws Exception {
            String lote = IntStream.range(0, 501)
                    .mapToObj(i -> titulo("1000.00"))
                    .collect(Collectors.joining(","));

            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(simulacaoCom(lote)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.titulos").exists());
        }
    }

    @Nested
    @DisplayName("Valores monetarios")
    class Monetarios {

        @Test
        @DisplayName("zero e negativo sao recusados com detalhamento por campo")
        void zeroENegativoRecusados() throws Exception {
            for (String invalido : new String[] {"0.00", "-1.00", "-0.01"}) {
                mvc.perform(post("/api/v1/simulacoes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(simulacaoCom(titulo(invalido))))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.campos['titulos[0].valorFace']").exists());
            }
        }

        @Test
        @DisplayName("escala alem de dois decimais e recusada")
        void escalaExcessivaRecusada() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(simulacaoCom(titulo("100.123"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos['titulos[0].valorFace']").exists());
        }

        @Test
        @DisplayName("mais de dezessete digitos inteiros e recusado")
        void inteirosDemaisRecusados() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(simulacaoCom(titulo("999999999999999999.00"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Nenhum campo de texto e ilimitado")
    class TextoLimitado {

        @Test
        @DisplayName("numeroDocumento acima de 50 caracteres e recusado")
        void numeroDocumentoLongo() throws Exception {
            String longo = "D".repeat(51);
            String corpo = cessaoCom("""
                    {"tipoRecebivel":"DUPLICATA_MERCANTIL","numeroDocumento":"%s",
                     "documentoSacado":"52998224725","valorFace":"1000.00",
                     "dataVencimento":"2026-09-04"}""".formatted(longo));

            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos['titulos[0].numeroDocumento']").exists());
        }

        @Test
        @DisplayName("registradoPor acima de 80 caracteres e recusado")
        void registradoPorLongo() throws Exception {
            String corpo = cessaoCom(tituloDeCessao("1000.00"))
                    .replace("\"operador\"", "\"" + "a".repeat(81) + "\"");

            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.registradoPor").exists());
        }

        @Test
        @DisplayName("chaveIdempotencia acima de 64 caracteres e recusada")
        void chaveLonga() throws Exception {
            mvc.perform(post("/api/v1/operacoes/1/liquidacao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"chaveIdempotencia":"%s","liquidadoPor":"op"}
                                    """.formatted("k".repeat(65))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.chaveIdempotencia").exists());
        }

        @Test
        @DisplayName("tipoRecebivel so aceita o alfabeto esperado")
        void tipoRecebivelRestrito() throws Exception {
            String corpo = simulacaoCom("""
                    {"tipoRecebivel":"'; DROP TABLE operacao; --","valorFace":"1000.00",
                     "dataVencimento":"2026-09-04"}""");

            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos['titulos[0].tipoRecebivel']").exists());
        }
    }

    @Nested
    @DisplayName("Todos os campos invalidos aparecem, nao so o primeiro")
    class TodosOsCampos {

        @Test
        @DisplayName("tres campos invalidos produzem tres entradas")
        void listaTodosOsCampos() throws Exception {
            String corpo = """
                    {"documentoCedente":"123","registradoPor":"","moedaTitulo":"real",
                     "moedaLiquidacao":"BRL","dataOperacao":"2026-07-20",
                     "titulos":[%s]}
                    """.formatted(tituloDeCessao("1000.00"));

            String resposta = mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.documentoCedente").exists())
                    .andExpect(jsonPath("$.campos.registradoPor").exists())
                    .andExpect(jsonPath("$.campos.moedaTitulo").exists())
                    .andReturn().getResponse().getContentAsString();

            assertThat(resposta)
                    .as("corrigir um campo por vez, a cada requisicao, e' o que a "
                            + "listagem completa evita")
                    .contains("correlationId");
        }
    }
}
