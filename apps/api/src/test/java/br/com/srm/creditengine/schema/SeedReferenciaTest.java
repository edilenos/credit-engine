package br.com.srm.creditengine.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Verifica o seed da V3: o sistema recem-subido precisa permitir simular sem
 * cadastro manual, e os parametros semeados precisam bater com o enunciado.
 *
 * <p>Sem {@code @Transactional}: estes testes so leem, e o alvo e' o estado que
 * o Flyway deixou no banco.
 */
@SpringBootTest
@DisplayName("V3 — seed de referencia")
class SeedReferenciaTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("BRL e USD estao cadastradas, com escala 2")
    void moedasDoEnunciadoEstaoCadastradas() {
        List<String> moedas = jdbc.sql(
                "SELECT codigo || ':' || escala_padrao FROM moeda ORDER BY codigo")
                .query(String.class).list();

        assertThat(moedas).containsExactly("BRL:2", "USD:2");
    }

    @Test
    @DisplayName("spreads conferem com os valores do enunciado")
    void spreadsConferemComOEnunciado() {
        assertThat(spreadDe("DUPLICATA_MERCANTIL"))
                .as("Duplicata Mercantil: 1,5%% a.m.")
                .isEqualByComparingTo(new BigDecimal("0.015000"));

        assertThat(spreadDe("CHEQUE_PRE_DATADO"))
                .as("Cheque Pre-datado: 2,5%% a.m.")
                .isEqualByComparingTo(new BigDecimal("0.025000"));
    }

    private BigDecimal spreadDe(String codigo) {
        return jdbc.sql("SELECT spread FROM tipo_recebivel WHERE codigo = :codigo")
                .param("codigo", codigo).query(BigDecimal.class).single();
    }

    @Test
    @DisplayName("todo tipo semeado tem periodicidade coerente com a convencao")
    void todoTipoTemPeriodicidadeCoerenteComAConvencao() {
        // ACT_30 e COMERCIAL_30_360 produzem expoente em MESES;
        // ACT_360, ACT_365 e BUS_252 em ANOS; TAXA_DIARIA em DIAS.
        // Combinacao incoerente nao lanca erro — produz preco errado por ordem
        // de grandeza. E' o risco R7, e o seed nao pode ser a origem dele.
        List<String> incoerentes = jdbc.sql("""
                SELECT codigo || ' (' || periodicidade || ' x ' || convencao_contagem || ')'
                  FROM tipo_recebivel
                 WHERE NOT (
                       (periodicidade = 'MENSAL' AND convencao_contagem IN ('ACT_30', 'COMERCIAL_30_360'))
                    OR (periodicidade = 'ANUAL'  AND convencao_contagem IN ('ACT_360', 'ACT_365', 'BUS_252'))
                    OR (periodicidade = 'DIARIA' AND convencao_contagem = 'TAXA_DIARIA')
                 )
                """).query(String.class).list();

        assertThat(incoerentes)
                .as("unidade da taxa precisa casar com a unidade produzida pela convencao")
                .isEmpty();
    }

    @Test
    @DisplayName("existe taxa base vigente para precificar")
    void existeTaxaBaseVigente() {
        Long vigentes = jdbc.sql("""
                SELECT count(*) FROM parametro_precificacao
                 WHERE vigencia_inicio <= now()
                """).query(Long.class).single();

        assertThat(vigentes).isPositive();
    }

    @Test
    @DisplayName("existe cedente de exemplo para desenvolvimento")
    void existeCedenteDeExemplo() {
        Long ativos = jdbc.sql("SELECT count(*) FROM cedente WHERE ativo").query(Long.class).single();

        assertThat(ativos).isPositive();
    }

    @Test
    @DisplayName("os dois sentidos do par BRL/USD tem cotacao, permitindo cross-currency")
    void parDeMoedasTemCotacaoNosDoisSentidos() {
        List<String> pares = jdbc.sql("""
                SELECT o.codigo || '->' || d.codigo
                  FROM taxa_cambio t
                  JOIN moeda o ON o.id = t.moeda_origem_id
                  JOIN moeda d ON d.id = t.moeda_destino_id
                 ORDER BY 1
                """).query(String.class).list();

        assertThat(pares).contains("BRL->USD", "USD->BRL");
    }
}
