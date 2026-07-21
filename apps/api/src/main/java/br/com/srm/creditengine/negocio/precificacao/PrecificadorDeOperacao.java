package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.dominio.PrecisaoDecimal;
import br.com.srm.creditengine.negocio.cambio.Conversao;
import br.com.srm.creditengine.negocio.cambio.ServicoDeCambio;
import br.com.srm.creditengine.negocio.observabilidade.MetricasDeNegocio;

/**
 * Precifica um lote de recebiveis e aplica a conversao cambial <b>no final</b>.
 *
 * <h2>A ordem e' requisito, nao detalhe</h2>
 *
 * O paragrafo 3.2 do enunciado e' explicito: em operacao cross-currency,
 * "aplicar a conversao cambial no final". Algebricamente
 * {@code (VF x c) / (1+i)^n} e {@code (VF / (1+i)^n) x c} sao a mesma coisa —
 * a diferenca esta no <b>arredondamento intermediario</b>, e ela e' real.
 *
 * <p>Com fator 1,0385879 e cotacao 0,185, um titulo de R$ 1.003,00 vale
 * USD 178,66 convertendo no fim e USD 178,67 convertendo no inicio. Um centavo
 * por titulo, numa carteira de milhares, com sinal sistematico. Ha teste
 * fixando exatamente esse caso.
 *
 * <h2>Converte o total uma vez, nao titulo a titulo</h2>
 *
 * Cada conversao arredonda. Converter N titulos individualmente acumula N
 * arredondamentos; converter a soma acumula um. "No final" tambem significa
 * isso — e o modelo de dados ja tinha decidido nesse sentido:
 * {@code operacao.valor_liquidacao} e' o total convertido, e o recebivel nao
 * tem coluna equivalente.
 *
 * <h2>Moeda unica nao encosta no cambio</h2>
 *
 * Operacao BRL para BRL nao consulta cotacao nem grava nenhuma. Buscar uma
 * cotacao de BRL para BRL levantaria erro de negocio — e com razao: nao existe.
 */
@Service
public class PrecificadorDeOperacao {

    private final MotorDePrecificacao motor;
    private final ServicoDeCambio cambio;
    private final MetricasDeNegocio metricas;

    public PrecificadorDeOperacao(MotorDePrecificacao motor, ServicoDeCambio cambio,
                                  MetricasDeNegocio metricas) {
        this.motor = motor;
        this.cambio = cambio;
        this.metricas = metricas;
    }

    /**
     * Precifica o lote e devolve o valor a desembolsar na moeda de liquidacao.
     *
     * @throws LoteVazioException se nao houver titulos
     * @throws br.com.srm.creditengine.negocio.cambio.CotacaoIndisponivelException
     *         se a operacao for cross-currency e nao houver cotacao vigente para
     *         o par — a operacao e' bloqueada, nao precificada por aproximacao
     */
    @Transactional(readOnly = true)
    public PrecificacaoDaOperacao precificar(List<ContextoDePrecificacao> titulos,
                                             String moedaTitulo,
                                             String moedaLiquidacao,
                                             LocalDate dataOperacao) {
        if (titulos.isEmpty()) {
            throw new LoteVazioException();
        }

        // Cronometrado a mao em vez de @Timed: a anotacao mede a chamada
        // inteira, incluindo as recusas, e um lote rejeitado no primeiro titulo
        // entraria como precificacao rapida, puxando o percentil para baixo.
        long inicio = System.nanoTime();

        List<PrecificacaoDoTitulo> precificados = titulos.stream()
                .map(motor::precificar)
                .toList();

        metricas.registrarTempoDePrecificacao(System.nanoTime() - inicio);

        BigDecimal faceTotal = exigirRepresentavel("Valor de face total",
                somar(precificados, PrecificacaoDoTitulo::valorFace));
        BigDecimal presenteTotal = exigirRepresentavel("Valor presente total",
                somar(precificados, PrecificacaoDoTitulo::valorPresente));

        if (moedaTitulo.equals(moedaLiquidacao)) {
            return new PrecificacaoDaOperacao(
                    precificados, faceTotal, presenteTotal, presenteTotal, null);
        }

        // Aqui, e so aqui, o cambio entra: depois de todo o desconto calculado.
        // O resultado e' checado de novo porque a conversao pode levar o total
        // para fora da faixa mesmo com o presente dentro dela — basta a cotacao
        // ser maior que 1.
        OffsetDateTime momento = dataOperacao.atStartOfDay().atOffset(ZoneOffset.UTC);
        Conversao conversao = cambio.converter(presenteTotal, moedaTitulo, moedaLiquidacao, momento);

        return new PrecificacaoDaOperacao(precificados, faceTotal, presenteTotal,
                exigirRepresentavel("Valor de liquidacao", conversao.valorConvertido()),
                conversao.cotacaoAplicada());
    }

    /**
     * O total precisa caber no que o sistema representa.
     *
     * <p>A validacao por item nao cobre isto: cada titulo cabe em dezessete
     * digitos, a soma de quinhentos nao. Sem a checagem, o estouro so aparecia
     * no {@code INSERT}, como erro de integridade do Postgres — e o tratamento
     * generico o traduzia em "a operacao foi alterada por outra requisicao",
     * mandando o cliente repetir algo que falharia identicamente para sempre.
     *
     * <p>Vale tambem para a simulacao, que nao grava nada: prever um total que
     * a efetivacao vai recusar e' pior do que recusar na hora.
     */
    private BigDecimal exigirRepresentavel(String oQue, BigDecimal valor) {
        if (valor.compareTo(PrecisaoDecimal.VALOR_MAXIMO) > 0) {
            throw new ValorForaDeFaixaException(oQue, valor);
        }
        return valor;
    }

    private BigDecimal somar(List<PrecificacaoDoTitulo> titulos,
                             java.util.function.Function<PrecificacaoDoTitulo, BigDecimal> campo) {
        return titulos.stream()
                .map(campo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
