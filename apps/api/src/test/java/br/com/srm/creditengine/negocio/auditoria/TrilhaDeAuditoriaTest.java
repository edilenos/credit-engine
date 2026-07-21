package br.com.srm.creditengine.negocio.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import br.com.srm.creditengine.dominio.EntidadeAuditada;
import br.com.srm.creditengine.dominio.TipoEvento;
import br.com.srm.creditengine.persistencia.entidade.EventoAuditoria;
import br.com.srm.creditengine.persistencia.repositorio.EventoAuditoriaRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.LiquidacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.OperacaoRepositorio;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Trilha de auditoria (PBI-30).
 *
 * <p><b>Sem {@code @Transactional}.</b> Dois criterios de aceite so significam
 * alguma coisa contra transacoes reais: que o evento participa da transacao da
 * operacao (rollback leva os dois), e que a tabela e' append-only de verdade. Um
 * teste transacional desfaria tudo no fim e provaria nenhum dos dois.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Trilha de auditoria")
class TrilhaDeAuditoriaTest {

    private static final String CEDENTE = "11222333000181";

    @Autowired private MockMvc mvc;
    @Autowired private EventoAuditoriaRepositorio eventos;
    @Autowired private OperacaoRepositorio operacoes;
    @Autowired private LiquidacaoRepositorio liquidacoes;
    @Autowired private JdbcTemplate jdbc;

    @Autowired private ObjectMapper json;

    /**
     * Le o payload como arvore, nunca como texto cru.
     *
     * <p>{@code jsonb} <b>normaliza</b> o documento: reordena as chaves pela
     * regra interna do Postgres e reescreve o espacamento. O
     * {@code LinkedHashMap} do servico nao sobrevive a ida ao banco, e assercao
     * sobre a string gravada quebra por formatacao, nao por conteudo — foi o que
     * aconteceu na primeira versao destes testes.
     */
    private JsonNode payloadDe(EventoAuditoria evento) {
        return json.readTree(evento.getPayload());
    }

    @AfterEach
    void limpar() {
        // A trilha nao aceita DELETE pelo trigger; desligar e religar e' o unico
        // jeito de limpar entre testes. Em producao ninguem faz isso — e' o
        // ponto da tabela.
        jdbc.execute("ALTER TABLE evento_auditoria DISABLE TRIGGER tg_evento_auditoria_imutavel");
        jdbc.execute("DELETE FROM evento_auditoria");
        jdbc.execute("ALTER TABLE evento_auditoria ENABLE TRIGGER tg_evento_auditoria_imutavel");
        liquidacoes.deleteAll();
        operacoes.deleteAll();
    }

    private String corpoDaCessao(String tipoRecebivel, String vencimento) {
        return """
                {
                  "documentoCedente": "%s",
                  "registradoPor": "ana.mesa",
                  "moedaTitulo": "BRL",
                  "moedaLiquidacao": "USD",
                  "dataOperacao": "2026-07-20",
                  "titulos": [{"tipoRecebivel":"%s","numeroDocumento":"DUP-A",
                               "documentoSacado":"52998224725","valorFace":"100000.00",
                               "dataVencimento":"%s"}]
                }
                """.formatted(CEDENTE, tipoRecebivel, vencimento);
    }

    private Long registrarOperacao() throws Exception {
        String local = mvc.perform(post("/api/v1/operacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaCessao("DUPLICATA_MERCANTIL", "2026-09-04")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        String caminho = URI.create(local).getPath();
        return Long.valueOf(caminho.substring(caminho.lastIndexOf('/') + 1));
    }

    @Nested
    @DisplayName("Eventos gerados")
    class EventosGerados {

        @Test
        @DisplayName("registrar cessao gera evento com ator, timestamp e payload")
        void cessaoGeraEvento() throws Exception {
            Long id = registrarOperacao();

            List<EventoAuditoria> daOperacao =
                    eventos.findByEntidadeAndEntidadeIdOrderByIdAsc(EntidadeAuditada.OPERACAO, id);

            assertThat(daOperacao).hasSize(1);
            EventoAuditoria evento = daOperacao.getFirst();

            assertThat(evento.getTipoEvento()).isEqualTo(TipoEvento.OPERACAO_REGISTRADA);
            assertThat(evento.getAtor())
                    .as("o ator vem do pedido, nao de uma constante")
                    .isEqualTo("ana.mesa");
            assertThat(evento.getOcorridoEm()).isNotNull();

            JsonNode payload = payloadDe(evento);
            assertThat(payload.get("operacaoId").asLong()).isEqualTo(id);
            assertThat(payload.get("cedente").asString()).isEqualTo(CEDENTE);
            assertThat(payload.get("valorPresenteTotal").asString()).isEqualTo("96284.58");
            assertThat(payload.get("quantidadeDeRecebiveis").asInt()).isEqualTo(1);
            assertThat(payload.get("status").asString()).isEqualTo("PENDENTE");
        }

        @Test
        @DisplayName("liquidar gera o segundo evento, e a linha do tempo fica completa")
        void liquidacaoGeraSegundoEvento() throws Exception {
            Long id = registrarOperacao();

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"chaveIdempotencia":"k-audit","liquidadoPor":"bruno.backoffice"}
                                    """))
                    .andExpect(status().isCreated());

            assertThat(eventos.count()).isEqualTo(2);

            EventoAuditoria daLiquidacao = eventos.findAll().stream()
                    .filter(e -> e.getEntidade() == EntidadeAuditada.LIQUIDACAO)
                    .findFirst().orElseThrow();

            assertThat(daLiquidacao.getTipoEvento()).isEqualTo(TipoEvento.OPERACAO_LIQUIDADA);
            assertThat(daLiquidacao.getAtor())
                    .as("quem registrou e quem liquidou sao pessoas diferentes, "
                            + "e a trilha precisa distinguir")
                    .isEqualTo("bruno.backoffice");

            JsonNode payload = payloadDe(daLiquidacao);
            assertThat(payload.get("operacaoId").asLong()).isEqualTo(id);
            assertThat(payload.get("valorLiquidado").asString()).isEqualTo("17812.65");
            assertThat(payload.get("cotacaoAplicada").asString()).isEqualTo("0.185000");
            assertThat(payload.get("chaveIdempotencia").asString()).isEqualTo("k-audit");
        }

        @Test
        @DisplayName("retry idempotente nao gera evento novo")
        void retryNaoGeraEventoNovo() throws Exception {
            Long id = registrarOperacao();
            String pedido = """
                    {"chaveIdempotencia":"k-unica","liquidadoPor":"bruno.backoffice"}
                    """;

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON).content(pedido))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON).content(pedido))
                    .andExpect(status().isOk());

            assertThat(eventos.count())
                    .as("replay devolve o original sem escrever; se gerasse evento, "
                            + "a trilha mostraria duas liquidacoes que nao houve")
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Participa da transacao da operacao")
    class MesmaTransacao {

        @Test
        @DisplayName("cessao que falha nao deixa evento orfao")
        void cessaoQueFalhaNaoDeixaEvento() throws Exception {
            long antes = eventos.count();

            // Cheque com prazo alem do limite: a Strategy recusa depois de o
            // fluxo ja ter comecado, e a transacao inteira volta.
            mvc.perform(post("/api/v1/operacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoDaCessao("CHEQUE_PRE_DATADO", "2028-01-01")))
                    .andExpect(status().isUnprocessableEntity());

            assertThat(eventos.count())
                    .as("evento de cessao que nao existiu faria a trilha mentir — "
                            + "pior que trilha faltando")
                    .isEqualTo(antes);
            assertThat(operacoes.count()).isZero();
        }

        @Test
        @DisplayName("liquidacao recusada nao deixa evento")
        void liquidacaoRecusadaNaoDeixaEvento() throws Exception {
            Long id = registrarOperacao();

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"chaveIdempotencia":"k-1","liquidadoPor":"bruno"}
                                    """))
                    .andExpect(status().isCreated());

            long depoisDaPrimeira = eventos.count();

            mvc.perform(post("/api/v1/operacoes/{id}/liquidacao", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"chaveIdempotencia":"k-2","liquidadoPor":"bruno"}
                                    """))
                    .andExpect(status().isConflict());

            assertThat(eventos.count()).isEqualTo(depoisDaPrimeira);
        }
    }

    @Nested
    @DisplayName("Append-only e garantia do banco")
    class AppendOnly {

        @Test
        @DisplayName("UPDATE na trilha e recusado pelo banco")
        void updateEhRecusado() throws Exception {
            registrarOperacao();

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE evento_auditoria SET ator = 'outro' WHERE id = "
                            + "(SELECT MIN(id) FROM evento_auditoria)"))
                    .as("trilha que pode ser alterada nao e' trilha")
                    .hasMessageContaining("append-only");
        }

        @Test
        @DisplayName("DELETE na trilha e recusado pelo banco")
        void deleteEhRecusado() throws Exception {
            registrarOperacao();

            assertThatThrownBy(() -> jdbc.update("DELETE FROM evento_auditoria"))
                    .as("a garantia e estrutural, nao convencao documentada")
                    .hasMessageContaining("append-only");
        }
    }

    @Nested
    @DisplayName("O payload e escolhido, nao despejado")
    class PayloadSelecionado {

        @Test
        @DisplayName("nao carrega o lote inteiro nem campos de entidade nao pedidos")
        void naoDespejaEntidade() throws Exception {
            Long id = registrarOperacao();

            String payload = eventos
                    .findByEntidadeAndEntidadeIdOrderByIdAsc(EntidadeAuditada.OPERACAO, id)
                    .getFirst().getPayload();

            assertThat(payload)
                    .as("o detalhe por titulo ja mora em `recebivel`, com os parametros "
                            + "congelados; duplicar aqui so aumenta o que pode vazar")
                    .doesNotContain("documentoSacado")
                    .doesNotContain("numeroDocumento")
                    .doesNotContain("expoenteAplicado")
                    .doesNotContain("version");
        }

        @Test
        @DisplayName("valores monetarios sao string, nao numero JSON")
        void monetariosSaoString() throws Exception {
            Long id = registrarOperacao();

            JsonNode payload = payloadDe(eventos
                    .findByEntidadeAndEntidadeIdOrderByIdAsc(EntidadeAuditada.OPERACAO, id)
                    .getFirst());

            assertThat(payload.get("valorFaceTotal").isString())
                    .as("numero JSON seria relido como ponto flutuante, perdendo "
                            + "exatamente a precisao que a trilha existe para registrar")
                    .isTrue();
            assertThat(payload.get("valorFaceTotal").asString())
                    .as("a escala tambem precisa sobreviver: 100000.0 nao e 100000.00")
                    .isEqualTo("100000.00");
        }

        @Test
        @DisplayName("a coluna e jsonb de verdade: da para consultar por campo")
        void payloadEhConsultavelPorCampo() throws Exception {
            Long id = registrarOperacao();

            String cedente = jdbc.queryForObject(
                    "SELECT payload ->> 'cedente' FROM evento_auditoria "
                            + "WHERE entidade = 'OPERACAO' AND entidade_id = ?",
                    String.class, id);

            assertThat(cedente)
                    .as("jsonb e nao text: a diferenca entre trilha auditavel e log "
                            + "que so serve para ler com o olho")
                    .isEqualTo(CEDENTE);
        }
    }
}
