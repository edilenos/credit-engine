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
import br.com.srm.creditengine.negocio.precificacao.spread.SpreadDeChequePreDatado;
import br.com.srm.creditengine.negocio.precificacao.spread.SpreadDeDuplicataMercantil;
import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;

/**
 * Strategy de spread, testada sem Spring e sem banco (PBI-23).
 *
 * <p>As regras de risco sao funcoes puras do contexto, e {@code TipoRecebivel}
 * e' um objeto comum — nada disso precisa de contexto de aplicacao. Os testes
 * de integracao com o resolvedor injetado continuam em
 * {@code ResolvedorDeSpreadTest}.
 */
@DisplayName("Strategies de spread (unitario)")
class EstrategiasDeSpreadTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 7, 20);
    private static final int PRAZO_MAXIMO_DO_CHEQUE = 180;

    private final SpreadDeDuplicataMercantil duplicata = new SpreadDeDuplicataMercantil();
    private final SpreadDeChequePreDatado cheque = new SpreadDeChequePreDatado(PRAZO_MAXIMO_DO_CHEQUE);

    private TipoRecebivel tipo(String codigo, String spread) {
        return new TipoRecebivel(codigo, codigo, new BigDecimal(spread),
                Periodicidade.MENSAL, ConvencaoContagem.ACT_30);
    }

    private ContextoDePrecificacao contexto(TipoRecebivel tipo, int prazoEmDias) {
        return new ContextoDePrecificacao(
                tipo, new BigDecimal("100000.00"), HOJE, HOJE.plusDays(prazoEmDias));
    }

    @Nested
    @DisplayName("Duplicata mercantil")
    class Duplicata {

        @Test
        @DisplayName("aplica o spread do produto sem ajuste: 1,5% a.m.")
        void aplicaOSpreadDoProduto() {
            var tipo = tipo(SpreadDeDuplicataMercantil.CODIGO, "0.015000");

            assertThat(duplicata.calcular(contexto(tipo, 46)))
                    .isEqualByComparingTo(new BigDecimal("0.015000"));
        }

        @Test
        @DisplayName("o prazo nao altera o premio: risco de sacado nao muda de natureza")
        void prazoNaoAlteraOPremio() {
            var tipo = tipo(SpreadDeDuplicataMercantil.CODIGO, "0.015000");

            for (int prazo : new int[] {0, 30, 180, 400, 1000}) {
                assertThat(duplicata.calcular(contexto(tipo, prazo)))
                        .as("prazo de %d dias", prazo)
                        .isEqualByComparingTo(new BigDecimal("0.015000"));
            }
        }

        @Test
        @DisplayName("responde apenas pelo proprio codigo")
        void respondeApenasPeloProprioCodigo() {
            assertThat(duplicata.suporta(tipo(SpreadDeDuplicataMercantil.CODIGO, "0.015000"))).isTrue();
            assertThat(duplicata.suporta(tipo(SpreadDeChequePreDatado.CODIGO, "0.025000"))).isFalse();
        }
    }

    @Nested
    @DisplayName("Cheque pre-datado")
    class Cheque {

        @Test
        @DisplayName("aplica o spread do produto: 2,5% a.m.")
        void aplicaOSpreadDoProduto() {
            var tipo = tipo(SpreadDeChequePreDatado.CODIGO, "0.025000");

            assertThat(cheque.calcular(contexto(tipo, 46)))
                    .isEqualByComparingTo(new BigDecimal("0.025000"));
        }

        @Test
        @DisplayName("no limite exato do prazo ainda precifica")
        void noLimiteExatoAindaPrecifica() {
            var tipo = tipo(SpreadDeChequePreDatado.CODIGO, "0.025000");

            assertThat(cheque.calcular(contexto(tipo, PRAZO_MAXIMO_DO_CHEQUE)))
                    .as("o limite e inclusivo: 180 dias vale, 181 nao")
                    .isEqualByComparingTo(new BigDecimal("0.025000"));
        }

        @Test
        @DisplayName("um dia alem do limite ja recusa")
        void umDiaAlemDoLimiteRecusa() {
            var tipo = tipo(SpreadDeChequePreDatado.CODIGO, "0.025000");

            assertThatThrownBy(() -> cheque.calcular(contexto(tipo, PRAZO_MAXIMO_DO_CHEQUE + 1)))
                    .isInstanceOf(PrazoInviavelException.class)
                    .hasMessageContaining("181");
        }

        @Test
        @DisplayName("o limite vem da configuracao, nao do codigo")
        void limiteVemDaConfiguracao() {
            var chequeCurto = new SpreadDeChequePreDatado(30);
            var tipo = tipo(SpreadDeChequePreDatado.CODIGO, "0.025000");

            assertThatThrownBy(() -> chequeCurto.calcular(contexto(tipo, 31)))
                    .as("a mesa ajusta a politica sem recompilar")
                    .isInstanceOf(PrazoInviavelException.class);
        }
    }

    @Nested
    @DisplayName("As duas regras sao diferentes")
    class RegrasDiferentes {

        @Test
        @DisplayName("400 dias: duplicata precifica, cheque recusa")
        void mesmoPrazoDestinosDiferentes() {
            var tipoDuplicata = tipo(SpreadDeDuplicataMercantil.CODIGO, "0.015000");
            var tipoCheque = tipo(SpreadDeChequePreDatado.CODIGO, "0.025000");

            assertThat(duplicata.calcular(contexto(tipoDuplicata, 400))).isNotNull();

            assertThatThrownBy(() -> cheque.calcular(contexto(tipoCheque, 400)))
                    .as("nao e a mesma regra com dois numeros: uma tem horizonte, a outra nao")
                    .isInstanceOf(PrazoInviavelException.class);
        }
    }

    @Nested
    @DisplayName("Resolvedor")
    class Resolvedor {

        private final ResolvedorDeSpread resolvedor =
                new ResolvedorDeSpread(List.of(duplicata, cheque));

        @Test
        @DisplayName("seleciona a regra pelo codigo do produto")
        void selecionaPeloCodigo() {
            assertThat(resolvedor.spreadPara(
                    contexto(tipo(SpreadDeDuplicataMercantil.CODIGO, "0.015000"), 46)))
                    .isEqualByComparingTo(new BigDecimal("0.015000"));

            assertThat(resolvedor.spreadPara(
                    contexto(tipo(SpreadDeChequePreDatado.CODIGO, "0.025000"), 46)))
                    .isEqualByComparingTo(new BigDecimal("0.025000"));
        }

        @Test
        @DisplayName("produto sem regra levanta erro, nunca devolve zero")
        void produtoSemRegraLevantaErro() {
            assertThatThrownBy(() -> resolvedor.spreadPara(
                    contexto(tipo("CONTRATO_SEM_REGRA", "0.030000"), 46)))
                    .isInstanceOf(EstrategiaDeSpreadNaoEncontradaException.class);
        }
    }

    @Nested
    @DisplayName("Contexto")
    class Contexto {

        @Test
        @DisplayName("vencimento anterior a operacao e recusado na construcao")
        void vencimentoNoPassado() {
            assertThatThrownBy(() -> contexto(tipo("X", "0.01"), -1))
                    .isInstanceOf(VencimentoNoPassadoException.class);
        }

        @Test
        @DisplayName("vencimento igual a data da operacao e valido: prazo zero")
        void vencimentoNoMesmoDia() {
            assertThat(contexto(tipo("X", "0.01"), 0).prazoEmDiasCorridos()).isZero();
        }
    }
}
