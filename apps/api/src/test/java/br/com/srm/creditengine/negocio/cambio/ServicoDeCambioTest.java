package br.com.srm.creditengine.negocio.cambio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.dominio.FonteCotacao;
import br.com.srm.creditengine.persistencia.entidade.TaxaCambio;

/**
 * Regras de vigencia e conversao do motor de cambio (PBI-13).
 *
 * <p>Os testes de resiliencia do provedor externo ficam no PBI-16; aqui o alvo
 * e a regra de negocio.
 */
@SpringBootTest
@Transactional
@DisplayName("Motor de cambio")
class ServicoDeCambioTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.now();

    @Autowired
    private ServicoDeCambio cambio;

    @Nested
    @DisplayName("Vigencia")
    class Vigencia {

        @Test
        @DisplayName("registrar nova cotacao preserva as anteriores")
        void registrarPreservaAsAnteriores() {
            int antes = cambio.historico("BRL", "USD").size();

            cambio.registrar("BRL", "USD", new BigDecimal("0.200000"),
                    AGORA.minusDays(2), FonteCotacao.MANUAL);
            cambio.registrar("BRL", "USD", new BigDecimal("0.210000"),
                    AGORA.minusDays(1), FonteCotacao.MANUAL);

            assertThat(cambio.historico("BRL", "USD"))
                    .as("append-only: cotacao nunca e sobrescrita")
                    .hasSize(antes + 2);
        }

        @Test
        @DisplayName("consulta retorna a vigente na data, nao a mais recente absoluta")
        void consultaRespeitaADataDeCorte() {
            cambio.registrar("BRL", "USD", new BigDecimal("0.300000"),
                    AGORA.minusDays(10), FonteCotacao.MANUAL);
            cambio.registrar("BRL", "USD", new BigDecimal("0.400000"),
                    AGORA.minusDays(5), FonteCotacao.MANUAL);

            TaxaCambio emSeteDiasAtras = cambio.cotacaoVigente("BRL", "USD", AGORA.minusDays(7));

            assertThat(emSeteDiasAtras.getCotacao())
                    .as("a de 5 dias atras ainda nao valia ha 7 dias")
                    .isEqualByComparingTo(new BigDecimal("0.300000"));
        }

        @Test
        @DisplayName("cotacao com vigencia futura nao vaza para consulta de hoje")
        void cotacaoFuturaNaoVazaParaHoje() {
            cambio.registrar("BRL", "USD", new BigDecimal("9.999999"),
                    AGORA.plusDays(30), FonteCotacao.MANUAL);

            TaxaCambio hoje = cambio.cotacaoVigente("BRL", "USD", AGORA);

            assertThat(hoje.getCotacao()).isNotEqualByComparingTo(new BigDecimal("9.999999"));
        }

        @Test
        @DisplayName("mesma vigencia: vence a inserida por ultimo, que e a correcao")
        void mesmaVigenciaVenceAUltimaInserida() {
            OffsetDateTime instante = AGORA.minusDays(3);
            cambio.registrar("BRL", "USD", new BigDecimal("0.500000"), instante, FonteCotacao.MANUAL);
            cambio.registrar("BRL", "USD", new BigDecimal("0.550000"), instante, FonteCotacao.MANUAL);

            assertThat(cambio.cotacaoVigente("BRL", "USD", instante).getCotacao())
                    .as("em modelo append-only, corrigir e acrescentar")
                    .isEqualByComparingTo(new BigDecimal("0.550000"));
        }

        @Test
        @DisplayName("na borda exata, a cotacao ja vale: a comparacao e <= e nao <")
        void naBordaExataACotacaoJaVale() {
            // Off-by-one classico: trocar <= por < faria a cotacao so passar a
            // valer um instante depois do que foi contratado. Ate o PBI-16 nada
            // testava a borda em si.
            OffsetDateTime inicio = AGORA.minusDays(4);
            cambio.registrar("BRL", "USD", new BigDecimal("0.777000"), inicio, FonteCotacao.MANUAL);

            assertThat(cambio.cotacaoVigente("BRL", "USD", inicio).getCotacao())
                    .as("consultar no exato instante da vigencia precisa devolver a cotacao")
                    .isEqualByComparingTo(new BigDecimal("0.777000"));
        }

        @Test
        @DisplayName("um instante antes da vigencia, a cotacao ainda nao vale")
        void umInstanteAntesACotacaoAindaNaoVale() {
            // A coluna e timestamptz, com precisao de microssegundo; 1 ms e o
            // menor delta seguramente distinguivel apos ida e volta ao banco.
            OffsetDateTime inicio = AGORA.minusDays(4);
            cambio.registrar("BRL", "USD", new BigDecimal("0.888000"), inicio, FonteCotacao.MANUAL);

            assertThat(cambio.cotacaoVigente("BRL", "USD", inicio.minusNanos(1_000_000)).getCotacao())
                    .as("cotacao nao pode retroagir: quem consulta antes ve a anterior")
                    .isNotEqualByComparingTo(new BigDecimal("0.888000"));
        }

        @Test
        @DisplayName("par sem cotacao ate a data levanta erro de negocio, nao devolve nulo")
        void parSemCotacaoLevantaErroDeNegocio() {
            OffsetDateTime antesDeQualquerCotacao = OffsetDateTime.parse("2000-01-01T00:00:00Z");

            assertThatThrownBy(() -> cambio.cotacaoVigente("BRL", "USD", antesDeQualquerCotacao))
                    .isInstanceOf(CotacaoIndisponivelException.class)
                    .hasMessageContaining("BRL")
                    .hasMessageContaining("USD");
        }
    }

    @Nested
    @DisplayName("Conversao inversa")
    class ConversaoInversa {

        @Test
        @DisplayName("o sistema nao deriva cotacao por 1/x: sentido nao cotado e erro")
        void naoDerivaCotacaoInversa() {
            cambio.registrar("BRL", "USD", new BigDecimal("0.185000"), AGORA.minusDays(1),
                    FonteCotacao.MANUAL);

            // O sentido inverso so responde porque foi cadastrado de forma
            // independente no seed. Se o sistema derivasse por 1/x, a cotacao
            // devolvida seria 5,4054 em vez dos 5,40 efetivamente cotados.
            TaxaCambio inverso = cambio.cotacaoVigente("USD", "BRL", AGORA);

            assertThat(inverso.getCotacao())
                    .as("cambio real nao e reciproco: 1/0,185 seria 5,4054, e a cotacao e 5,40")
                    .isEqualByComparingTo(new BigDecimal("5.400000"))
                    .isNotEqualByComparingTo(BigDecimal.ONE.divide(new BigDecimal("0.185000"), 6,
                            java.math.RoundingMode.HALF_EVEN));
        }

        @Test
        @DisplayName("os dois sentidos sao linhas independentes, nao uma derivada da outra")
        void sentidosSaoIndependentes() {
            BigDecimal ida = cambio.cotacaoVigente("BRL", "USD", AGORA).getCotacao();
            BigDecimal volta = cambio.cotacaoVigente("USD", "BRL", AGORA).getCotacao();

            assertThat(ida.multiply(volta))
                    .as("se um fosse derivado do outro, o produto seria exatamente 1")
                    .isNotEqualByComparingTo(BigDecimal.ONE);
        }
    }

    @Nested
    @DisplayName("Conversao de valores")
    class ConversaoDeValores {

        @Test
        @DisplayName("converte usando a cotacao vigente e devolve qual foi usada")
        void converteEDevolveACotacaoAplicada() {
            Conversao resultado = cambio.converter(new BigDecimal("1000.00"), "BRL", "USD", AGORA);

            assertThat(resultado.valorConvertido())
                    .as("1000 BRL a 0,185 = 185,00 USD")
                    .isEqualByComparingTo(new BigDecimal("185.00"));
            assertThat(resultado.cotacaoAplicada())
                    .as("a operacao precisa gravar qual cotacao usou, para auditoria")
                    .isNotNull();
        }

        @Test
        @DisplayName("arredonda na escala da moeda de destino, lida do cadastro")
        void arredondaNaEscalaDaMoedaDeDestino() {
            Conversao resultado = cambio.converter(new BigDecimal("333.33"), "BRL", "USD", AGORA);

            assertThat(resultado.valorConvertido().scale())
                    .as("escala vem de moeda.escala_padrao, nao de constante no codigo")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("conversao sem cotacao disponivel propaga o erro de negocio")
        void conversaoSemCotacaoPropagaErro() {
            assertThatThrownBy(() -> cambio.converter(new BigDecimal("100.00"), "BRL", "USD",
                    OffsetDateTime.parse("2000-01-01T00:00:00Z")))
                    .isInstanceOf(CotacaoIndisponivelException.class);
        }
    }

    @Nested
    @DisplayName("Registro")
    class Registro {

        @Test
        @DisplayName("moeda desconhecida e erro de negocio, nao violacao de constraint")
        void moedaDesconhecidaEhErroDeNegocio() {
            assertThatThrownBy(() -> cambio.registrar("BRL", "XYZ", new BigDecimal("1.000000"),
                    AGORA, FonteCotacao.MANUAL))
                    .isInstanceOf(MoedaDesconhecidaException.class)
                    .hasMessageContaining("XYZ");
        }

        @Test
        @DisplayName("cotacao de uma moeda para ela mesma e recusada antes do banco")
        void moedaParaElaMesmaEhRecusada() {
            assertThatThrownBy(() -> cambio.registrar("BRL", "BRL", new BigDecimal("1.000000"),
                    AGORA, FonteCotacao.MANUAL))
                    .isInstanceOf(ParDeMoedasInvalidoException.class);
        }
    }
}
