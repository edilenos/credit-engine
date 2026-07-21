package br.com.srm.creditengine.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import br.com.srm.creditengine.persistencia.repositorio.LiquidacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.OperacaoRepositorio;

/**
 * Extrato de liquidacao (PBI-35).
 *
 * <p><b>Sem {@code @Transactional}:</b> a consulta e' SQL nativo pelo
 * {@code JdbcClient}, e num teste transacional ela enxergaria escritas que o
 * banco ainda nao comitou — o resultado passaria por motivo errado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Extrato de liquidacao")
class ExtratoDeLiquidacaoTest {

    private static final String ROTA = "/api/v1/relatorios/extrato-liquidacao";
    private static final String CEDENTE = "11222333000181";
    private static final String OUTRO_CEDENTE = "44555666000177";

    @Autowired private MockMvc mvc;
    @Autowired private OperacaoRepositorio operacoes;
    @Autowired private LiquidacaoRepositorio liquidacoes;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void prepararCedenteAlternativo() {
        jdbc.update("""
                INSERT INTO cedente (documento, razao_social, ativo) VALUES (?, ?, TRUE)
                ON CONFLICT (documento) DO NOTHING
                """, OUTRO_CEDENTE, "Segundo Cedente Ltda");
    }

    @AfterEach
    void limpar() {
        jdbc.execute("ALTER TABLE evento_auditoria DISABLE TRIGGER tg_evento_auditoria_imutavel");
        jdbc.execute("DELETE FROM evento_auditoria");
        jdbc.execute("ALTER TABLE evento_auditoria ENABLE TRIGGER tg_evento_auditoria_imutavel");
        liquidacoes.deleteAll();
        operacoes.deleteAll();
        jdbc.update("DELETE FROM cedente WHERE documento = ?", OUTRO_CEDENTE);
    }

    private Long registrar(String cedente, String moedaLiquidacao, String valorFace)
            throws Exception {
        String local = mvc.perform(post("/api/v1/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentoCedente":"%s","registradoPor":"ana.mesa",
                                 "moedaTitulo":"BRL","moedaLiquidacao":"%s",
                                 "dataOperacao":"2026-07-20",
                                 "titulos":[{"tipoRecebivel":"DUPLICATA_MERCANTIL",
                                             "numeroDocumento":"D-1","documentoSacado":"52998224725",
                                             "valorFace":"%s","dataVencimento":"2026-09-04"}]}
                                """.formatted(cedente, moedaLiquidacao, valorFace)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        String caminho = URI.create(local).getPath();
        return Long.valueOf(caminho.substring(caminho.lastIndexOf('/') + 1));
    }

    private void liquidar(Long id, String chave) throws Exception {
        mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chaveIdempotencia":"%s","liquidadoPor":"bruno"}
                                """.formatted(chave)))
                .andExpect(status().isCreated());
    }

    private Long registrarELiquidar(String cedente, String moeda, String valor, String chave)
            throws Exception {
        Long id = registrar(cedente, moeda, valor);
        liquidar(id, chave);
        return id;
    }

    @Nested
    @DisplayName("Conteudo")
    class Conteudo {

        @Test
        @DisplayName("traz os dados das quatro tabelas em uma linha")
        void trazDadosCruzados() throws Exception {
            registrarELiquidar(CEDENTE, "USD", "100000.00", "k-1");

            mvc.perform(get(ROTA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conteudo.length()").value(1))
                    .andExpect(jsonPath("$.conteudo[0].documentoCedente").value(CEDENTE))
                    .andExpect(jsonPath("$.conteudo[0].razaoSocialCedente")
                            .value("Cedente Exemplo Ltda"))
                    .andExpect(jsonPath("$.conteudo[0].moedaTitulo").value("BRL"))
                    .andExpect(jsonPath("$.conteudo[0].moedaLiquidacao").value("USD"))
                    .andExpect(jsonPath("$.conteudo[0].valorFaceTotal").value(100000.00))
                    .andExpect(jsonPath("$.conteudo[0].valorPresenteTotal").value(96284.58))
                    .andExpect(jsonPath("$.conteudo[0].valorLiquidado").value(17812.65))
                    .andExpect(jsonPath("$.conteudo[0].cotacaoAplicada").value(0.185))
                    .andExpect(jsonPath("$.conteudo[0].liquidadoPor").value("bruno"));
        }

        @Test
        @DisplayName("o desagio vem calculado no SQL, nao no cliente")
        void desagioVemDoBanco() throws Exception {
            registrarELiquidar(CEDENTE, "BRL", "100000.00", "k-des");

            mvc.perform(get(ROTA))
                    .andExpect(jsonPath("$.conteudo[0].desagioTotal").value(3715.42));
        }

        @Test
        @DisplayName("operacao ainda nao liquidada nao aparece no extrato")
        void pendenteNaoAparece() throws Exception {
            registrar(CEDENTE, "BRL", "100000.00");

            mvc.perform(get(ROTA))
                    .andExpect(jsonPath("$.totalDeItens").value(0))
                    .andExpect(jsonPath("$.conteudo.length()").value(0));
        }
    }

    @Nested
    @DisplayName("Filtros opcionais e combinaveis")
    class Filtros {

        @Test
        @DisplayName("sem filtro, traz tudo")
        void semFiltro() throws Exception {
            registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-a");
            registrarELiquidar(OUTRO_CEDENTE, "USD", "2000.00", "k-b");

            mvc.perform(get(ROTA)).andExpect(jsonPath("$.totalDeItens").value(2));
        }

        @Test
        @DisplayName("por cedente")
        void porCedente() throws Exception {
            registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-a");
            registrarELiquidar(OUTRO_CEDENTE, "BRL", "2000.00", "k-b");

            mvc.perform(get(ROTA).param("documentoCedente", OUTRO_CEDENTE))
                    .andExpect(jsonPath("$.totalDeItens").value(1))
                    .andExpect(jsonPath("$.conteudo[0].documentoCedente").value(OUTRO_CEDENTE));
        }

        @Test
        @DisplayName("por moeda de liquidacao")
        void porMoeda() throws Exception {
            registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-a");
            registrarELiquidar(OUTRO_CEDENTE, "USD", "2000.00", "k-b");

            mvc.perform(get(ROTA).param("moedaLiquidacao", "USD"))
                    .andExpect(jsonPath("$.totalDeItens").value(1))
                    .andExpect(jsonPath("$.conteudo[0].moedaLiquidacao").value("USD"));
        }

        @Test
        @DisplayName("os filtros se combinam: cedente E moeda")
        void filtrosCombinam() throws Exception {
            registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-a");
            registrarELiquidar(CEDENTE, "USD", "2000.00", "k-b");
            registrarELiquidar(OUTRO_CEDENTE, "USD", "3000.00", "k-c");

            mvc.perform(get(ROTA)
                            .param("documentoCedente", CEDENTE)
                            .param("moedaLiquidacao", "USD"))
                    .andExpect(jsonPath("$.totalDeItens").value(1));
        }

        @Test
        @DisplayName("periodo que exclui hoje devolve vazio")
        void periodoQueExclui() throws Exception {
            registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-a");

            mvc.perform(get(ROTA).param("ate", "2020-01-01"))
                    .andExpect(jsonPath("$.totalDeItens").value(0));
        }

        @Test
        @DisplayName("periodo que inclui hoje devolve o registro, contando o dia inteiro")
        void periodoQueInclui() throws Exception {
            registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-a");
            String hoje = java.time.LocalDate.now().toString();

            // 'ate' comparado com a meia-noite excluiria tudo que aconteceu no
            // proprio dia — o filtro precisa alcancar o fim dele.
            mvc.perform(get(ROTA).param("de", hoje).param("ate", hoje))
                    .andExpect(jsonPath("$.totalDeItens").value(1));
        }
    }

    @Nested
    @DisplayName("Paginacao no servidor")
    class Paginacao {

        @Test
        @DisplayName("o total e do filtro, nao da pagina")
        void totalEhDoFiltro() throws Exception {
            for (int i = 0; i < 5; i++) {
                registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-" + i);
            }

            mvc.perform(get(ROTA).param("tamanho", "2"))
                    .andExpect(jsonPath("$.conteudo.length()").value(2))
                    .andExpect(jsonPath("$.totalDeItens").value(5))
                    .andExpect(jsonPath("$.totalDePaginas").value(3))
                    .andExpect(jsonPath("$.temProxima").value(true));
        }

        @Test
        @DisplayName("a ultima pagina nao anuncia proxima")
        void ultimaPagina() throws Exception {
            for (int i = 0; i < 3; i++) {
                registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-" + i);
            }

            mvc.perform(get(ROTA).param("tamanho", "2").param("pagina", "1"))
                    .andExpect(jsonPath("$.conteudo.length()").value(1))
                    .andExpect(jsonPath("$.temProxima").value(false));
        }

        @Test
        @DisplayName("nao existe caminho que devolva a colecao inteira")
        void semRotaSemPaginacao() throws Exception {
            for (int i = 0; i < 5; i++) {
                registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-" + i);
            }

            // Sem teto, este pedido devolveria a tabela toda e a paginacao
            // viraria decoracao.
            mvc.perform(get(ROTA).param("tamanho", "1000000"))
                    .andExpect(jsonPath("$.tamanho").value(FiltroDoExtrato.TAMANHO_MAXIMO));
        }

        @Test
        @DisplayName("paginas nao repetem nem perdem linha quando os instantes empatam")
        void ordenacaoEstavel() throws Exception {
            for (int i = 0; i < 6; i++) {
                registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-" + i);
            }

            var primeira = idsDaPagina(0);
            var segunda = idsDaPagina(1);
            var terceira = idsDaPagina(2);

            assertThat(primeira).doesNotContainAnyElementsOf(segunda);
            assertThat(segunda).doesNotContainAnyElementsOf(terceira);
            assertThat(primeira).hasSize(2);

            // Sem o desempate por l.id no ORDER BY, liquidacoes gravadas no
            // mesmo instante podem trocar de posicao entre consultas: o cliente
            // ve uma linha duas vezes e outra nenhuma.
            assertThat(java.util.stream.Stream.of(primeira, segunda, terceira)
                    .flatMap(java.util.List::stream).distinct().count())
                    .isEqualTo(6);
        }

        private java.util.List<Integer> idsDaPagina(int pagina) throws Exception {
            String corpo = mvc.perform(get(ROTA)
                            .param("tamanho", "2").param("pagina", String.valueOf(pagina)))
                    .andReturn().getResponse().getContentAsString();

            var ids = new java.util.ArrayList<Integer>();
            var matcher = java.util.regex.Pattern
                    .compile("\"liquidacaoId\":(\\d+)").matcher(corpo);
            while (matcher.find()) {
                ids.add(Integer.valueOf(matcher.group(1)));
            }
            return ids;
        }
    }

    @Nested
    @DisplayName("Ordenacao por lista branca")
    class Ordenacao {

        @Test
        @DisplayName("ordena por valor, do maior para o menor")
        void ordenaPorValor() throws Exception {
            registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-a");
            registrarELiquidar(CEDENTE, "BRL", "9000.00", "k-b");

            mvc.perform(get(ROTA)
                            .param("ordenarPor", "VALOR_LIQUIDADO").param("direcao", "DESC"))
                    .andExpect(jsonPath("$.conteudo[0].valorFaceTotal").value(9000.00));
        }

        @Test
        @DisplayName("coluna fora da lista branca devolve 422, nao um padrao silencioso")
        void colunaForaDaListaBranca() throws Exception {
            mvc.perform(get(ROTA).param("ordenarPor", "valor_liquidado; DROP TABLE operacao"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString("Valores aceitos")));
        }

        @Test
        @DisplayName("tentativa de injecao na direcao tambem e recusada")
        void direcaoInvalida() throws Exception {
            mvc.perform(get(ROTA).param("direcao", "ASC; DELETE FROM liquidacao"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("a tabela continua intacta depois das tentativas")
        void tabelaIntacta() throws Exception {
            registrarELiquidar(CEDENTE, "BRL", "1000.00", "k-a");

            mvc.perform(get(ROTA).param("ordenarPor", "x; DROP TABLE liquidacao"));
            mvc.perform(get(ROTA).param("direcao", "x; DROP TABLE liquidacao"));

            mvc.perform(get(ROTA)).andExpect(jsonPath("$.totalDeItens").value(1));
        }
    }

    @Nested
    @DisplayName("A rota nao passa pela camada de negocio")
    class DuasCamadas {

        @Test
        @DisplayName("o controller depende so da consulta, e o pacote nao importa negocio")
        void pacoteIsolado() {
            // A separacao fisica e' o que torna a excecao do paragrafo 3.6
            // legivel. Se algum dia entrar um servico de dominio aqui, este
            // teste falha e a decisao volta a ser discutida em vez de erodir.
            var construtores = ExtratoDeLiquidacaoController.class.getDeclaredConstructors();

            assertThat(construtores).hasSize(1);
            assertThat(construtores[0].getParameterTypes())
                    .as("relatorio le, projeta e pagina — nao ha regra a aplicar, "
                            + "e um servico no meio seria repasse")
                    .containsExactly(ConsultaDeExtrato.class);
        }
    }
}
