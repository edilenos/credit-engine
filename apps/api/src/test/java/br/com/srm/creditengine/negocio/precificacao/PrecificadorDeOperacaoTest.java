package br.com.srm.creditengine.negocio.precificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.dominio.PrecisaoDecimal;
import br.com.srm.creditengine.negocio.cambio.CotacaoIndisponivelException;
import br.com.srm.creditengine.negocio.cambio.ServicoDeCambio;
import br.com.srm.creditengine.persistencia.repositorio.TipoRecebivelRepositorio;
import ch.obermuhlner.math.big.BigDecimalMath;

/**
 * Conversao cambial aplicada ao final (PBI-22).
 *
 * <p>O criterio de aceite central nao pede que a ordem esteja certa: pede um
 * teste que <b>demonstre</b> que a ordem invertida produz outro numero. Sem
 * isso, "converte no final" seria afirmacao, nao verificacao.
 */
@SpringBootTest
@Transactional
@DisplayName("Conversao cambial ao final")
class PrecificadorDeOperacaoTest {

    private static final LocalDate OPERACAO = LocalDate.of(2026, 7, 20);

    @Autowired private PrecificadorDeOperacao precificador;
    @Autowired private TipoRecebivelRepositorio tipos;

    @MockitoSpyBean private ServicoDeCambio cambio;

    private ContextoDePrecificacao titulo(String valorFace, int prazoEmDias) {
        return new ContextoDePrecificacao(
                tipos.findByCodigo("DUPLICATA_MERCANTIL").orElseThrow(),
                new BigDecimal(valorFace), OPERACAO, OPERACAO.plusDays(prazoEmDias));
    }

    @Nested
    @DisplayName("A ordem importa")
    class OrdemImporta {

        @Test
        @DisplayName("converter no fim da 178,66; converter no inicio daria 178,67")
        void converterNoFimDiferenteDeConverterNoInicio() {
            var resultado = precificador.precificar(
                    List.of(titulo("1003.00", 46)), "BRL", "USD", OPERACAO);

            assertThat(resultado.valorLiquidacao())
                    .as("ordem correta: desconta, arredonda, converte")
                    .isEqualByComparingTo(new BigDecimal("178.66"));

            assertThat(converterNoInicio(new BigDecimal("1003.00")))
                    .as("ordem invertida chega a outro numero — e por isso que o "
                            + "enunciado especifica quando converter")
                    .isEqualByComparingTo(new BigDecimal("178.67"))
                    .isNotEqualByComparingTo(resultado.valorLiquidacao());
        }

        /**
         * Reproduz a ordem que o enunciado proibe: converte o valor de face
         * primeiro, depois desconta. Algebricamente identico; a diferenca vem do
         * arredondamento intermediario.
         */
        private BigDecimal converterNoInicio(BigDecimal valorFace) {
            MathContext mc = PrecisaoDecimal.CONTEXTO;
            BigDecimal fator = BigDecimalMath.pow(
                    new BigDecimal("1.025"), new BigDecimal("1.5333333333"), mc);

            BigDecimal faceConvertido = PrecisaoDecimal.comoMoeda(
                    valorFace.multiply(new BigDecimal("0.185000"), mc));

            return PrecisaoDecimal.comoMoeda(PrecisaoDecimal.dividir(faceConvertido, fator));
        }

        @Test
        @DisplayName("converte o total uma vez: tres titulos de 1.000 dao 534,38, nao 534,39")
        void converteOTotalUmaVez() {
            // As duas ordens nem sempre divergem — com muitos valores os
            // arredondamentos individuais se cancelam. Este lote foi encontrado
            // por busca, nao presumido: a primeira versao do teste usava tres
            // valores quaisquer e passou por acaso, afirmando uma divergencia
            // que nao acontecia ali.
            List<ContextoDePrecificacao> lote = List.of(
                    titulo("1000.00", 46), titulo("1000.00", 46), titulo("1000.00", 46));

            var resultado = precificador.precificar(lote, "BRL", "USD", OPERACAO);

            BigDecimal somaDasConversoesIndividuais = resultado.titulos().stream()
                    .map(t -> PrecisaoDecimal.comoMoeda(
                            t.valorPresente().multiply(new BigDecimal("0.185000"),
                                    PrecisaoDecimal.CONTEXTO)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(resultado.valorLiquidacao())
                    .as("converter a soma acumula um arredondamento; converter N titulos "
                            + "acumula N")
                    .isEqualByComparingTo(new BigDecimal("534.38"));
            assertThat(somaDasConversoesIndividuais)
                    .isEqualByComparingTo(new BigDecimal("534.39"))
                    .isNotEqualByComparingTo(resultado.valorLiquidacao());
        }
    }

    @Nested
    @DisplayName("Cross-currency")
    class CrossCurrency {

        @Test
        @DisplayName("grava a cotacao aplicada, sem a qual a auditoria nao reconstroi o valor")
        void gravaACotacaoAplicada() {
            var resultado = precificador.precificar(
                    List.of(titulo("100000.00", 46)), "BRL", "USD", OPERACAO);

            assertThat(resultado.isCrossCurrency()).isTrue();
            assertThat(resultado.cotacaoAplicada()).isNotNull();
            assertThat(resultado.cotacaoAplicada().getCotacao())
                    .isEqualByComparingTo(new BigDecimal("0.185000"));
        }

        @Test
        @DisplayName("o valor presente permanece na moeda do titulo; so a liquidacao converte")
        void valorPresenteFicaNaMoedaDoTitulo() {
            var resultado = precificador.precificar(
                    List.of(titulo("100000.00", 46)), "BRL", "USD", OPERACAO);

            assertThat(resultado.valorPresenteTotal())
                    .as("em BRL, como o titulo")
                    .isEqualByComparingTo(new BigDecimal("96284.58"));
            assertThat(resultado.valorLiquidacao())
                    .as("em USD: 96284,58 x 0,185")
                    .isEqualByComparingTo(new BigDecimal("17812.65"));
        }

        @Test
        @DisplayName("par sem cotacao bloqueia a operacao em vez de aproximar")
        void parSemCotacaoBloqueiaAOperacao() {
            assertThatThrownBy(() -> precificador.precificar(
                    List.of(titulo("100000.00", 46)), "BRL", "USD",
                    LocalDate.of(2000, 1, 1)))
                    .isInstanceOf(CotacaoIndisponivelException.class);
        }
    }

    @Nested
    @DisplayName("Moeda unica")
    class MoedaUnica {

        @Test
        @DisplayName("nao consulta o cambio nem grava cotacao")
        void naoConsultaOCambio() {
            var resultado = precificador.precificar(
                    List.of(titulo("100000.00", 46)), "BRL", "BRL", OPERACAO);

            assertThat(resultado.isCrossCurrency()).isFalse();
            assertThat(resultado.cotacaoAplicada()).isNull();
            verify(cambio, never())
                    .converter(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("liquidacao e igual ao valor presente, sem conversao no meio")
        void liquidacaoIgualAoValorPresente() {
            var resultado = precificador.precificar(
                    List.of(titulo("100000.00", 46)), "BRL", "BRL", OPERACAO);

            assertThat(resultado.valorLiquidacao())
                    .isEqualByComparingTo(resultado.valorPresenteTotal())
                    .isEqualByComparingTo(new BigDecimal("96284.58"));
        }
    }

    @Nested
    @DisplayName("Lote")
    class Lote {

        @Test
        @DisplayName("soma face e presente de todos os titulos")
        void somaOsTitulos() {
            var resultado = precificador.precificar(
                    List.of(titulo("100000.00", 46), titulo("50000.00", 46)),
                    "BRL", "BRL", OPERACAO);

            assertThat(resultado.titulos()).hasSize(2);
            assertThat(resultado.valorFaceTotal()).isEqualByComparingTo(new BigDecimal("150000.00"));
            assertThat(resultado.desagioTotal())
                    .isEqualByComparingTo(
                            resultado.valorFaceTotal().subtract(resultado.valorPresenteTotal()));
        }

        @Test
        @DisplayName("lote vazio e recusado, nao vira operacao de valor zero")
        void loteVazioEhRecusado() {
            assertThatThrownBy(() -> precificador.precificar(List.of(), "BRL", "BRL", OPERACAO))
                    .isInstanceOf(LoteVazioException.class);
        }

        @Test
        @DisplayName("titulos com prazos diferentes sao descontados cada um pelo seu")
        void prazosDiferentesPorTitulo() {
            var resultado = precificador.precificar(
                    List.of(titulo("100000.00", 46), titulo("100000.00", 0)),
                    "BRL", "BRL", OPERACAO);

            assertThat(resultado.titulos().get(1).valorPresente())
                    .as("prazo zero nao sofre desconto, mesmo dentro de um lote")
                    .isEqualByComparingTo(new BigDecimal("100000.00"));
            assertThat(resultado.titulos().get(0).valorPresente())
                    .isLessThan(resultado.titulos().get(1).valorPresente());
        }
    }

    @Nested
    @DisplayName("Precisao")
    class Precisao {

        @Test
        @DisplayName("o valor liquidado sai na escala da moeda de destino")
        void escalaDaMoedaDeDestino() {
            var resultado = precificador.precificar(
                    List.of(titulo("1003.00", 46)), "BRL", "USD", OPERACAO);

            assertThat(resultado.valorLiquidacao().scale())
                    .as("escala vem de moeda.escala_padrao, nao de constante")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("nenhum arredondamento intermediario alem dos declarados")
        void semArredondamentoIntermediarioExtra() {
            var resultado = precificador.precificar(
                    List.of(titulo("100000.00", 46)), "BRL", "USD", OPERACAO);

            BigDecimal esperado = PrecisaoDecimal.comoMoeda(
                    resultado.valorPresenteTotal()
                            .multiply(resultado.cotacaoAplicada().getCotacao(),
                                    PrecisaoDecimal.CONTEXTO));

            assertThat(resultado.valorLiquidacao())
                    .as("liquidacao e exatamente presente x cotacao, arredondado uma vez")
                    .isEqualByComparingTo(esperado);
        }
    }
}
