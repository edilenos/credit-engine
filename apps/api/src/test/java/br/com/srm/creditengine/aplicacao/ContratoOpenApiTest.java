package br.com.srm.creditengine.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Contrato OpenAPI (PBI-33).
 *
 * <p>O contrato e' gerado, e por isso quebra em silencio: uma anotacao que nao
 * pega, ou um schema que o springdoc sobrescreve, continuam respondendo 200 com
 * um documento pior. Foi o que aconteceu ao integrar — o {@code ProblemDetail}
 * registrado no bean {@code OpenAPI} era descartado, e as 51 respostas de erro
 * apontavam para um schema inexistente. O contrato seguia servindo normalmente.
 *
 * <p>Dai o teste de referencias orfas: e' a checagem que pega isso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Contrato OpenAPI")
class ContratoOpenApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private JsonNode contrato() throws Exception {
        String corpo = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    @Nested
    @DisplayName("Disponibilidade")
    class Disponibilidade {

        @Test
        @DisplayName("o contrato e o Swagger UI respondem")
        void contratoEUiRespondem() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("todos os endpoints da API aparecem no contrato")
        void todosOsEndpointsAparecem() throws Exception {
            JsonNode caminhos = contrato().get("paths");

            assertThat(caminhos.propertyNames())
                    .contains("/api/v1/simulacoes",
                            "/api/v1/operacoes",
                            "/api/v1/operacoes/{id}",
                            "/api/v1/operacoes/{id}/liquidacao",
                            "/api/v1/cadastros/tipos-recebivel",
                            "/api/v1/cadastros/moedas",
                            "/api/v1/cambio/taxas");
        }
    }

    @Nested
    @DisplayName("Erros documentados, nao so os sucessos")
    class ErrosDocumentados {

        @Test
        @DisplayName("escrita documenta 400, 404, 409, 413, 422 e 500")
        void escritaDocumentaOsErros() throws Exception {
            JsonNode simulacoes = contrato().get("paths").get("/api/v1/simulacoes").get("post");

            assertThat(simulacoes.get("responses").propertyNames())
                    .contains("200", "400", "404", "409", "413", "422", "500");
        }

        @Test
        @DisplayName("leitura nao documenta 409 nem 422: nao ha o que conflitar")
        void leituraNaoDocumentaConflito() throws Exception {
            JsonNode leitura = contrato().get("paths").get("/api/v1/operacoes/{id}").get("get");

            assertThat(leitura.get("responses").propertyNames())
                    .contains("200", "404")
                    .doesNotContain("409", "413", "422");
        }

        @Test
        @DisplayName("nenhuma referencia de schema aponta para o vazio")
        void nenhumaReferenciaOrfa() throws Exception {
            JsonNode contrato = contrato();
            JsonNode schemas = contrato.get("components").get("schemas");

            List<String> orfas = new ArrayList<>();
            int total = 0;

            for (JsonNode caminho : contrato.get("paths")) {
                for (JsonNode operacao : caminho) {
                    JsonNode respostas = operacao.get("responses");
                    if (respostas == null) continue;

                    for (JsonNode resposta : respostas) {
                        JsonNode ref = resposta.at("/content/application~1problem+json/schema/$ref");
                        if (ref.isMissingNode()) continue;

                        total++;
                        String nome = ref.asString().substring(ref.asString().lastIndexOf('/') + 1);
                        if (schemas.get(nome) == null) {
                            orfas.add(nome);
                        }
                    }
                }
            }

            assertThat(total)
                    .as("se nenhuma resposta de erro tem schema, o customizer parou de rodar")
                    .isGreaterThan(20);
            assertThat(orfas)
                    .as("$ref para schema inexistente e contrato quebrado que continua "
                            + "respondendo 200 — so aparece em quem tenta usar")
                    .isEmpty();
        }

        @Test
        @DisplayName("o ProblemDetail documenta o correlationId")
        void problemDetailDocumentaCorrelacao() throws Exception {
            JsonNode problema = contrato().at("/components/schemas/ProblemDetail/properties");

            assertThat(problema.propertyNames())
                    .contains("type", "title", "status", "detail", "instance",
                            "correlationId", "campos");
        }
    }

    @Nested
    @DisplayName("Schemas descrevem o dominio")
    class SchemasDescrevemODominio {

        @Test
        @DisplayName("campo monetario declara a escala de duas casas")
        void monetarioDeclaraEscala() throws Exception {
            JsonNode valorFace = contrato()
                    .at("/components/schemas/TituloRequest/properties/valorFace");

            assertThat(valorFace.get("multipleOf").asDouble())
                    .as("o example sozinho nao serve: JSON nao preserva zero a direita, "
                            + "entao 100000.00 vira 100000 e a escala some")
                    .isEqualTo(0.01);
            assertThat(valorFace.get("minimum").asDouble()).isEqualTo(0.01);
        }

        @Test
        @DisplayName("os exemplos usam dado real do dominio, nao 'string'")
        void exemplosSaoRealistas() throws Exception {
            JsonNode titulo = contrato().at("/components/schemas/TituloRequest/properties");

            assertThat(titulo.at("/tipoRecebivel/example").asString())
                    .isEqualTo("DUPLICATA_MERCANTIL");
            assertThat(titulo.at("/dataVencimento/example").asString())
                    .isEqualTo("2026-09-04");
        }

        @Test
        @DisplayName("as operacoes estao agrupadas por area")
        void operacoesAgrupadas() throws Exception {
            List<String> tags = new ArrayList<>();
            contrato().get("tags").forEach(tag -> tags.add(tag.get("name").asString()));

            assertThat(tags).contains("Simulacao", "Operacoes", "Cadastros", "Cambio");
        }
    }
}
