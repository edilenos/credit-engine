package br.com.srm.creditengine.negocio.precificacao;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Strategy de spread — o que so o contexto real pode provar (PBI-18, PBI-23).
 *
 * <p>As regras em si sao testadas sem Spring e sem banco em
 * {@code EstrategiasDeSpreadTest}, onde rodam em milissegundos. Sobraram aqui
 * duas perguntas que aquele teste nao alcanca, porque as duas dependem de
 * infraestrutura de verdade:
 *
 * <ol>
 *   <li>o <b>seed</b> carrega os spreads do enunciado — 1,5% e 2,5% a.m.? Um
 *       teste unitario constroi o {@code TipoRecebivel} a mao e por isso nunca
 *       veria uma migracao com o numero errado;
 *   <li>a <b>descoberta por injecao</b> funciona — um produto novo entra so
 *       criando a classe? So o container responde isso.
 * </ol>
 */
@SpringBootTest
@Transactional
@DisplayName("Strategy de spread — integracao")
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
    @DisplayName("O seed carrega os spreads do enunciado")
    class SeedDeReferencia {

        @Test
        @DisplayName("duplicata mercantil: 1,5% a.m.")
        void duplicataRecebeSpreadDoEnunciado() {
            assertThat(resolvedor.spreadPara(contexto(SpreadDeDuplicataMercantil.CODIGO, 46)))
                    .as("valor vem da migracao V3, nao de um objeto montado no teste")
                    .isEqualByComparingTo(new BigDecimal("0.015000"));
        }

        @Test
        @DisplayName("cheque pre-datado: 2,5% a.m.")
        void chequeRecebeSpreadDoEnunciado() {
            assertThat(resolvedor.spreadPara(contexto(SpreadDeChequePreDatado.CODIGO, 46)))
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
}
