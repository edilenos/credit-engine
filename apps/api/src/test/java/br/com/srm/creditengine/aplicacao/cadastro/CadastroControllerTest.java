package br.com.srm.creditengine.aplicacao.cadastro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * Dados de referencia para os seletores do painel (PBI-26).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Endpoints de cadastro")
class CadastroControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;

    @Test
    @DisplayName("lista os produtos do seed com spread e convencao")
    void listaOsProdutosDoSeed() throws Exception {
        mvc.perform(get("/api/v1/cadastros/tipos-recebivel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.codigo=='DUPLICATA_MERCANTIL')].spread").value(0.015))
                .andExpect(jsonPath("$[?(@.codigo=='CHEQUE_PRE_DATADO')].spread").value(0.025))
                .andExpect(jsonPath("$[?(@.codigo=='DUPLICATA_MERCANTIL')].convencaoContagem")
                        .value("ACT_30"));
    }

    @Test
    @DisplayName("produto desativado nao e oferecido para nova operacao")
    void produtoDesativadoNaoEhOferecido() throws Exception {
        // Update em massa em vez de setter: desativar produto ainda nao e'
        // operacao do sistema, e criar um setter so para este teste poria API
        // de producao a servico da suite.
        em.createQuery("update TipoRecebivel t set t.ativo = false where t.codigo = :codigo")
                .setParameter("codigo", "CHEQUE_PRE_DATADO")
                .executeUpdate();
        em.clear();

        mvc.perform(get("/api/v1/cadastros/tipos-recebivel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("DUPLICATA_MERCANTIL"));
    }

    @Test
    @DisplayName("lista as moedas com a escala que o cliente usa para formatar")
    void listaAsMoedas() throws Exception {
        mvc.perform(get("/api/v1/cadastros/moedas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.codigo=='BRL')].escalaPadrao").value(2));
    }
}
