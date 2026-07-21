package br.com.srm.creditengine.aplicacao.simulacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import br.com.srm.creditengine.persistencia.repositorio.LiquidacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.OperacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.RecebivelRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.TaxaCambioRepositorio;

/**
 * Contrato HTTP da simulacao (PBI-24).
 *
 * <p><b>Sem {@code @Transactional} de proposito.</b> O criterio de aceite
 * central e' "nenhum registro e' persistido", e um teste transacional provaria
 * isso por acidente: o rollback do proprio teste apagaria qualquer escrita. Sem
 * ele, cada requisicao abre e fecha a propria transacao, e a contagem de linhas
 * depois vale como evidencia.
 *
 * <p>Valores esperados calculados fora da aplicacao, sobre o seed da {@code V3}:
 * taxa base 1% a.m., duplicata 1,5% a.m., cheque 2,5% a.m., BRL para USD a
 * 0,185.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Endpoint de simulacao")
class SimulacaoControllerTest {

    /** 20/07/2026 a 04/09/2026: 46 dias corridos, expoente 1,5333 em ACT/30. */
    private static final String LOTE_MISTO = """
            {
              "moedaTitulo": "BRL",
              "moedaLiquidacao": "BRL",
              "dataOperacao": "2026-07-20",
              "titulos": [
                {"tipoRecebivel":"DUPLICATA_MERCANTIL","valorFace":"100000.00","dataVencimento":"2026-09-04"},
                {"tipoRecebivel":"CHEQUE_PRE_DATADO","valorFace":"50000.00","dataVencimento":"2026-09-04"}
              ]
            }
            """;

    @Autowired
    private MockMvc mvc;

    private static String loteDe(String tipo, String valorFace, String moedaLiquidacao) {
        return """
                {
                  "moedaTitulo": "BRL",
                  "moedaLiquidacao": "%s",
                  "dataOperacao": "2026-07-20",
                  "titulos": [
                    {"tipoRecebivel":"%s","valorFace":"%s","dataVencimento":"2026-09-04"}
                  ]
                }
                """.formatted(moedaLiquidacao, tipo, valorFace);
    }

    @Nested
    @DisplayName("Precificacao do lote")
    class Precificacao {

        @Test
        @DisplayName("devolve item a item mais o total, com os parametros aplicados")
        void devolveItensETotais() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens.length()").value(2))
                    .andExpect(jsonPath("$.valorFaceTotal").value(150000.00))
                    .andExpect(jsonPath("$.valorPresenteTotal").value(143715.49))
                    .andExpect(jsonPath("$.desagioTotal").value(6284.51))
                    .andExpect(jsonPath("$.valorLiquidacao").value(143715.49));
        }

        @Test
        @DisplayName("cada item expoe o spread da sua Strategy, nao um spread do lote")
        void cadaItemExpoeOProprioSpread() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens[0].spreadAplicado").value(0.015))
                    .andExpect(jsonPath("$.itens[0].valorPresente").value(96284.58))
                    .andExpect(jsonPath("$.itens[1].spreadAplicado").value(0.025))
                    .andExpect(jsonPath("$.itens[1].valorPresente").value(47430.91));
        }

        @Test
        @DisplayName("expoe taxa base, taxa total, convencao e expoente para conferencia")
        void expoeOsParametrosDeCalculo() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens[0].taxaBaseAplicada").value(0.01))
                    .andExpect(jsonPath("$.itens[0].taxaTotal")
                            .value(0.025))
                    .andExpect(jsonPath("$.itens[0].convencaoAplicada").value("ACT_30"))
                    .andExpect(jsonPath("$.itens[0].expoenteAplicado").value(1.5333333333))
                    .andExpect(jsonPath("$.itens[0].desagio").value(3715.42));
        }

        @Test
        @DisplayName("o indice do item espelha a posicao no lote enviado")
        void indiceEspelhaAOrdemDoPedido() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens[0].indice").value(0))
                    .andExpect(jsonPath("$.itens[1].indice").value(1))
                    .andExpect(jsonPath("$.itens[0].valorFace").value(100000.00))
                    .andExpect(jsonPath("$.itens[1].valorFace").value(50000.00));
        }

        @Test
        @DisplayName("data da operacao ausente assume hoje e volta explicita na resposta")
        void dataAusenteAssumeHoje() throws Exception {
            String semData = """
                    {
                      "moedaTitulo":"BRL","moedaLiquidacao":"BRL",
                      "titulos":[{"tipoRecebivel":"DUPLICATA_MERCANTIL",
                                  "valorFace":"1000.00","dataVencimento":"2027-01-01"}]
                    }
                    """;

            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(semData))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dataOperacao")
                            .value(java.time.LocalDate.now().toString()));
        }
    }

    @Nested
    @DisplayName("Cambio")
    class Cambio {

        @Test
        @DisplayName("cross-currency converte o total e devolve a cotacao usada")
        void crossCurrencyDevolveCotacao() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO.replace("\"moedaLiquidacao\": \"BRL\"",
                                    "\"moedaLiquidacao\": \"USD\"")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.crossCurrency").value(true))
                    .andExpect(jsonPath("$.cotacaoAplicada").value(0.185))
                    .andExpect(jsonPath("$.valorPresenteTotal")
                            .value(143715.49))
                    .andExpect(jsonPath("$.valorLiquidacao")
                            .value(26587.37));
        }

        @Test
        @DisplayName("moeda unica nao devolve cotacao, e liquidacao e o proprio presente")
        void moedaUnicaNaoTemCotacao() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.crossCurrency").value(false))
                    .andExpect(jsonPath("$.cotacaoAplicada").doesNotExist())
                    .andExpect(jsonPath("$.valorLiquidacao").value(143715.49));
        }
    }

    @Nested
    @DisplayName("Nao cria recurso")
    class NaoCriaRecurso {

        @Autowired private OperacaoRepositorio operacoes;
        @Autowired private RecebivelRepositorio recebiveis;
        @Autowired private LiquidacaoRepositorio liquidacoes;
        @Autowired private TaxaCambioRepositorio cotacoes;

        @Test
        @DisplayName("responde 200 sem Location: nenhum recurso passou a existir")
        void respondeDuzentosSemLocation() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Location"));
        }

        @Test
        @DisplayName("nenhuma linha e gravada, inclusive na trilha de cambio")
        void nenhumaLinhaEhGravada() throws Exception {
            long operacoesAntes = operacoes.count();
            long recebiveisAntes = recebiveis.count();
            long liquidacoesAntes = liquidacoes.count();
            long cotacoesAntes = cotacoes.count();

            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOTE_MISTO.replace("\"moedaLiquidacao\": \"BRL\"",
                                    "\"moedaLiquidacao\": \"USD\"")))
                    .andExpect(status().isOk());

            assertThat(operacoes.count()).isEqualTo(operacoesAntes);
            assertThat(recebiveis.count()).isEqualTo(recebiveisAntes);
            assertThat(liquidacoes.count()).isEqualTo(liquidacoesAntes);
            assertThat(cotacoes.count())
                    .as("consultar a cotacao para converter nao pode gravar cotacao")
                    .isEqualTo(cotacoesAntes);
        }
    }

    @Nested
    @DisplayName("Payload malformado devolve 400 com detalhamento por campo")
    class PayloadMalformado {

        @Test
        @DisplayName("valor de face zero")
        void valorDeFaceZero() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loteDe("DUPLICATA_MERCANTIL", "0.00", "BRL")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos['titulos[0].valorFace']").exists());
        }

        @Test
        @DisplayName("moeda fora do formato ISO 4217")
        void moedaForaDoFormato() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loteDe("DUPLICATA_MERCANTIL", "1000.00", "dolar")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.moedaLiquidacao").exists());
        }

        @Test
        @DisplayName("lote acima do teto e recusado antes de qualquer calculo")
        void loteAcimaDoTeto() throws Exception {
            String excessivo = IntStream.rangeClosed(0, SimularRequest.LOTE_MAXIMO)
                    .mapToObj(i -> """
                            {"tipoRecebivel":"DUPLICATA_MERCANTIL","valorFace":"1000.00",\
                            "dataVencimento":"2026-09-04"}""")
                    .collect(Collectors.joining(",",
                            """
                            {"moedaTitulo":"BRL","moedaLiquidacao":"BRL",\
                            "dataOperacao":"2026-07-20","titulos":[""", "]}"));

            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(excessivo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.titulos").exists());
        }
    }

    @Nested
    @DisplayName("Payload valido que viola regra devolve 422")
    class RegraViolada {

        @Test
        @DisplayName("lote vazio e recusa do dominio, nao do formato")
        void loteVazio() throws Exception {
            // 422 e nao 400: a lista existe e esta bem formada. "Comprar nada"
            // e' pedido sintaticamente valido e sem sentido de negocio — mesma
            // resposta que o registro de cessao da (PBI-27).
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moedaTitulo":"BRL","moedaLiquidacao":"BRL","titulos":[]}
                                    """))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("tipo de recebivel nao cadastrado")
        void tipoNaoCadastrado() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loteDe("CONTRATO_INEXISTENTE", "1000.00", "BRL")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString("CONTRATO_INEXISTENTE")));
        }

        @Test
        @DisplayName("moeda nao cadastrada, mesmo em operacao de moeda unica")
        void moedaNaoCadastradaEmMoedaUnica() throws Exception {
            String emMoedaInexistente = loteDe("DUPLICATA_MERCANTIL", "1000.00", "XXX")
                    .replace("\"moedaTitulo\": \"BRL\"", "\"moedaTitulo\": \"XXX\"");

            // Sem a checagem explicita de moeda no servico este caso passaria:
            // em moeda unica o cambio nunca e consultado, entao ninguem
            // repararia que "XXX" nao existe ate a tentativa de efetivar.
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(emMoedaInexistente))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString("XXX")));
        }

        @Test
        @DisplayName("vencimento anterior a data da operacao")
        void vencimentoNoPassado() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loteDe("DUPLICATA_MERCANTIL", "1000.00", "BRL")
                                    .replace("2026-09-04", "2026-07-19")))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("cheque com prazo alem do limite da Strategy")
        void chequeComPrazoInviavel() throws Exception {
            mvc.perform(post("/api/v1/simulacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loteDe("CHEQUE_PRE_DATADO", "1000.00", "BRL")
                                    .replace("2026-09-04", "2028-01-01")))
                    .andExpect(status().isUnprocessableEntity());
        }
    }
}
