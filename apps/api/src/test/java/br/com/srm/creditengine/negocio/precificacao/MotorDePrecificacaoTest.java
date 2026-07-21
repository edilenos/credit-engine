package br.com.srm.creditengine.negocio.precificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.Periodicidade;
import br.com.srm.creditengine.persistencia.entidade.ParametroPrecificacao;
import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;
import br.com.srm.creditengine.persistencia.repositorio.TipoRecebivelRepositorio;
import jakarta.persistence.EntityManager;

/**
 * Composicao do motor de precificacao (PBI-21).
 *
 * <p>O motor <b>compoe</b>: busca o parametro vigente, resolve o spread, resolve
 * o expoente e delega a conta. Este teste cobre a composicao, que so existe com
 * banco e contexto. A aritmetica em si — precisao, bordas, determinismo — foi
 * para {@code CalculadoraDeValorPresenteTest} no PBI-23, onde roda sem
 * infraestrutura.
 *
 * <p>Os valores esperados vem de calculo independente, rodado fora da
 * aplicacao. O PBI-17 mostrou o custo de confiar em conta mental: o exemplo
 * trabalhado do backlog ficou errado em 6 centavos por varios PBIs, afirmado
 * como "conferido a mao".
 */
@SpringBootTest
@org.springframework.context.annotation.Import(MotorDePrecificacaoTest.ProdutosSinteticos.class)
@Transactional
@DisplayName("Motor de precificacao")
class MotorDePrecificacaoTest {

    /** Operacao em 20/07/2026; +46 dias vence em 04/09; +60 dias fecha 2 meses em ACT/30. */
    private static final LocalDate OPERACAO = LocalDate.of(2026, 7, 20);

    @Autowired private MotorDePrecificacao motor;
    @Autowired private TipoRecebivelRepositorio tipos;
    @Autowired private EntityManager em;

    /**
     * Regra de risco para os produtos sinteticos deste teste.
     *
     * <p>Precisou existir porque o motor recusou o primeiro rascunho: um tipo
     * sem Strategy levanta {@code EstrategiaDeSpreadNaoEncontradaException} em
     * vez de assumir spread zero. O teste estava incompleto, e o sistema
     * reclamou — que e' exatamente o comportamento desenhado no PBI-18.
     */
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ProdutosSinteticos {
        @org.springframework.context.annotation.Bean
        EstrategiaDeSpread spreadDeContratoDeTeste() {
            return new EstrategiaDeSpread() {
                @Override
                public boolean suporta(TipoRecebivel tipo) {
                    return tipo.getCodigo().startsWith("CONTRATO_");
                }

                @Override
                public BigDecimal calcular(ContextoDePrecificacao contexto) {
                    return contexto.tipo().getSpread();
                }
            };
        }
    }

    private ContextoDePrecificacao contexto(String codigoDoTipo, String valorFace, int prazoEmDias) {
        return new ContextoDePrecificacao(
                tipos.findByCodigo(codigoDoTipo).orElseThrow(),
                new BigDecimal(valorFace), OPERACAO, OPERACAO.plusDays(prazoEmDias));
    }

    /** Produto e taxa base em unidade anual, para exercitar convencoes anuais. */
    private ContextoDePrecificacao contextoAnual(ConvencaoContagem convencao, int prazoEmDias) {
        em.persist(new ParametroPrecificacao(new BigDecimal("0.100000"), Periodicidade.ANUAL,
                OffsetDateTime.parse("2026-01-02T00:00:00Z")));
        TipoRecebivel tipo = new TipoRecebivel("CONTRATO_" + convencao, "Contrato indexado",
                new BigDecimal("0.025000"), Periodicidade.ANUAL, convencao);
        em.persist(tipo);
        em.flush();

        return new ContextoDePrecificacao(
                tipo, new BigDecimal("100000.00"), OPERACAO, OPERACAO.plusDays(prazoEmDias));
    }

    @Nested
    @DisplayName("A formula")
    class Formula {

        @Test
        @DisplayName("10.000 a 2,5% a.m. por 2 meses exatos vale 9.518,14")
        void doisMesesExatos() {
            // 60 dias sob ACT/30 dao expoente exatamente 2.
            var resultado = motor.precificar(contexto("DUPLICATA_MERCANTIL", "10000.00", 60));

            assertThat(resultado.expoenteAplicado()).isEqualByComparingTo(new BigDecimal("2"));
            assertThat(resultado.taxaTotal())
                    .as("1% de taxa base + 1,5% de spread da duplicata")
                    .isEqualByComparingTo(new BigDecimal("0.025000"));
            assertThat(resultado.valorPresente()).isEqualByComparingTo(new BigDecimal("9518.14"));
        }

        @Test
        @DisplayName("100.000 a 2,5% a.m. por 46 dias vale 96.284,58")
        void quarentaESeisDias() {
            var resultado = motor.precificar(contexto("DUPLICATA_MERCANTIL", "100000.00", 46));

            assertThat(resultado.expoenteAplicado())
                    .isEqualByComparingTo(new BigDecimal("1.5333333333"));
            assertThat(resultado.valorPresente())
                    .as("o expoente fracionario e o caso normal, nao a excecao")
                    .isEqualByComparingTo(new BigDecimal("96284.58"));
        }

        @Test
        @DisplayName("prazo zero devolve o valor de face, sem desagio")
        void prazoZero() {
            var resultado = motor.precificar(contexto("DUPLICATA_MERCANTIL", "100000.00", 0));

            assertThat(resultado.valorPresente()).isEqualByComparingTo(new BigDecimal("100000.00"));
            assertThat(resultado.desagio()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("o desagio e exposto e fecha com face menos presente")
        void desagioEhExposto() {
            var resultado = motor.precificar(contexto("DUPLICATA_MERCANTIL", "100000.00", 46));

            assertThat(resultado.desagio())
                    .isEqualByComparingTo(new BigDecimal("3715.42"))
                    .isEqualByComparingTo(
                            resultado.valorFace().subtract(resultado.valorPresente()));
        }
    }

    @Nested
    @DisplayName("As Strategies mudam o preco")
    class StrategiesMudamOPreco {

        @Test
        @DisplayName("duplicata e cheque, mesmos parametros, precos diferentes")
        void duplicataECheque() {
            var duplicata = motor.precificar(contexto("DUPLICATA_MERCANTIL", "100000.00", 46));
            var cheque = motor.precificar(contexto("CHEQUE_PRE_DATADO", "100000.00", 46));

            assertThat(duplicata.valorPresente()).isEqualByComparingTo(new BigDecimal("96284.58"));
            assertThat(cheque.valorPresente())
                    .as("spread de 2,5% contra 1,5%: risco maior, preco menor")
                    .isEqualByComparingTo(new BigDecimal("94861.82"));

            assertThat(duplicata.valorPresente())
                    .as("a Strategy de spread muda o resultado final, nao so um campo intermediario")
                    .isGreaterThan(cheque.valorPresente());
        }

        @Test
        @DisplayName("mesma operacao sob convencoes diferentes produz precos diferentes")
        void convencoesDiferentesMudamOPreco() {
            var porAct365 = motor.precificar(contextoAnual(ConvencaoContagem.ACT_365, 46));
            var porBus252 = motor.precificar(contextoAnual(ConvencaoContagem.BUS_252, 46));

            assertThat(porAct365.valorPresente()).isEqualByComparingTo(new BigDecimal("98526.57"));
            assertThat(porBus252.valorPresente()).isEqualByComparingTo(new BigDecimal("98423.42"));

            BigDecimal delta = porAct365.valorPresente().subtract(porBus252.valorPresente());
            assertThat(delta)
                    .as("46 dias corridos contra 34 uteis: R$ %s de diferenca em R$ 100.000, "
                            + "so por escolher outra convencao", delta)
                    .isEqualByComparingTo(new BigDecimal("103.15"));
        }
    }

    @Nested
    @DisplayName("Recusas")
    class Recusas {

        @Test
        @DisplayName("vencimento no passado e recusado antes de qualquer calculo")
        void vencimentoNoPassado() {
            assertThatThrownBy(() -> contexto("DUPLICATA_MERCANTIL", "100000.00", -1))
                    .isInstanceOf(VencimentoNoPassadoException.class);
        }

        @Test
        @DisplayName("taxa base e spread em unidades diferentes nao podem ser somados")
        void unidadesDivergentesEntreTaxaBaseESpread() {
            // Taxa base ANUAL cadastrada depois da mensal do seed; produto MENSAL.
            em.persist(new ParametroPrecificacao(new BigDecimal("0.100000"), Periodicidade.ANUAL,
                    OffsetDateTime.parse("2026-01-02T00:00:00Z")));
            em.flush();

            assertThatThrownBy(() -> motor.precificar(
                    contexto("DUPLICATA_MERCANTIL", "100000.00", 46)))
                    .as("somar 1% ao ano com 1,5% ao mes produz numero sem significado, "
                            + "e a soma acontece sem erro nenhum")
                    .isInstanceOf(UnidadesDaTaxaDivergentesException.class);
        }

        @Test
        @DisplayName("cheque com prazo alem do limite e recusado pela Strategy, nao precificado")
        void chequeComPrazoInviavel() {
            assertThatThrownBy(() -> motor.precificar(
                    contexto("CHEQUE_PRE_DATADO", "100000.00", 400)))
                    .isInstanceOf(PrazoInviavelException.class);
        }
    }
}
