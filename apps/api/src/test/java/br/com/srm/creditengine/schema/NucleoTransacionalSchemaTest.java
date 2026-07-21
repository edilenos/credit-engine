package br.com.srm.creditengine.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica que as garantias transacionais da V2 sao estruturais — vivem no
 * banco e valem mesmo que a camada de negocio falhe.
 *
 * <p>Em especial as tres defesas contra liquidacao dupla que o paragrafo 3.3 do
 * enunciado cobra, e a imutabilidade da trilha de auditoria.
 *
 * <p>Cada teste roda em transacao revertida. Como o Postgres aborta a transacao
 * ao violar constraint, cada caso executa uma unica operacao invalida por vez.
 */
@SpringBootTest
@Transactional
@DisplayName("V2 — nucleo transacional")
class NucleoTransacionalSchemaTest {

    @Autowired
    private JdbcClient jdbc;

    private long brl;
    private long usd;
    private long cedente;
    private long tipo;
    private long parametro;

    @BeforeEach
    void prepararCadastros() {
        brl = inserirMoeda("BRL", "Real");
        usd = inserirMoeda("USD", "Dolar");
        cedente = jdbc.sql("""
                INSERT INTO cedente (documento, razao_social)
                VALUES ('12345678000199', 'Cedente de Teste') RETURNING id
                """).query(Long.class).single();
        tipo = jdbc.sql("""
                INSERT INTO tipo_recebivel (codigo, nome, spread, periodicidade, convencao_contagem)
                VALUES ('DUPLICATA_MERCANTIL', 'Duplicata', 0.015000, 'MENSAL', 'ACT_30')
                RETURNING id
                """).query(Long.class).single();
        parametro = jdbc.sql("""
                INSERT INTO parametro_precificacao (taxa_base, periodicidade, vigencia_inicio)
                VALUES (0.010000, 'MENSAL', :vigencia) RETURNING id
                """).param("vigencia", OffsetDateTime.now()).query(Long.class).single();
    }

    private long inserirMoeda(String codigo, String nome) {
        return jdbc.sql("""
                INSERT INTO moeda (codigo, nome, escala_padrao)
                VALUES (:codigo, :nome, 2) RETURNING id
                """)
                .param("codigo", codigo).param("nome", nome)
                .query(Long.class).single();
    }

    /** Operacao em moeda unica, valores coerentes. */
    private long inserirOperacao(String face, String presente) {
        return jdbc.sql("""
                INSERT INTO operacao (cedente_id, moeda_titulo_id, moeda_liquidacao_id,
                                      valor_face_total, valor_presente_total, valor_liquidacao, status)
                VALUES (:cedente, :moeda, :moeda,
                        CAST(:face AS NUMERIC), CAST(:presente AS NUMERIC),
                        CAST(:presente AS NUMERIC), 'PENDENTE')
                RETURNING id
                """)
                .param("cedente", cedente).param("moeda", brl)
                .param("face", face).param("presente", presente)
                .query(Long.class).single();
    }

    private void inserirLiquidacao(long operacaoId, String chave) {
        jdbc.sql("""
                INSERT INTO liquidacao (operacao_id, chave_idempotencia, valor_liquidado, liquidado_por)
                VALUES (:operacao, :chave, 96284.52, 'operador.teste')
                """)
                .param("operacao", operacaoId).param("chave", chave)
                .update();
    }

    private long inserirEvento() {
        return jdbc.sql("""
                INSERT INTO evento_auditoria (entidade, entidade_id, tipo_evento, payload, ator)
                VALUES ('OPERACAO', 1, 'OPERACAO_CRIADA', CAST('{"v":1}' AS JSONB), 'operador.teste')
                RETURNING id
                """).query(Long.class).single();
    }

    // ---------------------------------------------------------------- defesas

    @Test
    @DisplayName("duas liquidacoes para a mesma operacao violam unicidade")
    void liquidacaoDuplicadaViolaUnicidade() {
        long operacao = inserirOperacao("100000.00", "96284.52");
        inserirLiquidacao(operacao, "chave-1");

        assertThatThrownBy(() -> inserirLiquidacao(operacao, "chave-2"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_liquidacao_operacao");
    }

    @Test
    @DisplayName("chave de idempotencia repetida viola unicidade")
    void chaveDeIdempotenciaRepetidaViolaUnicidade() {
        long primeira = inserirOperacao("100000.00", "96284.52");
        long segunda = inserirOperacao("200000.00", "190000.00");
        inserirLiquidacao(primeira, "mesma-chave");

        assertThatThrownBy(() -> inserirLiquidacao(segunda, "mesma-chave"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_liquidacao_idempotencia");
    }

    @Test
    @DisplayName("operacao nasce com version zero, base do optimistic locking")
    void operacaoNasceComVersionZero() {
        long operacao = inserirOperacao("100000.00", "96284.52");

        Long version = jdbc.sql("SELECT version FROM operacao WHERE id = :id")
                .param("id", operacao).query(Long.class).single();

        assertThat(version).isZero();
    }

    // -------------------------------------------------------------- validacao

    @Test
    @DisplayName("valor de face negativo e' rejeitado pelo banco")
    void valorDeFaceNegativoEhRejeitado() {
        // Nao da' para violar apenas ck_operacao_valor_face: o valor presente
        // precisa ser > 0 e <= face, entao face negativo derruba ao menos duas
        // constraints ao mesmo tempo. Qual delas o Postgres reporta primeiro
        // nao e' garantido, e afirmar uma especifica tornaria o teste fragil.
        // O que importa aqui e' que o banco recusa, e recusa por regra de valor.
        assertThatThrownBy(() -> inserirOperacao("-1.00", "1.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_operacao_");
    }

    @Test
    @DisplayName("valor presente maior que o de face viola CHECK: desagio nunca e' negativo")
    void valorPresenteMaiorQueFaceViolaCheck() {
        assertThatThrownBy(() -> inserirOperacao("100.00", "101.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_operacao_desagio_nao_negativo");
    }

    @Test
    @DisplayName("status fora do dominio viola CHECK")
    void statusForaDoDominioViolaCheck() {
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO operacao (cedente_id, moeda_titulo_id, moeda_liquidacao_id,
                                      valor_face_total, valor_presente_total, valor_liquidacao, status)
                VALUES (:cedente, :moeda, :moeda, 100.00, 90.00, 90.00, 'EM_ANALISE')
                """)
                .param("cedente", cedente).param("moeda", brl).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_operacao_status");
    }

    @Test
    @DisplayName("operacao cross-currency sem cotacao viola CHECK")
    void crossCurrencySemCotacaoViolaCheck() {
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO operacao (cedente_id, moeda_titulo_id, moeda_liquidacao_id,
                                      valor_face_total, valor_presente_total, valor_liquidacao, status)
                VALUES (:cedente, :titulo, :liquidacao, 100.00, 90.00, 18.00, 'PENDENTE')
                """)
                .param("cedente", cedente).param("titulo", brl).param("liquidacao", usd).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_operacao_cambio_coerente");
    }

    @Test
    @DisplayName("operacao em moeda unica com cotacao tambem viola CHECK")
    void moedaUnicaComCotacaoViolaCheck() {
        long cotacao = jdbc.sql("""
                INSERT INTO taxa_cambio (moeda_origem_id, moeda_destino_id, cotacao, vigencia_inicio, fonte)
                VALUES (:origem, :destino, 5.400000, :vigencia, 'MANUAL') RETURNING id
                """)
                .param("origem", brl).param("destino", usd)
                .param("vigencia", OffsetDateTime.now())
                .query(Long.class).single();

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO operacao (cedente_id, moeda_titulo_id, moeda_liquidacao_id, taxa_cambio_id,
                                      valor_face_total, valor_presente_total, valor_liquidacao, status)
                VALUES (:cedente, :moeda, :moeda, :cotacao, 100.00, 90.00, 90.00, 'PENDENTE')
                """)
                .param("cedente", cedente).param("moeda", brl).param("cotacao", cotacao).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_operacao_cambio_coerente");
    }

    @Test
    @DisplayName("recebivel fora de qualquer operacao viola chave estrangeira")
    void recebivelSemOperacaoViolaFk() {
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO recebivel (operacao_id, tipo_recebivel_id, parametro_precificacao_id,
                                       numero_documento, sacado_documento, valor_face, data_vencimento,
                                       convencao_aplicada, expoente_aplicado, taxa_base_aplicada,
                                       spread_aplicado, valor_presente)
                VALUES (999999, :tipo, :parametro, 'DUP-1', '12345678000199', 100.00, :vencimento,
                        'ACT_30', 1.5333333333, 0.010000, 0.015000, 96.28)
                """)
                .param("tipo", tipo).param("parametro", parametro)
                .param("vencimento", LocalDate.now().plusDays(46)).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_recebivel_operacao");
    }

    // --------------------------------------------------------------- trilha

    @Test
    @DisplayName("evento de auditoria nao pode ser alterado")
    void eventoDeAuditoriaNaoPodeSerAlterado() {
        long evento = inserirEvento();

        assertThatThrownBy(() -> jdbc.sql("UPDATE evento_auditoria SET ator = 'outro' WHERE id = :id")
                .param("id", evento).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("evento de auditoria nao pode ser apagado")
    void eventoDeAuditoriaNaoPodeSerApagado() {
        long evento = inserirEvento();

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM evento_auditoria WHERE id = :id")
                .param("id", evento).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    // ----------------------------------------------------------------- schema

    @Test
    @DisplayName("as sequences de lote incrementam de 50, como o allocationSize do JPA exigira")
    void sequencesDeLoteIncrementamDeCinquenta() {
        var incrementos = jdbc.sql("""
                SELECT sequencename || '=' || increment_by
                  FROM pg_sequences
                 WHERE schemaname = 'public' AND sequencename LIKE 'seq_%'
                 ORDER BY 1
                """).query(String.class).list();

        assertThat(incrementos)
                .as("divergencia entre INCREMENT BY e allocationSize gera colisao de id")
                .containsExactly("seq_evento_auditoria=50", "seq_recebivel=50");
    }

    @Test
    @DisplayName("nenhuma coluna do nucleo transacional usa ponto flutuante")
    void nenhumaColunaUsaPontoFlutuante() {
        var flutuantes = jdbc.sql("""
                SELECT table_name || '.' || column_name
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND data_type IN ('real', 'double precision')
                """).query(String.class).list();

        assertThat(flutuantes).isEmpty();
    }
}
