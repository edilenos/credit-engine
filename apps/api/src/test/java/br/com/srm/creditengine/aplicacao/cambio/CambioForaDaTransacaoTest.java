package br.com.srm.creditengine.aplicacao.cambio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Endpoints de cambio fora da transacao de leitura.
 *
 * <p><b>Esta classe existe porque {@code CambioControllerTest} e'
 * {@code @Transactional} — e por isso nao podia falhar.</b> Naquele teste a
 * sessao do Hibernate fica aberta a requisicao inteira, entao
 * {@code CotacaoResponse.de()} inicializa as moedas LAZY sem problema. Em
 * producao, com {@code open-in-view: false}, a sessao ja fechou quando o
 * mapeamento roda, e o resultado era {@code 500}.
 *
 * <p>O defeito ficou aberto do PBI-25 ao encerramento da v1.0.0: os testes
 * aprovavam, a CI ficava verde, e so aparecia para quem chamasse o endpoint.
 *
 * <p>Sem {@code @Transactional}, portanto — de proposito, e o proposito e' este.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Cambio fora da transacao")
class CambioForaDaTransacaoTest {

    /** Valor exclusivo desta classe, para a limpeza nao alcancar o seed. */
    private static final String COTACAO_DO_TESTE = "5.410000";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Sem {@code @Transactional} nao ha rollback, e o que este teste grava fica.
     *
     * <p>Isso ja quebrou outro teste: a tabela e' append-only e
     * {@code vigenteEm} devolve a cotacao mais recente, entao a linha criada
     * aqui sombreava o seed e fazia {@code ServicoDeCambioTest} ler 5,41 onde
     * esperava 5,40. Escrever sem transacao exige limpar explicitamente.
     */
    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM taxa_cambio WHERE cotacao = ?::numeric", COTACAO_DO_TESTE);
    }

    @Test
    @DisplayName("cotacao vigente mapeia as moedas sem sessao aberta")
    void vigenteMapeiaMoedas() throws Exception {
        mvc.perform(get("/api/v1/cambio/taxas")
                        .param("origem", "BRL").param("destino", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moedaOrigem").value("BRL"))
                .andExpect(jsonPath("$.moedaDestino").value("USD"))
                .andExpect(jsonPath("$.cotacao").value(0.185));
    }

    @Test
    @DisplayName("historico paginado mapeia as moedas de cada linha")
    void historicoMapeiaMoedas() throws Exception {
        mvc.perform(get("/api/v1/cambio/taxas/historico")
                        .param("origem", "BRL").param("destino", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].moedaOrigem").value("BRL"))
                .andExpect(jsonPath("$.content[0].moedaDestino").value("USD"));
    }

    @Test
    @DisplayName("consulta por id mapeia as moedas")
    void porIdMapeiaMoedas() throws Exception {
        String corpo = mvc.perform(get("/api/v1/cambio/taxas")
                        .param("origem", "BRL").param("destino", "USD"))
                .andReturn().getResponse().getContentAsString();

        String id = corpo.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get("/api/v1/cambio/taxas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moedaOrigem").value("BRL"))
                .andExpect(jsonPath("$.moedaDestino").value("USD"));
    }

    @Test
    @DisplayName("o registro devolve a cotacao criada com as moedas resolvidas")
    void registroMapeiaMoedas() throws Exception {
        // Este caminho ja funcionava: `registrar` carrega as moedas para montar
        // a entidade, entao elas nao sao proxy. Fica coberto para o dia em que
        // alguem trocar a busca por getReferenceById.
        mvc.perform(post("/api/v1/cambio/taxas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moedaOrigem":"USD","moedaDestino":"BRL","cotacao":"%s"}
                                """.formatted(COTACAO_DO_TESTE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moedaOrigem").value("USD"))
                .andExpect(jsonPath("$.moedaDestino").value("BRL"));
    }
}
