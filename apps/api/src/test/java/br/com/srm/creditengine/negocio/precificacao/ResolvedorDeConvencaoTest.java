package br.com.srm.creditengine.negocio.precificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.Periodicidade;

/**
 * Convencoes de contagem de dias (PBI-19).
 *
 * <p>Os expoentes esperados foram calculados por script independente, nao de
 * cabeca — o PBI-17 mostrou o custo de confiar em conta mental: o exemplo
 * trabalhado do backlog estava errado em 6 centavos e ficou meses assim.
 */
@SpringBootTest
@DisplayName("Convencoes de contagem de dias")
class ResolvedorDeConvencaoTest {

    /** Operacao em 20/07/2026, vencimento em 04/09/2026: 46 dias corridos. */
    private static final LocalDate OPERACAO = LocalDate.of(2026, 7, 20);
    private static final LocalDate VENCIMENTO_46_DIAS = LocalDate.of(2026, 9, 4);

    @Autowired
    private ResolvedorDeConvencao resolvedor;

    private BigDecimal expoente(ConvencaoContagem convencao, LocalDate inicio, LocalDate fim) {
        return resolvedor.expoente(convencao, convencao.periodicidadeEsperada(), inicio, fim);
    }

    @Nested
    @DisplayName("Contagem por dias corridos")
    class DiasCorridos {

        @Test
        @DisplayName("ACT/30 sobre 46 dias produz 1,5333 meses")
        void act30() {
            assertThat(expoente(ConvencaoContagem.ACT_30, OPERACAO, VENCIMENTO_46_DIAS))
                    .isEqualByComparingTo(new BigDecimal("1.5333333333"));
        }

        @Test
        @DisplayName("ACT/360 sobre 46 dias produz 0,1278 anos")
        void act360() {
            assertThat(expoente(ConvencaoContagem.ACT_360, OPERACAO, VENCIMENTO_46_DIAS))
                    .isEqualByComparingTo(new BigDecimal("0.1277777778"));
        }

        @Test
        @DisplayName("ACT/365 sobre 46 dias produz 0,1260 anos")
        void act365() {
            assertThat(expoente(ConvencaoContagem.ACT_365, OPERACAO, VENCIMENTO_46_DIAS))
                    .as("ACT/365 fixo nao ajusta bissexto, ao contrario de ACT/ACT")
                    .isEqualByComparingTo(new BigDecimal("0.1260273973"));
        }

        @Test
        @DisplayName("ACT/360 e ACT/365 diferem so na base, e a diferenca aparece")
        void act360EAct365DiferemSoNaBase() {
            BigDecimal por360 = expoente(ConvencaoContagem.ACT_360, OPERACAO, VENCIMENTO_46_DIAS);
            BigDecimal por365 = expoente(ConvencaoContagem.ACT_365, OPERACAO, VENCIMENTO_46_DIAS);

            assertThat(por360)
                    .as("mesma contagem, denominador menor: expoente maior")
                    .isGreaterThan(por365);
        }
    }

    @Nested
    @DisplayName("Taxa diaria")
    class TaxaDiaria {

        @Test
        @DisplayName("produz expoente inteiro: 46 dias viram exatamente 46")
        void produzExpoenteInteiro() {
            BigDecimal exp = expoente(ConvencaoContagem.TAXA_DIARIA, OPERACAO, VENCIMENTO_46_DIAS);

            assertThat(exp).isEqualByComparingTo(new BigDecimal("46"));
            assertThat(exp.stripTrailingZeros().scale())
                    .as("expoente inteiro permite BigDecimal.pow(int) sem perda; "
                            + "a validacao cruzada com big-math fica no PBI-21, "
                            + "que e onde a potenciacao existe")
                    .isLessThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Regra comercial 30/360")
    class Comercial30360 {

        @Test
        @DisplayName("31/01 a 31/03 fecha exatamente 2 meses — o ajuste de borda em acao")
        void ajusteDeBordaNasDuasPontas() {
            BigDecimal comercial = expoente(ConvencaoContagem.COMERCIAL_30_360,
                    LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31));

            assertThat(comercial)
                    .as("sem os ajustes daria 61 dias, e um mes fechado apareceria como mes e um dia")
                    .isEqualByComparingTo(new BigDecimal("2"));
        }

        @Test
        @DisplayName("31/01 a 01/03: so o ajuste de D1 se aplica")
        void apenasOAjusteDeD1() {
            BigDecimal comercial = expoente(ConvencaoContagem.COMERCIAL_30_360,
                    LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1));

            assertThat(comercial)
                    .as("D1=31 vira 30; D2=1 nao sofre ajuste. 360*0 + 30*2 + (1-30) = 31 dias")
                    .isEqualByComparingTo(new BigDecimal("1.0333333333"));
        }

        @Test
        @DisplayName("nao e dias corridos disfarcado: 30/360 e ACT/30 divergem no mesmo intervalo")
        void divergeDeDiasCorridos() {
            LocalDate inicio = LocalDate.of(2026, 1, 15);
            LocalDate fim = LocalDate.of(2026, 2, 15);

            BigDecimal comercial = expoente(ConvencaoContagem.COMERCIAL_30_360, inicio, fim);
            BigDecimal corridos = expoente(ConvencaoContagem.ACT_30, inicio, fim);

            assertThat(comercial)
                    .as("mes comercial fechado")
                    .isEqualByComparingTo(new BigDecimal("1"));
            assertThat(corridos)
                    .as("janeiro tem 31 dias, entao ACT conta 31/30")
                    .isEqualByComparingTo(new BigDecimal("1.0333333333"));
            assertThat(comercial)
                    .as("logica de contagem diferente, nao apenas denominador diferente — "
                            + "e o que justifica esta familia ser Strategy")
                    .isNotEqualByComparingTo(corridos);
        }
    }

    @Nested
    @DisplayName("Validacao de unidade — o risco R7")
    class ValidacaoDeUnidade {

        @Test
        @DisplayName("taxa mensal com convencao anual e recusada antes de qualquer calculo")
        void taxaMensalComConvencaoAnualEhRecusada() {
            assertThatThrownBy(() -> resolvedor.expoente(
                    ConvencaoContagem.ACT_365, Periodicidade.MENSAL, OPERACAO, VENCIMENTO_46_DIAS))
                    .as("(1+0,015)^0,126 e uma conta executavel que devolve numero plausivel "
                            + "e errado por ordem de grandeza; nada falharia sozinho")
                    .isInstanceOf(UnidadeIncompativelException.class)
                    .hasMessageContaining("ACT_365")
                    .hasMessageContaining("MENSAL");
        }

        @Test
        @DisplayName("taxa anual com convencao mensal tambem e recusada")
        void taxaAnualComConvencaoMensalEhRecusada() {
            assertThatThrownBy(() -> resolvedor.expoente(
                    ConvencaoContagem.ACT_30, Periodicidade.ANUAL, OPERACAO, VENCIMENTO_46_DIAS))
                    .isInstanceOf(UnidadeIncompativelException.class);
        }

        @Test
        @DisplayName("taxa diaria so casa com a convencao diaria")
        void taxaDiariaSoCasaComConvencaoDiaria() {
            assertThat(expoente(ConvencaoContagem.TAXA_DIARIA, OPERACAO, VENCIMENTO_46_DIAS))
                    .isNotNull();

            assertThatThrownBy(() -> resolvedor.expoente(
                    ConvencaoContagem.TAXA_DIARIA, Periodicidade.MENSAL, OPERACAO, VENCIMENTO_46_DIAS))
                    .isInstanceOf(UnidadeIncompativelException.class);
        }
    }

    @Nested
    @DisplayName("Registro de implementacoes")
    class Registro {

        @Test
        @DisplayName("as cinco convencoes deste PBI estao registradas por codigo")
        void cincoConvencoesRegistradas() {
            assertThat(resolvedor.convencoesRegistradas()).contains(
                    ConvencaoContagem.ACT_30,
                    ConvencaoContagem.ACT_360,
                    ConvencaoContagem.ACT_365,
                    ConvencaoContagem.TAXA_DIARIA,
                    ConvencaoContagem.COMERCIAL_30_360);
        }

        @Test
        @DisplayName("codigo sem implementacao falha alto, nao cai em default silencioso")
        void codigoSemImplementacaoFalhaAlto() {
            // BUS_252 existe no enum e so ganha implementacao no PBI-20.
            assertThatThrownBy(() -> resolvedor.expoente(
                    ConvencaoContagem.BUS_252, Periodicidade.ANUAL, OPERACAO, VENCIMENTO_46_DIAS))
                    .as("expoente calculado pela convencao errada produz preco errado sem rastro")
                    .isInstanceOf(ConvencaoNaoImplementadaException.class)
                    .hasMessageContaining("BUS_252");
        }
    }
}
