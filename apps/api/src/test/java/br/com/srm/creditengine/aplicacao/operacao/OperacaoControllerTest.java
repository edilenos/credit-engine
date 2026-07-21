package br.com.srm.creditengine.aplicacao.operacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.srm.creditengine.persistencia.repositorio.OperacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.RecebivelRepositorio;

/**
 * Registro da cessao (PBI-27).
 *
 * <p><b>Sem {@code @Transactional} de proposito.</b> O criterio central e' o
 * rollback do lote, e um teste transacional o provaria por acidente: o proprio
 * rollback do teste apagaria tudo, inclusive o que tivesse sido gravado
 * indevidamente. Cada requisicao precisa abrir e fechar a propria transacao
 * para a contagem de linhas depois significar alguma coisa.
 *
 * <p>Em troca, a limpeza e' manual — dai o {@code @AfterEach}. O seed nao tem
 * operacoes, entao apagar todas devolve o banco ao estado inicial.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Registro de cessao")
class OperacaoControllerTest {

    private static final String CEDENTE = "11222333000181";

    @Autowired private MockMvc mvc;
    @Autowired private OperacaoRepositorio operacoes;
    @Autowired private RecebivelRepositorio recebiveis;

    @AfterEach
    void limpar() {
        operacoes.deleteAll();
    }

    private static String lote(String moedaLiquidacao, String titulos) {
        return """
                {
                  "documentoCedente": "%s",
                  "moedaTitulo": "BRL",
                  "moedaLiquidacao": "%s",
                  "dataOperacao": "2026-07-20",
                  "titulos": [%s]
                }
                """.formatted(CEDENTE, moedaLiquidacao, titulos);
    }

    private static String titulo(String tipo, String valorFace, String vencimento) {
        return """
                {"tipoRecebivel":"%s","numeroDocumento":"DUP-%s","documentoSacado":"52998224725",
                 "valorFace":"%s","dataVencimento":"%s"}
                """.formatted(tipo, valorFace.replace(".", ""), valorFace, vencimento);
    }

    /** 100.000 duplicata + 50.000 cheque, 46 dias: 96.284,58 + 47.430,91. */
    private static final String LOTE_MISTO = lote("BRL",
            titulo("DUPLICATA_MERCANTIL", "100000.00", "2026-09-04") + ","
                    + titulo("CHEQUE_PRE_DATADO", "50000.00", "2026-09-04"));

    @Nested
    @DisplayName("Registro bem-sucedido")
    class RegistroBemSucedido {

        @Test
        @DisplayName("devolve 201 com Location e o lote precificado")
        void devolve201ComLocation() throws Exception {
            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.matchesPattern(".*/api/v1/operacoes/\\d+$")))
                    .andExpect(jsonPath("$.status").value("PENDENTE"))
                    .andExpect(jsonPath("$.documentoCedente").value(CEDENTE))
                    .andExpect(jsonPath("$.recebiveis.length()").value(2))
                    // A coluna tem DEFAULT now(), mas o Hibernate so le o valor
                    // de volta com @Generated. Sem esta assercao o POST devolvia
                    // criadoEm: null para uma linha que tem timestamp gravado —
                    // e o GET seguinte mostrava o valor certo, entao a
                    // divergencia so aparecia comparando as duas respostas.
                    .andExpect(jsonPath("$.criadoEm").exists());
        }

        @Test
        @DisplayName("o Location devolvido e realmente consultavel")
        void locationEhConsultavel() throws Exception {
            String local = mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getHeader("Location");

            // Este GET roda fora da transacao de escrita: e' o teste que pega
            // LazyInitializationException no mapeamento, que nenhum teste
            // transacional pegaria.
            mvc.perform(get(java.net.URI.create(local).getPath()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recebiveis[0].tipoRecebivel")
                            .value("DUPLICATA_MERCANTIL"));
        }

        @Test
        @DisplayName("o total e a soma dos itens, sem divergencia de centavo")
        void totalEhASomaDosItens() throws Exception {
            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.valorFaceTotal").value(150000.00))
                    .andExpect(jsonPath("$.recebiveis[0].valorPresente").value(96284.58))
                    .andExpect(jsonPath("$.recebiveis[1].valorPresente").value(47430.91))
                    .andExpect(jsonPath("$.valorPresenteTotal").value(143715.49))
                    .andExpect(jsonPath("$.desagioTotal").value(6284.51));
        }

        @Test
        @DisplayName("cada recebivel grava a taxa base e o spread do momento")
        void gravaOsParametrosCongelados() throws Exception {
            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.recebiveis[0].taxaBaseAplicada").value(0.01))
                    .andExpect(jsonPath("$.recebiveis[0].spreadAplicado").value(0.015))
                    .andExpect(jsonPath("$.recebiveis[1].spreadAplicado").value(0.025))
                    .andExpect(jsonPath("$.recebiveis[0].convencaoAplicada").value("ACT_30"))
                    .andExpect(jsonPath("$.recebiveis[0].expoenteAplicado").value(1.5333333333));
        }

        @Test
        @DisplayName("operacao cross-currency grava a cotacao usada")
        void crossCurrencyGravaCotacao() throws Exception {
            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(lote("USD",
                                    titulo("DUPLICATA_MERCANTIL", "100000.00", "2026-09-04"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cotacaoAplicada").value(0.185))
                    .andExpect(jsonPath("$.valorLiquidacao").value(17812.65))
                    .andExpect(jsonPath("$.moedaLiquidacao").value("USD"));
        }

        @Test
        @DisplayName("operacao em moeda unica nao grava cotacao")
        void moedaUnicaNaoGravaCotacao() throws Exception {
            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cotacaoAplicada").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Atomicidade do lote")
    class Atomicidade {

        @Test
        @DisplayName("um titulo invalido no fim do lote nao deixa nenhum entrar")
        void tituloInvalidoDerrubaOLoteInteiro() throws Exception {
            long operacoesAntes = operacoes.count();
            long recebiveisAntes = recebiveis.count();

            // Dois titulos validos e um cheque com prazo alem do limite da
            // Strategy. O invalido e' o ULTIMO de proposito: os dois primeiros
            // ja foram precificados quando a recusa acontece.
            String comInvalidoNoFim = lote("BRL",
                    titulo("DUPLICATA_MERCANTIL", "100000.00", "2026-09-04") + ","
                            + titulo("DUPLICATA_MERCANTIL", "50000.00", "2026-09-04") + ","
                            + titulo("CHEQUE_PRE_DATADO", "10000.00", "2028-01-01"));

            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comInvalidoNoFim))
                    .andExpect(status().isUnprocessableEntity());

            assertThat(operacoes.count())
                    .as("cessao parcial significaria o fundo desembolsando por "
                            + "carteira diferente da contratada")
                    .isEqualTo(operacoesAntes);
            assertThat(recebiveis.count()).isEqualTo(recebiveisAntes);
        }

        @Test
        @DisplayName("tipo inexistente no meio do lote tambem derruba tudo")
        void tipoInexistenteDerrubaOLoteInteiro() throws Exception {
            long operacoesAntes = operacoes.count();

            String comTipoInvalido = lote("BRL",
                    titulo("DUPLICATA_MERCANTIL", "100000.00", "2026-09-04") + ","
                            + titulo("CONTRATO_INEXISTENTE", "50000.00", "2026-09-04"));

            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(comTipoInvalido))
                    .andExpect(status().isUnprocessableEntity());

            assertThat(operacoes.count()).isEqualTo(operacoesAntes);
        }
    }

    @Nested
    @DisplayName("Recusas")
    class Recusas {

        @Test
        @DisplayName("cedente inexistente devolve 404")
        void cedenteInexistente() throws Exception {
            String outroCedente = LOTE_MISTO.replace(CEDENTE, "99999999000199");

            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(outroCedente))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString("99999999000199")));
        }

        @Test
        @DisplayName("lote vazio devolve 422: pedido bem formado, sem sentido de negocio")
        void loteVazio() throws Exception {
            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(lote("BRL", "")))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("documento do sacado fora do formato devolve 400 por campo")
        void documentoDoSacadoInvalido() throws Exception {
            String sacadoInvalido = LOTE_MISTO.replace("52998224725", "123");

            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sacadoInvalido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos['titulos[0].documentoSacado']").exists());
        }

        @Test
        @DisplayName("operacao inexistente devolve 404")
        void operacaoInexistente() throws Exception {
            mvc.perform(get("/api/v1/operacoes/999999"))
                    .andExpect(status().isNotFound());
        }
    }
}
