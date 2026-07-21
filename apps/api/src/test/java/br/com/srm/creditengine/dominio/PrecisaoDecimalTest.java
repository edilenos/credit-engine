package br.com.srm.creditengine.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Politica de precisao decimal (PBI-17). */
@DisplayName("Precisao decimal")
class PrecisaoDecimalTest {

    @Nested
    @DisplayName("Divisao segura")
    class DivisaoSegura {

        @Test
        @DisplayName("a sobrecarga ingenua do BigDecimal estoura em dizima — e por isso que dividir() existe")
        void sobrecargaIngenuaEstouraEmDizima() {
            BigDecimal um = BigDecimal.ONE;
            BigDecimal tres = new BigDecimal("3");

            assertThatThrownBy(() -> um.divide(tres))
                    .as("documenta a armadilha que a politica remove")
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        @DisplayName("dividir() nao estoura em dizima")
        void dividirNaoEstouraEmDizima() {
            assertThatCode(() -> PrecisaoDecimal.dividir(BigDecimal.ONE, new BigDecimal("3")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("dividir() no caso real da formula: valor face sobre fator de desconto")
        void dividirNoCasoRealDaFormula() {
            // VF / (1+i)^n com fator dizimico — e o coracao do sistema, e produz
            // dizima o tempo todo.
            BigDecimal valorFace = new BigDecimal("100000.00");
            BigDecimal fator = new BigDecimal("1.0385879050");

            BigDecimal presente = PrecisaoDecimal.dividir(valorFace, fator);

            // Conferido por calculo independente, nao de cabeca: a primeira
            // versao deste teste trazia 96284.52 e reprovou. O erro vinha do
            // exemplo trabalhado do backlog, que estava errado em 6 centavos e
            // foi corrigido junto com este PBI.
            assertThat(PrecisaoDecimal.comoMoeda(presente))
                    .isEqualByComparingTo(new BigDecimal("96284.58"));
        }

        @Test
        @DisplayName("mantem precisao alta: quem arredonda e a fronteira de saida")
        void mantemPrecisaoAlta() {
            BigDecimal resultado = PrecisaoDecimal.dividir(BigDecimal.ONE, new BigDecimal("3"));

            assertThat(resultado.precision())
                    .as("arredondar a cada passo intermediario acumula erro em vez de conte-lo")
                    .isGreaterThan(20);
        }
    }

    @Nested
    @DisplayName("Arredondamento bancario")
    class ArredondamentoBancario {

        @Test
        @DisplayName("HALF_EVEN alterna o destino do empate, HALF_UP sempre sobe")
        void halfEvenAlternaODestinoDoEmpate() {
            assertThat(PrecisaoDecimal.comoMoeda(new BigDecimal("2.345")))
                    .as("empate com digito anterior par: desce")
                    .isEqualByComparingTo(new BigDecimal("2.34"));

            assertThat(PrecisaoDecimal.comoMoeda(new BigDecimal("2.355")))
                    .as("empate com digito anterior impar: sobe")
                    .isEqualByComparingTo(new BigDecimal("2.36"));
        }

        @Test
        @DisplayName("numa carteira grande, HALF_UP enviesa a soma e HALF_EVEN nao")
        void halfUpEnviesaASomaDeUmaCarteira() {
            // Mil valores em empate exato, com o digito retido percorrendo
            // 0..9: 0,005 / 0,015 / 0,025 ... E' o que faz HALF_EVEN alternar
            // entre descer e subir. A primeira versao deste teste usava
            // "N,005", onde o digito retido e' SEMPRE zero — ali HALF_EVEN
            // tambem descia sempre, e os dois modos empatavam em desvio.
            BigDecimal somaHalfUp = BigDecimal.ZERO;
            BigDecimal somaHalfEven = BigDecimal.ZERO;
            BigDecimal exata = BigDecimal.ZERO;

            for (int i = 0; i < 1000; i++) {
                BigDecimal valor = new BigDecimal(i).movePointLeft(2).add(new BigDecimal("0.005"));
                exata = exata.add(valor);
                somaHalfUp = somaHalfUp.add(valor.setScale(2, RoundingMode.HALF_UP));
                somaHalfEven = somaHalfEven.add(PrecisaoDecimal.comoMoeda(valor));
            }

            BigDecimal desvioHalfUp = somaHalfUp.subtract(exata).abs();
            BigDecimal desvioHalfEven = somaHalfEven.subtract(exata).abs();

            assertThat(desvioHalfEven)
                    .as("o vies de HALF_EVEN se cancela (%s); o de HALF_UP acumula (%s). "
                            + "Pouco por operacao, muito no agregado", desvioHalfEven, desvioHalfUp)
                    .isLessThan(desvioHalfUp);
        }
    }

    @Nested
    @DisplayName("Escalas")
    class Escalas {

        @Test
        @DisplayName("moeda usa 2 casas; taxa usa 6 porque entra em exponenciacao")
        void escalasPorFinalidade() {
            assertThat(PrecisaoDecimal.comoMoeda(new BigDecimal("1.239999")).scale()).isEqualTo(2);
            assertThat(PrecisaoDecimal.comoTaxa(new BigDecimal("0.0150000001")).scale()).isEqualTo(6);
            assertThat(PrecisaoDecimal.comoExpoente(new BigDecimal("1.53333333333")).scale()).isEqualTo(10);
        }

        @Test
        @DisplayName("escala da moeda vem do cadastro, nao de constante")
        void escalaDaMoedaVemDoCadastro() {
            BigDecimal valor = new BigDecimal("1234.5678");

            assertThat(PrecisaoDecimal.comoMoeda(valor, 0).scale())
                    .as("JPY nao tem centavos")
                    .isZero();
            assertThat(PrecisaoDecimal.comoMoeda(valor, 3).scale())
                    .as("KWD usa 3 casas")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("o contexto tem folga sobre as colunas NUMERIC(19,x)")
        void contextoTemFolgaSobreAsColunas() {
            assertThat(PrecisaoDecimal.CONTEXTO.getPrecision())
                    .as("nenhum passo intermediario pode perder o que a coluna final guardaria")
                    .isGreaterThan(19);
            assertThat(PrecisaoDecimal.CONTEXTO.getRoundingMode())
                    .isEqualTo(PrecisaoDecimal.ARREDONDAMENTO);
        }
    }
}
