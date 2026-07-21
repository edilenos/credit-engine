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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.Periodicidade;
import br.com.srm.creditengine.negocio.precificacao.spread.SpreadDeChequePreDatado;
import br.com.srm.creditengine.negocio.precificacao.spread.SpreadDeDuplicataMercantil;
import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;
import br.com.srm.creditengine.persistencia.repositorio.TipoRecebivelRepositorio;

/**
 * Strategy de spread por tipo de recebivel (PBI-18).
 *
 * <p>O que estes testes precisam provar nao e' apenas "o spread sai certo" —
 * um {@code Map} passaria nisso. E' que as regras sao <b>diferentes entre si</b>
 * e que <b>um produto novo entra sem tocar em nada</b>.
 */
@SpringBootTest
@Transactional
@DisplayName("Strategy de spread")
class ResolvedorDeSpreadTest {

    private static final LocalDate HOJE = LocalDate.now();

    @Autowired
    private ResolvedorDeSpread resolvedor;

    @Autowired
    private TipoRecebivelRepositorio tipos;

    private ContextoDePrecificacao contexto(String codigoDoTipo, int prazoEmDias) {
        TipoRecebivel tipo = tipos.findByCodigo(codigoDoTipo).orElseThrow();
        return new ContextoDePrecificacao(
                tipo, new BigDecimal("100000.00"), HOJE, HOJE.plusDays(prazoEmDias));
    }

    @Nested
    @DisplayName("Selecao polimorfica")
    class Selecao {

        @Test
        @DisplayName("duplicata recebe o spread do enunciado: 1,5% a.m.")
        void duplicataRecebeSpreadDoEnunciado() {
            assertThat(resolvedor.spreadPara(contexto(SpreadDeDuplicataMercantil.CODIGO, 46)))
                    .isEqualByComparingTo(new BigDecimal("0.015000"));
        }

        @Test
        @DisplayName("cheque recebe o spread do enunciado: 2,5% a.m.")
        void chequeRecebeSpreadDoEnunciado() {
            assertThat(resolvedor.spreadPara(contexto(SpreadDeChequePreDatado.CODIGO, 46)))
                    .isEqualByComparingTo(new BigDecimal("0.025000"));
        }

        @Test
        @DisplayName("tipo sem regra levanta erro, nunca devolve spread zero")
        void tipoSemRegraLevantaErro() {
            TipoRecebivel semRegra = new TipoRecebivel(
                    "CONTRATO_SEM_REGRA", "Contrato", new BigDecimal("0.030000"),
                    Periodicidade.MENSAL, ConvencaoContagem.ACT_30);
            ContextoDePrecificacao ctx = new ContextoDePrecificacao(
                    semRegra, new BigDecimal("1000.00"), HOJE, HOJE.plusDays(30));

            assertThatThrownBy(() -> resolvedor.spreadPara(ctx))
                    .as("produto precificado com premio de risco nulo sai caro e nao deixa rastro")
                    .isInstanceOf(EstrategiaDeSpreadNaoEncontradaException.class)
                    .hasMessageContaining("CONTRATO_SEM_REGRA");
        }
    }

    @Nested
    @DisplayName("As regras sao diferentes de verdade")
    class RegrasDiferentes {

        @Test
        @DisplayName("cheque recusa prazo alem do limite; duplicata aceita o mesmo prazo")
        void chequeRecusaPrazoLongoQueDuplicataAceita() {
            int prazoLongo = 400;

            assertThatThrownBy(() -> resolvedor.spreadPara(
                    contexto(SpreadDeChequePreDatado.CODIGO, prazoLongo)))
                    .as("cheque tem horizonte de apresentacao e prescricao; passado ele, "
                            + "antecipar nao e operacao de credito")
                    .isInstanceOf(PrazoInviavelException.class);

            assertThat(resolvedor.spreadPara(contexto(SpreadDeDuplicataMercantil.CODIGO, prazoLongo)))
                    .as("credito de sacado nao tem horizonte: duplicata longa continua duplicata. "
                            + "E aqui que se ve que nao e a mesma regra com dois numeros")
                    .isEqualByComparingTo(new BigDecimal("0.015000"));
        }

        @Test
        @DisplayName("cheque dentro do limite e precificado normalmente")
        void chequeDentroDoLimiteEhPrecificado() {
            assertThat(resolvedor.spreadPara(contexto(SpreadDeChequePreDatado.CODIGO, 179)))
                    .isEqualByComparingTo(new BigDecimal("0.025000"));
        }
    }

    @Nested
    @DisplayName("Extensibilidade (OCP)")
    class Extensibilidade {

        /**
         * Produto novo com regra genuinamente diferente: spread cresce com o
         * prazo. Existe so no contexto deste teste — e a prova de que adicionar
         * um tipo nao exige tocar no resolvedor nem no motor de calculo.
         */
        @TestConfiguration
        static class ProdutoNovo {
            @Bean
            EstrategiaDeSpread spreadDeContratoIndexado() {
                return new EstrategiaDeSpread() {
                    @Override
                    public boolean suporta(TipoRecebivel tipo) {
                        return "CONTRATO_INDEXADO".equals(tipo.getCodigo());
                    }

                    @Override
                    public BigDecimal calcular(ContextoDePrecificacao contexto) {
                        // Regra propria: premio cresce 0,1 ponto a cada 30 dias.
                        BigDecimal blocos = BigDecimal.valueOf(contexto.prazoEmDiasCorridos() / 30);
                        return contexto.tipo().getSpread()
                                .add(blocos.multiply(new BigDecimal("0.001000")));
                    }
                };
            }
        }

        @Test
        @DisplayName("um produto novo entra so criando a classe, sem tocar no resolvedor")
        void produtoNovoEntraSemTocarNoResolvedor() {
            TipoRecebivel indexado = new TipoRecebivel(
                    "CONTRATO_INDEXADO", "Contrato indexado", new BigDecimal("0.020000"),
                    Periodicidade.MENSAL, ConvencaoContagem.ACT_30);
            ContextoDePrecificacao ctx = new ContextoDePrecificacao(
                    indexado, new BigDecimal("50000.00"), HOJE, HOJE.plusDays(90));

            assertThat(resolvedor.spreadPara(ctx))
                    .as("0,02 base + 3 blocos de 30 dias x 0,001 = 0,023")
                    .isEqualByComparingTo(new BigDecimal("0.023000"));
        }

        @Test
        @DisplayName("o resolvedor enxerga as tres regras sem ter sido alterado")
        void resolvedorEnxergaAsTresRegras() {
            assertThat(resolvedor.quantidadeDeEstrategias())
                    .as("duas de producao mais a do teste, todas descobertas por injecao")
                    .isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Validacao do contexto")
    class ValidacaoDoContexto {

        @Test
        @DisplayName("vencimento no passado nao chega a ser precificado")
        void vencimentoNoPassadoNaoEhPrecificado() {
            TipoRecebivel tipo = tipos.findByCodigo(SpreadDeDuplicataMercantil.CODIGO).orElseThrow();

            assertThatThrownBy(() -> new ContextoDePrecificacao(
                    tipo, new BigDecimal("1000.00"), HOJE, HOJE.minusDays(1)))
                    .as("titulo ja vencido nao tem o que antecipar")
                    .isInstanceOf(VencimentoNoPassadoException.class);
        }
    }
}
