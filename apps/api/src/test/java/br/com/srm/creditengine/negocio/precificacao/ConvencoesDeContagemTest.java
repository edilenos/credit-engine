package br.com.srm.creditengine.negocio.precificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.Periodicidade;
import br.com.srm.creditengine.negocio.precificacao.convencao.ContagemAct30;
import br.com.srm.creditengine.negocio.precificacao.convencao.ContagemAct360;
import br.com.srm.creditengine.negocio.precificacao.convencao.ContagemAct365;
import br.com.srm.creditengine.negocio.precificacao.convencao.ContagemComercial30360;
import br.com.srm.creditengine.negocio.precificacao.convencao.ContagemTaxaDiaria;

/**
 * Convencoes de contagem, testadas sem Spring e sem banco (PBI-23).
 *
 * <p>Cinco das seis convencoes sao funcoes puras de duas datas. So a BUS/252
 * precisa do calendario de feriados, e por isso continua em
 * {@code ContagemBus252Test} com contexto e banco.
 *
 * <p>Expoentes conferidos por calculo independente antes de virarem assercao.
 */
@DisplayName("Convencoes de contagem (unitario)")
class ConvencoesDeContagemTest {

    private static final LocalDate OPERACAO = LocalDate.of(2026, 7, 20);
    private static final LocalDate VENCIMENTO_46 = LocalDate.of(2026, 9, 4);

    private final ContagemAct30 act30 = new ContagemAct30();
    private final ContagemAct360 act360 = new ContagemAct360();
    private final ContagemAct365 act365 = new ContagemAct365();
    private final ContagemTaxaDiaria diaria = new ContagemTaxaDiaria();
    private final ContagemComercial30360 comercial = new ContagemComercial30360();

    @Nested
    @DisplayName("Dias corridos sobre base fixa")
    class DiasCorridos {

        @Test
        @DisplayName("46 dias: ACT/30 = 1,5333 meses")
        void act30Em46Dias() {
            assertThat(act30.calcularExpoente(OPERACAO, VENCIMENTO_46))
                    .isEqualByComparingTo(new BigDecimal("1.5333333333"));
        }

        @Test
        @DisplayName("46 dias: ACT/360 = 0,1278 anos")
        void act360Em46Dias() {
            assertThat(act360.calcularExpoente(OPERACAO, VENCIMENTO_46))
                    .isEqualByComparingTo(new BigDecimal("0.1277777778"));
        }

        @Test
        @DisplayName("46 dias: ACT/365 = 0,1260 anos")
        void act365Em46Dias() {
            assertThat(act365.calcularExpoente(OPERACAO, VENCIMENTO_46))
                    .isEqualByComparingTo(new BigDecimal("0.1260273973"));
        }

        @Test
        @DisplayName("46 dias: taxa diaria = 46, expoente inteiro")
        void diariaEm46Dias() {
            BigDecimal expoente = diaria.calcularExpoente(OPERACAO, VENCIMENTO_46);

            assertThat(expoente).isEqualByComparingTo(new BigDecimal("46"));
            assertThat(expoente.stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
        }

        @Test
        @DisplayName("mesmo dia: todas produzem expoente zero")
        void mesmoDiaProduzZero() {
            for (var convencao : List.of(act30, act360, act365, diaria, comercial)) {
                assertThat(convencao.calcularExpoente(OPERACAO, OPERACAO))
                        .as("convencao %s", convencao.codigo())
                        .isEqualByComparingTo(BigDecimal.ZERO);
            }
        }

        @Test
        @DisplayName("ano bissexto nao muda ACT/365, que e fixo por definicao")
        void anoBissextoNaoMudaAct365() {
            // 2028 e bissexto: 29/02 existe e entra na contagem de dias corridos,
            // mas o denominador continua 365 — e isso que "fixo" significa.
            BigDecimal expoente = act365.calcularExpoente(
                    LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 1));

            assertThat(expoente)
                    .as("29 dias em fevereiro de 2028, sobre 365")
                    .isEqualByComparingTo(new BigDecimal("0.0794520548"));
        }
    }

    @Nested
    @DisplayName("Regra comercial 30/360")
    class Comercial {

        @Test
        @DisplayName("31/01 a 31/03: os dois ajustes de dia 31 se aplicam, fechando 2 meses")
        void ajusteNasDuasPontas() {
            assertThat(comercial.calcularExpoente(
                    LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31)))
                    .isEqualByComparingTo(new BigDecimal("2"));
        }

        @Test
        @DisplayName("31/01 a 01/03: so o ajuste de D1 se aplica")
        void apenasAjusteDeD1() {
            assertThat(comercial.calcularExpoente(
                    LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1)))
                    .isEqualByComparingTo(new BigDecimal("1.0333333333"));
        }

        @Test
        @DisplayName("30/01 a 31/03: D2=31 mas D1=30, entao o segundo ajuste ainda se aplica")
        void segundoAjusteDependeDoPrimeiro() {
            // D1 = 30 (nao sofre ajuste), D2 = 31 -> 30 porque D1 >= 30.
            // 360*0 + 30*2 + (30-30) = 60 dias = 2 meses.
            assertThat(comercial.calcularExpoente(
                    LocalDate.of(2026, 1, 30), LocalDate.of(2026, 3, 31)))
                    .as("a condicao do segundo ajuste testa D1 >= 30, nao D1 == 31")
                    .isEqualByComparingTo(new BigDecimal("2"));
        }

        @Test
        @DisplayName("15/01 a 31/03: D1 < 30, entao D2=31 NAO e ajustado")
        void segundoAjusteNaoSeAplicaComD1Baixo() {
            // D1 = 15, D2 = 31 permanece 31.
            // 360*0 + 30*2 + (31-15) = 76 dias.
            assertThat(comercial.calcularExpoente(
                    LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 31)))
                    .as("inverter a ordem dos ajustes daria 75 aqui — 2,5333 em vez de 2,5333... "
                            + "e o caso que separa as duas implementacoes")
                    .isEqualByComparingTo(new BigDecimal("2.5333333333"));
        }

        @Test
        @DisplayName("atravessa a virada de ano corretamente")
        void atravessaViradaDeAno() {
            // 360*1 + 30*(1-12) + (15-15) = 360 - 330 = 30 dias = 1 mes.
            assertThat(comercial.calcularExpoente(
                    LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15)))
                    .isEqualByComparingTo(new BigDecimal("1"));
        }

        @Test
        @DisplayName("nao e dias corridos: 15/01 a 15/02 diverge de ACT/30")
        void divergeDeDiasCorridos() {
            LocalDate inicio = LocalDate.of(2026, 1, 15);
            LocalDate fim = LocalDate.of(2026, 2, 15);

            assertThat(comercial.calcularExpoente(inicio, fim))
                    .isEqualByComparingTo(new BigDecimal("1"));
            assertThat(act30.calcularExpoente(inicio, fim))
                    .as("janeiro tem 31 dias corridos")
                    .isEqualByComparingTo(new BigDecimal("1.0333333333"));
        }
    }

    @Nested
    @DisplayName("Unidade declarada")
    class UnidadeDeclarada {

        @Test
        @DisplayName("cada convencao declara a unidade que produz")
        void cadaConvencaoDeclaraSuaUnidade() {
            assertThat(act30.periodicidadeEsperada()).isEqualTo(Periodicidade.MENSAL);
            assertThat(comercial.periodicidadeEsperada()).isEqualTo(Periodicidade.MENSAL);
            assertThat(act360.periodicidadeEsperada()).isEqualTo(Periodicidade.ANUAL);
            assertThat(act365.periodicidadeEsperada()).isEqualTo(Periodicidade.ANUAL);
            assertThat(diaria.periodicidadeEsperada()).isEqualTo(Periodicidade.DIARIA);
        }
    }

    @Nested
    @DisplayName("Resolvedor")
    class Resolvedor {

        private final ResolvedorDeConvencao resolvedor =
                new ResolvedorDeConvencao(List.of(act30, act360, act365, diaria, comercial));

        @Test
        @DisplayName("seleciona pelo codigo, sem if nem switch")
        void selecionaPeloCodigo() {
            assertThat(resolvedor.expoente(ConvencaoContagem.ACT_30, Periodicidade.MENSAL,
                    OPERACAO, VENCIMENTO_46))
                    .isEqualByComparingTo(new BigDecimal("1.5333333333"));
        }

        @Test
        @DisplayName("unidade incompativel e recusada antes de qualquer calculo")
        void unidadeIncompativelEhRecusada() {
            assertThatThrownBy(() -> resolvedor.expoente(
                    ConvencaoContagem.ACT_365, Periodicidade.MENSAL, OPERACAO, VENCIMENTO_46))
                    .isInstanceOf(UnidadeIncompativelException.class);
        }

        @Test
        @DisplayName("codigo sem implementacao falha alto")
        void codigoSemImplementacaoFalhaAlto() {
            assertThatThrownBy(() -> resolvedor.expoente(
                    ConvencaoContagem.BUS_252, Periodicidade.ANUAL, OPERACAO, VENCIMENTO_46))
                    .as("este resolvedor nao recebeu a BUS_252; o de producao recebe")
                    .isInstanceOf(ConvencaoNaoImplementadaException.class);
        }
    }
}
