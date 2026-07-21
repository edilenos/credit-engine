package br.com.srm.creditengine.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica que as garantias de integridade da V1 vivem no banco, e nao apenas
 * no codigo da aplicacao.
 *
 * <p>Os testes usam JDBC puro de proposito: nesta etapa ainda nao existem
 * entidades JPA, e o alvo aqui e' o schema em si. Cada teste roda em transacao
 * revertida ao final. Como o Postgres aborta a transacao ao violar constraint,
 * cada caso executa uma unica operacao invalida e nao toca no banco depois.
 */
@SpringBootTest
@Transactional
@DisplayName("V1 — cadastros basicos")
class CadastrosBasicosSchemaTest {

    @Autowired
    private JdbcClient jdbc;

    private long inserirMoeda(String codigo, String nome, int escala) {
        return jdbc.sql("""
                INSERT INTO moeda (codigo, nome, escala_padrao)
                VALUES (:codigo, :nome, :escala)
                RETURNING id
                """)
                .param("codigo", codigo)
                .param("nome", nome)
                .param("escala", escala)
                .query(Long.class)
                .single();
    }

    private void inserirTipo(String codigo, String spread, String periodicidade, String convencao) {
        jdbc.sql("""
                INSERT INTO tipo_recebivel (codigo, nome, spread, periodicidade, convencao_contagem)
                VALUES (:codigo, :nome, CAST(:spread AS NUMERIC), :periodicidade, :convencao)
                """)
                .param("codigo", codigo)
                .param("nome", codigo)
                .param("spread", spread)
                .param("periodicidade", periodicidade)
                .param("convencao", convencao)
                .update();
    }

    private void inserirCotacao(long origem, long destino, String cotacao) {
        jdbc.sql("""
                INSERT INTO taxa_cambio (moeda_origem_id, moeda_destino_id, cotacao, vigencia_inicio, fonte)
                VALUES (:origem, :destino, CAST(:cotacao AS NUMERIC), :vigencia, 'MANUAL')
                """)
                .param("origem", origem)
                .param("destino", destino)
                .param("cotacao", cotacao)
                .param("vigencia", OffsetDateTime.now())
                .update();
    }

    @Test
    @DisplayName("codigo de moeda duplicado viola unicidade")
    void codigoDeMoedaDuplicadoViolaUnicidade() {
        inserirMoeda("BRL", "Real", 2);

        assertThatThrownBy(() -> inserirMoeda("BRL", "Real duplicado", 2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_moeda_codigo");
    }

    @Test
    @DisplayName("escala de moeda fora da faixa viola CHECK")
    void escalaDeMoedaForaDaFaixaViolaCheck() {
        assertThatThrownBy(() -> inserirMoeda("XXX", "Invalida", 9))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_moeda_escala");
    }

    @Test
    @DisplayName("spread negativo viola CHECK")
    void spreadNegativoViolaCheck() {
        assertThatThrownBy(() -> inserirTipo("TESTE_SPREAD", "-0.010000", "MENSAL", "ACT_30"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_tipo_recebivel_spread");
    }

    @Test
    @DisplayName("periodicidade fora do dominio viola CHECK")
    void periodicidadeForaDoDominioViolaCheck() {
        assertThatThrownBy(() -> inserirTipo("TESTE_PERIOD", "0.015000", "SEMANAL", "ACT_30"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_tipo_recebivel_periodicidade");
    }

    @Test
    @DisplayName("convencao de contagem fora do dominio viola CHECK")
    void convencaoForaDoDominioViolaCheck() {
        assertThatThrownBy(() -> inserirTipo("TESTE_CONV", "0.015000", "MENSAL", "ACT_999"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_tipo_recebivel_convencao");
    }

    @Test
    @DisplayName("cotacao zero nao e' cotacao: viola CHECK")
    void cotacaoZeroViolaCheck() {
        long brl = inserirMoeda("BRL", "Real", 2);
        long usd = inserirMoeda("USD", "Dolar", 2);

        assertThatThrownBy(() -> inserirCotacao(brl, usd, "0.000000"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_taxa_cambio_cotacao");
    }

    @Test
    @DisplayName("cotacao de uma moeda para ela mesma viola CHECK")
    void cotacaoComParIgualViolaCheck() {
        long brl = inserirMoeda("BRL", "Real", 2);

        assertThatThrownBy(() -> inserirCotacao(brl, brl, "1.000000"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_taxa_cambio_par_distinto");
    }

    @Test
    @DisplayName("nenhuma coluna do schema usa ponto flutuante")
    void nenhumaColunaUsaPontoFlutuante() {
        List<String> flutuantes = jdbc.sql("""
                SELECT table_name || '.' || column_name || ' (' || data_type || ')'
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND data_type IN ('real', 'double precision')
                 ORDER BY 1
                """)
                .query(String.class)
                .list();

        assertThat(flutuantes)
                .as("precisao decimal e' criterio de avaliacao: dinheiro e taxa nunca em ponto flutuante")
                .isEmpty();
    }

    @Test
    @DisplayName("as seis tabelas de cadastro existem")
    void schemaTemAsTabelasDeCadastro() {
        List<String> tabelas = jdbc.sql("""
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_type = 'BASE TABLE'
                 ORDER BY 1
                """)
                .query(String.class)
                .list();

        assertThat(tabelas).contains(
                "cedente", "feriado", "moeda",
                "parametro_precificacao", "taxa_cambio", "tipo_recebivel");
    }
}
