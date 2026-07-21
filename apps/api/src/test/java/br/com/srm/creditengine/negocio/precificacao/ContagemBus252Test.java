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
 * Calendario de dias uteis e convencao BUS/252 (PBI-20).
 *
 * <p>Os valores esperados foram contados por script independente sobre o mesmo
 * conjunto de feriados que a migracao V4 semeia.
 */
@SpringBootTest
@DisplayName("Contagem em dias uteis (BUS/252)")
class ContagemBus252Test {

    @Autowired
    private ResolvedorDeConvencao resolvedor;

    @Autowired
    private CalendarioDiasUteis calendario;

    private BigDecimal expoenteBus252(LocalDate inicio, LocalDate fim) {
        return resolvedor.expoente(ConvencaoContagem.BUS_252, Periodicidade.ANUAL, inicio, fim);
    }

    @Nested
    @DisplayName("Contagem de dias uteis")
    class Contagem {

        @Test
        @DisplayName("exclui fins de semana e feriados: 46 dias corridos viram 34 uteis")
        void excluiFinsDeSemanaEFeriados() {
            long uteis = calendario.diasUteisEntre(
                    LocalDate.of(2026, 7, 20), LocalDate.of(2026, 9, 4));

            assertThat(uteis)
                    .as("mesmo intervalo que as outras convencoes usam como referencia")
                    .isEqualTo(34);
        }

        @Test
        @DisplayName("periodo inteiramente em fim de semana devolve zero")
        void fimDeSemanaDevolveZero() {
            assertThat(calendario.diasUteisEntre(
                    LocalDate.of(2026, 7, 25), LocalDate.of(2026, 7, 27)))
                    .as("sabado e domingo, intervalo semiaberto: nenhum dia util")
                    .isZero();
        }

        @Test
        @DisplayName("a semana do Carnaval de 2026 tem 2 dias uteis, nao 5")
        void semanaDoCarnavalTemDoisDiasUteis() {
            // 16 e 17/02/2026 sao Carnaval (segunda e terca), calculados por
            // Computus: Pascoa 05/04, menos 48 e 47 dias.
            assertThat(calendario.diasUteisEntre(
                    LocalDate.of(2026, 2, 13), LocalDate.of(2026, 2, 19)))
                    .as("feriado movel atrelado a Pascoa entra na contagem")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("2026 tem 249 dias uteis — proximo dos 252 que dao nome a convencao")
        void anoTemCercaDe252DiasUteis() {
            long uteis = calendario.diasUteisEntre(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

            assertThat(uteis)
                    .as("o 252 e a media do mercado, nao a contagem de um ano especifico")
                    .isEqualTo(249);
        }
    }

    @Nested
    @DisplayName("Expoente")
    class Expoente {

        @Test
        @DisplayName("34 dias uteis sobre 252 dao 0,1349 anos")
        void expoenteSobre252() {
            assertThat(expoenteBus252(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 9, 4)))
                    .isEqualByComparingTo(new BigDecimal("0.1349206349"));
        }

        @Test
        @DisplayName("difere de ACT/365 no mesmo intervalo: dias uteis nao sao dias corridos")
        void difereDeAct365() {
            LocalDate inicio = LocalDate.of(2026, 7, 20);
            LocalDate fim = LocalDate.of(2026, 9, 4);

            BigDecimal bus = expoenteBus252(inicio, fim);
            BigDecimal act = resolvedor.expoente(
                    ConvencaoContagem.ACT_365, Periodicidade.ANUAL, inicio, fim);

            assertThat(bus)
                    .as("34/252 contra 46/365: mesma unidade anual, contagem diferente")
                    .isGreaterThan(act);
        }
    }

    @Nested
    @DisplayName("Cobertura do calendario")
    class Cobertura {

        @Test
        @DisplayName("cobre anos inteiros, nao o intervalo entre o primeiro e o ultimo feriado")
        void cobreAnosInteiros() {
            assertThat(calendario.inicioDaCobertura()).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(calendario.fimDaCobertura()).isEqualTo(LocalDate.of(2030, 12, 31));
        }

        @Test
        @DisplayName("data alem da cobertura falha alto, nao conta dias uteis a mais")
        void dataAlemDaCoberturaFalhaAlto() {
            assertThatThrownBy(() -> expoenteBus252(
                    LocalDate.of(2030, 12, 1), LocalDate.of(2031, 3, 1)))
                    .as("sem a checagem, os feriados de 2031 virariam dias uteis: "
                            + "expoente maior, fator maior, e o fundo pagaria menos que o devido")
                    .isInstanceOf(DataForaDaCoberturaException.class)
                    .hasMessageContaining("2031");
        }

        @Test
        @DisplayName("data anterior a cobertura tambem falha")
        void dataAnteriorFalha() {
            assertThatThrownBy(() -> expoenteBus252(
                    LocalDate.of(2024, 6, 1), LocalDate.of(2025, 6, 1)))
                    .isInstanceOf(DataForaDaCoberturaException.class);
        }
    }

    @Nested
    @DisplayName("Unidade")
    class Unidade {

        @Test
        @DisplayName("BUS_252 e anual: taxa mensal e recusada")
        void taxaMensalEhRecusada() {
            assertThatThrownBy(() -> resolvedor.expoente(
                    ConvencaoContagem.BUS_252, Periodicidade.MENSAL,
                    LocalDate.of(2026, 7, 20), LocalDate.of(2026, 9, 4)))
                    .isInstanceOf(UnidadeIncompativelException.class);
        }

        @Test
        @DisplayName("agora todas as seis convencoes tem implementacao registrada")
        void seisConvencoesRegistradas() {
            assertThat(resolvedor.convencoesRegistradas())
                    .as("BUS_252 estava no enum desde o PBI-11 e falhava alto ate agora")
                    .containsExactlyInAnyOrder(ConvencaoContagem.values());
        }
    }
}
