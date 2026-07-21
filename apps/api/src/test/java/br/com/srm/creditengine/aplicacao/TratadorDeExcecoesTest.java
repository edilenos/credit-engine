package br.com.srm.creditengine.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;

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
 * Tratamento global de excecoes (PBI-31).
 *
 * <p>Os casos felizes de cada status ja estao nos testes dos endpoints. Aqui se
 * prova o que e' transversal: que todo erro carrega correlacao, que nada vaza, e
 * que as falhas de borda — rota inexistente, JSON quebrado, tipo errado no path
 * — nao caem no 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Tratamento global de excecoes")
class TratadorDeExcecoesTest {

    @Autowired
    private MockMvc mvc;

    @Nested
    @DisplayName("Id de correlacao")
    class Correlacao {

        @Test
        @DisplayName("todo erro carrega o id no corpo e no cabecalho, e sao o mesmo")
        void erroCarregaCorrelacao() throws Exception {
            String corpo = mvc.perform(get("/api/v1/operacoes/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.correlationId").isNotEmpty())
                    .andExpect(header().exists(FiltroDeCorrelacao.CABECALHO))
                    .andReturn().getResponse().getContentAsString();

            String doCabecalho = mvc.perform(get("/api/v1/operacoes/999999"))
                    .andReturn().getResponse().getHeader(FiltroDeCorrelacao.CABECALHO);

            assertThat(corpo).contains("correlationId");
            assertThat(doCabecalho).isNotBlank();
        }

        @Test
        @DisplayName("id enviado pelo cliente e preservado, para rastrear entre servicos")
        void idDoClienteEhPreservado() throws Exception {
            mvc.perform(get("/api/v1/operacoes/999999")
                            .header(FiltroDeCorrelacao.CABECALHO, "rastro-do-cliente-123"))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string(FiltroDeCorrelacao.CABECALHO,
                            "rastro-do-cliente-123"))
                    .andExpect(jsonPath("$.correlationId").value("rastro-do-cliente-123"));
        }

        @Test
        @DisplayName("id malicioso e sanitizado: cabecalho e entrada do usuario")
        void idMaliciosoEhSanitizado() throws Exception {
            String comQuebraDeLinha = "abc\r\nINFO Falso login bem-sucedido";

            String devolvido = mvc.perform(get("/api/v1/operacoes/999999")
                            .header(FiltroDeCorrelacao.CABECALHO, comQuebraDeLinha))
                    .andReturn().getResponse().getHeader(FiltroDeCorrelacao.CABECALHO);

            assertThat(devolvido)
                    .as("quebra de linha no MDC injetaria uma linha falsa no log")
                    .doesNotContain("\n")
                    .doesNotContain("\r")
                    .doesNotContain(" ");
        }

        @Test
        @DisplayName("requisicao bem-sucedida tambem recebe o id")
        void sucessoTambemRecebeCorrelacao() throws Exception {
            mvc.perform(get("/api/v1/cadastros/moedas"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(FiltroDeCorrelacao.CABECALHO));
        }
    }

    @Nested
    @DisplayName("Nada vaza")
    class NadaVaza {

        @Test
        @DisplayName("erro de negocio nao expoe classe, SQL nem tabela")
        void erroDeNegocioNaoVaza() throws Exception {
            String corpo = mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moedaTitulo":"BRL","moedaLiquidacao":"BRL",
                                     "titulos":[{"tipoRecebivel":"NAO_EXISTE","valorFace":"1000.00",
                                                 "dataVencimento":"2027-01-01"}]}
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andReturn().getResponse().getContentAsString();

            assertThat(corpo.toLowerCase(Locale.ROOT))
                    .doesNotContain("exception")
                    .doesNotContain("br.com.srm")
                    .doesNotContain("select ")
                    .doesNotContain("hibernate")
                    .doesNotContain("\tat ");
        }

        @Test
        @DisplayName("JSON quebrado devolve 400 sem citar Jackson nem posicao no fluxo")
        void jsonQuebradoNaoVaza() throws Exception {
            String corpo = mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"moedaTitulo\": \"BRL\", isso nao e json}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.correlationId").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();

            assertThat(corpo.toLowerCase(Locale.ROOT))
                    .as("a mensagem original do Jackson cita classe, campo e offset")
                    .doesNotContain("jackson")
                    .doesNotContain("tools.jackson")
                    .doesNotContain("simularrequest")
                    .doesNotContain("line:");
        }
    }

    @Nested
    @DisplayName("Falhas de borda nao viram 500")
    class FalhasDeBorda {

        @Test
        @DisplayName("rota inexistente devolve 404 com correlacao")
        void rotaInexistente() throws Exception {
            mvc.perform(get("/api/v1/nao-existe"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
        }

        @Test
        @DisplayName("id nao numerico no path devolve 400, nao 500")
        void idNaoNumerico() throws Exception {
            mvc.perform(get("/api/v1/operacoes/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Parametro invalido"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
        }

        @Test
        @DisplayName("metodo nao suportado devolve 405")
        void metodoNaoSuportado() throws Exception {
            mvc.perform(delete("/api/v1/simulacoes"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
        }

        @Test
        @DisplayName("Content-Type errado devolve 415, nao 500")
        void contentTypeErrado() throws Exception {
            // O @ExceptionHandler(Exception.class) e' resolvido antes do
            // tratamento padrao do Spring, entao sem cuidado ele engole as
            // excecoes de borda do framework. Esta requisicao respondia 500 na
            // primeira versao do tratador: falha do cliente relatada como
            // defeito do servidor.
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("nao e json"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
        }

        @Test
        @DisplayName("parametro obrigatorio ausente devolve 400 nomeando o parametro")
        void parametroAusente() throws Exception {
            mvc.perform(get("/api/v1/cambio/taxas").param("origem", "BRL"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString("destino")))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Mapeamento por tipo, nao por lista")
    class MapeamentoPorTipo {

        @Test
        @DisplayName("excecao que herda de RecursoNaoEncontrado devolve 404, sem registro manual")
        void recursoNaoEncontradoPorHeranca() throws Exception {
            // CedenteNaoEncontradoException e OperacaoNaoEncontradaException nao
            // aparecem mais em nenhum @ExceptionHandler: herdam da base e por
            // isso respondem 404. Se caissem no tratamento de ExcecaoDeNegocio
            // virariam 422 silenciosamente.
            mvc.perform(get("/api/v1/operacoes/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso nao encontrado"));

            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"documentoCedente":"99999999000199","registradoPor":"x",
                                     "moedaTitulo":"BRL","moedaLiquidacao":"BRL",
                                     "dataOperacao":"2026-07-20",
                                     "titulos":[{"tipoRecebivel":"DUPLICATA_MERCANTIL",
                                                 "numeroDocumento":"D","documentoSacado":"52998224725",
                                                 "valorFace":"1000.00","dataVencimento":"2026-09-04"}]}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("regra de negocio comum continua 422")
        void regraDeNegocioContinua422() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moedaTitulo":"BRL","moedaLiquidacao":"BRL","titulos":[]}
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Regra de negocio violada"));
        }
    }
}
