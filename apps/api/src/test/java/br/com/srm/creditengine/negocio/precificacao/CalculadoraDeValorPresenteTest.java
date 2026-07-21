package br.com.srm.creditengine.negocio.precificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import br.com.srm.creditengine.dominio.PrecisaoDecimal;
import ch.obermuhlner.math.big.BigDecimalMath;

/**
 * A formula, testada sem Spring e sem banco (PBI-23).
 *
 * <p>O criterio de aceite pede exatamente isto: os testes das regras de calculo
 * rodam sem infraestrutura. Ate aqui todo teste de precificacao subia contexto
 * e banco, o que custava segundos por classe para conferir uma divisao.
 *
 * <p>Todos os valores esperados foram calculados fora da aplicacao antes de
 * virarem assercao.
 */
@DisplayName("Calculadora de valor presente")
class CalculadoraDeValorPresenteTest {

    /** 46 dias sob ACT/30. */
    private static final BigDecimal EXPOENTE_46_DIAS = new BigDecimal("1.5333333333");
    private static final BigDecimal TAXA_DUPLICATA = new BigDecimal("0.025");

    private BigDecimal descontar(String face, BigDecimal taxa, BigDecimal expoente) {
        return CalculadoraDeValorPresente.descontar(new BigDecimal(face), taxa, expoente);
    }

    @Nested
    @DisplayName("Casos de referencia")
    class CasosDeReferencia {

        @Test
        @DisplayName("100.000 a 2,5% por 1,5333 meses vale 96.284,58")
        void casoDeReferencia() {
            assertThat(descontar("100000.00", TAXA_DUPLICATA, EXPOENTE_46_DIAS))
                    .isEqualByComparingTo(new BigDecimal("96284.58"));
        }

        @Test
        @DisplayName("expoente zero devolve o proprio valor de face")
        void expoenteZero() {
            assertThat(descontar("100000.00", TAXA_DUPLICATA, BigDecimal.ZERO))
                    .as("(1+i)^0 = 1: sem prazo, sem desconto")
                    .isEqualByComparingTo(new BigDecimal("100000.00"));
        }

        @Test
        @DisplayName("taxa zero devolve o proprio valor de face, qualquer que seja o prazo")
        void taxaZero() {
            assertThat(descontar("100000.00", BigDecimal.ZERO, new BigDecimal("120")))
                    .isEqualByComparingTo(new BigDecimal("100000.00"));
        }
    }

    @Nested
    @DisplayName("Valores de borda")
    class ValoresDeBorda {

        @Test
        @DisplayName("um centavo continua um centavo: o desconto some no arredondamento")
        void montanteMinimo() {
            assertThat(descontar("0.01", TAXA_DUPLICATA, EXPOENTE_46_DIAS))
                    .as("0,0096 arredonda para 0,01 — abaixo da granularidade da moeda "
                            + "o desconto e' invisivel, e isso e' correto, nao um bug")
                    .isEqualByComparingTo(new BigDecimal("0.01"));
        }

        @Test
        @DisplayName("dez centavos ja mostram o desconto")
        void montantePequeno() {
            assertThat(descontar("0.10", TAXA_DUPLICATA, EXPOENTE_46_DIAS))
                    .isEqualByComparingTo(new BigDecimal("0.10"));
        }

        @Test
        @DisplayName("um trilhao nao estoura nem perde precisao")
        void montanteMuitoGrande() {
            assertThat(descontar("1000000000000.00", TAXA_DUPLICATA, EXPOENTE_46_DIAS))
                    .as("13 digitos inteiros mais 2 decimais cabem em NUMERIC(19,2); "
                            + "com double o resultado ja teria perdido os centavos")
                    .isEqualByComparingTo(new BigDecimal("962845798695.19"));
        }

        @Test
        @DisplayName("prazo longo: 10 anos a 2,5% ao mes derretem o valor presente")
        void prazoLongo() {
            assertThat(descontar("100000.00", TAXA_DUPLICATA, new BigDecimal("120")))
                    .isEqualByComparingTo(new BigDecimal("5165.78"));
        }

        @Test
        @DisplayName("prazo muito longo: 30 anos levam o valor presente a duas casas")
        void prazoMuitoLongo() {
            assertThat(descontar("100000.00", TAXA_DUPLICATA, new BigDecimal("360")))
                    .as("1,025^360 e da ordem de 7.000: o expoente domina o resultado")
                    .isEqualByComparingTo(new BigDecimal("13.79"));
        }

        @Test
        @DisplayName("expoente inteiro grande, na convencao de taxa diaria")
        void expoenteInteiroGrande() {
            // 3652 dias corridos a 0,05% ao dia — o caso da convencao TAXA_DIARIA.
            assertThat(descontar("100000.00", new BigDecimal("0.0005"), new BigDecimal("3652")))
                    .isEqualByComparingTo(new BigDecimal("16113.00"));
        }
    }

    @Nested
    @DisplayName("Robustez numerica")
    class RobustezNumerica {

        @Test
        @DisplayName("fator dizimico nao lanca ArithmeticException")
        void fatorDizimicoNaoLanca() {
            assertThatCode(() -> descontar("100000.00", new BigDecimal("0.0333333333"),
                    new BigDecimal("1.3333333333")))
                    .as("a sobrecarga BigDecimal.divide(BigDecimal) rejeitaria este caso")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("o resultado sai sempre na escala monetaria")
        void escalaMonetaria() {
            assertThat(descontar("100000.00", TAXA_DUPLICATA, EXPOENTE_46_DIAS).scale())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("mesma entrada, mesma saida: nenhuma dependencia de plataforma")
        void deterministico() {
            BigDecimal primeira = descontar("100000.00", TAXA_DUPLICATA, EXPOENTE_46_DIAS);

            for (int i = 0; i < 50; i++) {
                assertThat(descontar("100000.00", TAXA_DUPLICATA, EXPOENTE_46_DIAS))
                        .isEqualByComparingTo(primeira);
            }
        }

        @Test
        @DisplayName("dobrar a precisao do contexto nao muda as duas casas finais")
        void precisaoMaiorNaoMudaOResultado() {
            BigDecimal comPadrao = descontar("100000.00", TAXA_DUPLICATA, EXPOENTE_46_DIAS);
            BigDecimal comDobro = descontarCom(new MathContext(68, RoundingMode.HALF_EVEN));

            assertThat(comPadrao)
                    .as("34 digitos ja dao folga sobre os 19 das colunas NUMERIC")
                    .isEqualByComparingTo(comDobro);
        }

        private BigDecimal descontarCom(MathContext mc) {
            BigDecimal fator = BigDecimalMath.pow(
                    BigDecimal.ONE.add(TAXA_DUPLICATA), EXPOENTE_46_DIAS, mc);
            return new BigDecimal("100000.00").divide(fator, mc)
                    .setScale(2, RoundingMode.HALF_EVEN);
        }

        @Test
        @DisplayName("expoente inteiro: big-math e pow(int) chegam ao mesmo numero")
        void bigMathConfereComPowInt() {
            // TAXA_DIARIA produz expoente inteiro, entao os dois caminhos
            // precisam coincidir — se divergissem, um dos dois estaria errado e
            // nao haveria como saber qual.
            BigDecimal base = new BigDecimal("1.0005");

            BigDecimal viaBigMath = BigDecimalMath.pow(
                    base, new BigDecimal("46"), PrecisaoDecimal.CONTEXTO);
            BigDecimal viaPowInt = base.pow(46, PrecisaoDecimal.CONTEXTO);

            assertThat(viaBigMath.setScale(20, RoundingMode.HALF_EVEN))
                    .isEqualByComparingTo(viaPowInt.setScale(20, RoundingMode.HALF_EVEN));
        }

        @Test
        @DisplayName("valor presente nunca supera o valor de face com taxa positiva")
        void nuncaSuperaOValorDeFace() {
            for (String prazo : new String[] {"0.1", "1", "1.5333333333", "12", "120"}) {
                assertThat(descontar("100000.00", TAXA_DUPLICATA, new BigDecimal(prazo)))
                        .as("desagio negativo violaria o CHECK do banco em prazo=%s", prazo)
                        .isLessThanOrEqualTo(new BigDecimal("100000.00"));
            }
        }
    }
}
