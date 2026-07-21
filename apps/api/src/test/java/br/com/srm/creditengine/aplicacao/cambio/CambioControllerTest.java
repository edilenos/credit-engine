package br.com.srm.creditengine.aplicacao.cambio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Contrato HTTP dos endpoints de cambio (PBI-14).
 *
 * <p>Integracao completa, com banco real: os criterios de aceite deste PBI sao
 * afirmacoes sobre status code, e provar isso com o servico mockado testaria o
 * mock, nao o contrato.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Endpoints de cambio")
class CambioControllerTest {

    @Autowired
    private MockMvc mvc;

    private static final String REGISTRO_VALIDO = """
            {"moedaOrigem":"BRL","moedaDestino":"USD","cotacao":"0.190000"}
            """;

    @Nested
    @DisplayName("POST /api/v1/cambio/taxas")
    class Registro {

        @Test
        @DisplayName("devolve 201 com Location apontando para o recurso criado")
        void devolve201ComLocation() throws Exception {
            mvc.perform(post("/api/v1/cambio/taxas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTRO_VALIDO))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.matchesPattern(".*/api/v1/cambio/taxas/\\d+$")))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.moedaOrigem").value("BRL"))
                    .andExpect(jsonPath("$.fonte").value("MANUAL"));
        }

        @Test
        @DisplayName("o Location devolvido e realmente consultavel")
        void locationEhConsultavel() throws Exception {
            String local = mvc.perform(post("/api/v1/cambio/taxas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTRO_VALIDO))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getHeader("Location");

            mvc.perform(get(java.net.URI.create(local)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cotacao").value(0.190000));
        }

        @Test
        @DisplayName("moeda inexistente devolve 404, nao 500")
        void moedaInexistenteDevolve404() throws Exception {
            mvc.perform(post("/api/v1/cambio/taxas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moedaOrigem":"BRL","moedaDestino":"XYZ","cotacao":"1.000000"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("XYZ")));
        }

        @Test
        @DisplayName("cotacao zero devolve 422: bem formada, mas impossivel")
        void cotacaoZeroDevolve422() throws Exception {
            mvc.perform(post("/api/v1/cambio/taxas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moedaOrigem":"BRL","moedaDestino":"USD","cotacao":"0"}
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Regra de negocio violada"));
        }

        @Test
        @DisplayName("cotacao negativa tambem devolve 422")
        void cotacaoNegativaDevolve422() throws Exception {
            mvc.perform(post("/api/v1/cambio/taxas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moedaOrigem":"BRL","moedaDestino":"USD","cotacao":"-1.500000"}
                                    """))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("moeda para ela mesma devolve 422")
        void moedaParaElaMesmaDevolve422() throws Exception {
            mvc.perform(post("/api/v1/cambio/taxas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moedaOrigem":"BRL","moedaDestino":"BRL","cotacao":"1.000000"}
                                    """))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("payload malformado devolve 400 com os campos invalidos listados")
        void payloadMalformadoDevolve400() throws Exception {
            mvc.perform(post("/api/v1/cambio/taxas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moedaOrigem":"brl","cotacao":"1.000000"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.moedaOrigem").exists())
                    .andExpect(jsonPath("$.campos.moedaDestino").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/cambio/taxas")
    class Consulta {

        @Test
        @DisplayName("sem o parametro data, assume agora")
        void semDataAssumeAgora() throws Exception {
            mvc.perform(get("/api/v1/cambio/taxas")
                            .param("origem", "BRL").param("destino", "USD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.moedaOrigem").value("BRL"))
                    .andExpect(jsonPath("$.cotacao").exists());
        }

        @Test
        @DisplayName("com data anterior a qualquer cotacao devolve 404")
        void dataAnteriorDevolve404() throws Exception {
            mvc.perform(get("/api/v1/cambio/taxas")
                            .param("origem", "BRL").param("destino", "USD")
                            .param("data", "2000-01-01T00:00:00Z"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("cotacao serializa como decimal, sem notacao cientifica")
        void cotacaoSerializaComoDecimal() throws Exception {
            mvc.perform(get("/api/v1/cambio/taxas")
                            .param("origem", "BRL").param("destino", "USD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cotacao").value(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("E"))));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/cambio/taxas/historico")
    class Historico {

        @Test
        @DisplayName("responde paginado, com metadados de pagina")
        void respondePaginado() throws Exception {
            mvc.perform(get("/api/v1/cambio/taxas/historico")
                            .param("origem", "BRL").param("destino", "USD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").isNumber())
                    .andExpect(jsonPath("$.size").value(20));
        }

        @Test
        @DisplayName("respeita o tamanho de pagina pedido")
        void respeitaTamanhoPedido() throws Exception {
            mvc.perform(get("/api/v1/cambio/taxas/historico")
                            .param("origem", "BRL").param("destino", "USD")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(5));
        }

        @Test
        @DisplayName("nao aceita pagina ilimitada: size absurdo e limitado pelo teto")
        void naoAceitaPaginaIlimitada() throws Exception {
            mvc.perform(get("/api/v1/cambio/taxas/historico")
                            .param("origem", "BRL").param("destino", "USD")
                            .param("size", "1000000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(
                            org.hamcrest.Matchers.lessThanOrEqualTo(100)));
        }
    }
}
