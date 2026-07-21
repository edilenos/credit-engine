package br.com.srm.creditengine.aplicacao.simulacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.negocio.precificacao.PrecificacaoDaOperacao;
import br.com.srm.creditengine.negocio.precificacao.PrecificacaoDoTitulo;

/**
 * Resultado da simulacao.
 *
 * <p>Devolve os parametros aplicados, nao so o valor final. O operador precisa
 * conferir <b>por que</b> o numero deu isso — qual spread a Strategy do produto
 * produziu, qual convencao normalizou o prazo, qual cotacao entrou — antes de
 * decidir efetivar. Resposta com um total e nada mais obrigaria a confiar sem
 * verificar.
 *
 * @param itens               resultado por titulo, na ordem do pedido
 * @param valorFaceTotal      soma dos valores nominais, na moeda do titulo
 * @param valorPresenteTotal  soma dos valores presentes, na moeda do titulo
 * @param desagioTotal        desconto total, na moeda do titulo
 * @param moedaTitulo         moeda em que os titulos estao denominados
 * @param moedaLiquidacao     moeda do desembolso
 * @param valorLiquidacao     quanto o fundo desembolsa, na moeda de liquidacao
 * @param cotacaoAplicada     cotacao usada, ou {@code null} em moeda unica
 * @param crossCurrency       se houve conversao
 * @param dataOperacao        data-base efetivamente usada, util quando omitida
 */
public record SimulacaoResponse(
        List<ItemSimulado> itens,
        BigDecimal valorFaceTotal,
        BigDecimal valorPresenteTotal,
        BigDecimal desagioTotal,
        String moedaTitulo,
        String moedaLiquidacao,
        BigDecimal valorLiquidacao,
        BigDecimal cotacaoAplicada,
        boolean crossCurrency,
        LocalDate dataOperacao) {

    public static SimulacaoResponse de(PrecificacaoDaOperacao precificacao,
                                       String moedaTitulo,
                                       String moedaLiquidacao,
                                       LocalDate dataOperacao) {
        List<PrecificacaoDoTitulo> titulos = precificacao.titulos();
        List<ItemSimulado> itens = IntStream.range(0, titulos.size())
                .mapToObj(indice -> ItemSimulado.de(indice, titulos.get(indice)))
                .toList();

        return new SimulacaoResponse(
                itens,
                precificacao.valorFaceTotal(),
                precificacao.valorPresenteTotal(),
                precificacao.desagioTotal(),
                moedaTitulo,
                moedaLiquidacao,
                precificacao.valorLiquidacao(),
                precificacao.isCrossCurrency()
                        ? precificacao.cotacaoAplicada().getCotacao()
                        : null,
                precificacao.isCrossCurrency(),
                dataOperacao);
    }

    /**
     * Precificacao de um titulo.
     *
     * <p>O {@code indice} espelha a posicao no pedido. A ordem ja e' preservada,
     * mas deixar implicito obrigaria o cliente a contar itens para casar
     * requisicao e resposta — e um lote de 500 nao perdoa erro de contagem.
     *
     * @param indice             posicao correspondente no lote enviado
     * @param valorFace          valor nominal
     * @param valorPresente      quanto vale hoje, na moeda do titulo
     * @param desagio            face menos presente
     * @param taxaBaseAplicada   custo de oportunidade do fundo
     * @param spreadAplicado     premio de risco da Strategy do produto
     * @param taxaTotal          soma efetivamente usada na formula
     * @param convencaoAplicada  convencao que normalizou o prazo
     * @param expoenteAplicado   expoente usado, ja na unidade da taxa
     */
    public record ItemSimulado(
            int indice,
            BigDecimal valorFace,
            BigDecimal valorPresente,
            BigDecimal desagio,
            BigDecimal taxaBaseAplicada,
            BigDecimal spreadAplicado,
            BigDecimal taxaTotal,
            ConvencaoContagem convencaoAplicada,
            BigDecimal expoenteAplicado) {

        private static ItemSimulado de(int indice, PrecificacaoDoTitulo titulo) {
            return new ItemSimulado(
                    indice,
                    titulo.valorFace(),
                    titulo.valorPresente(),
                    titulo.desagio(),
                    titulo.taxaBaseAplicada(),
                    titulo.spreadAplicado(),
                    titulo.taxaTotal(),
                    titulo.convencaoAplicada(),
                    titulo.expoenteAplicado());
        }
    }
}
