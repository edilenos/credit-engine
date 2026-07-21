package br.com.srm.creditengine.aplicacao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS para o SPA (PBI-25).
 *
 * <p>Sem cobertura, esta configuracao falharia de um jeito particularmente
 * ruim: os outros testes passam porque MockMvc nao e' um navegador e nao aplica
 * politica de origem. A quebra so apareceria ao abrir a tela.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("CORS")
class ConfiguracaoDeCorsTest {

    private static final String ORIGEM_DO_SPA = "http://localhost:3000";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("preflight da origem do SPA e autorizado")
    void preflightAutorizadoParaOSpa() throws Exception {
        mvc.perform(options("/api/v1/simulacoes")
                        .header("Origin", ORIGEM_DO_SPA)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEM_DO_SPA));
    }

    @Test
    @DisplayName("origem nao listada e recusada")
    void origemDesconhecidaEhRecusada() throws Exception {
        mvc.perform(options("/api/v1/simulacoes")
                        .header("Origin", "http://sitio-malicioso.invalido")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("nao ha curinga na origem autorizada")
    void naoHaCuringa() throws Exception {
        mvc.perform(options("/api/v1/cambio/taxas")
                        .header("Origin", ORIGEM_DO_SPA)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        org.hamcrest.Matchers.not("*")));
    }
}
