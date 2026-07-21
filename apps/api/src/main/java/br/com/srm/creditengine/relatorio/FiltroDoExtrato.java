package br.com.srm.creditengine.relatorio;

import java.time.LocalDate;

/**
 * Filtros do extrato, todos opcionais e combinaveis.
 *
 * <p>Nulo significa "nao filtra por isto". A consulta monta o {@code WHERE} a
 * partir dos que vieram preenchidos, sempre com parametro nomeado — o valor do
 * usuario nunca entra no texto do SQL.
 *
 * @param de               inicio do periodo, inclusivo
 * @param ate              fim do periodo, inclusivo — o dia inteiro conta
 * @param documentoCedente CNPJ do cedente
 * @param moedaLiquidacao  codigo ISO da moeda de desembolso
 * @param ordenacao        coluna, escolhida em lista branca
 * @param direcao          ASC ou DESC
 * @param pagina           base zero
 * @param tamanho          itens por pagina, limitado por {@link #TAMANHO_MAXIMO}
 */
public record FiltroDoExtrato(
        LocalDate de,
        LocalDate ate,
        String documentoCedente,
        String moedaLiquidacao,
        OrdenacaoDoExtrato ordenacao,
        OrdenacaoDoExtrato.Direcao direcao,
        int pagina,
        int tamanho) {

    public static final int TAMANHO_PADRAO = 20;

    /**
     * Teto de pagina.
     *
     * <p>Sem ele, {@code tamanho=1000000} devolve a tabela inteira e a
     * paginacao vira decoracao — o criterio de aceite pede que <b>nao exista</b>
     * caminho que retorne a colecao completa.
     */
    public static final int TAMANHO_MAXIMO = 100;

    public FiltroDoExtrato {
        ordenacao = ordenacao != null ? ordenacao : OrdenacaoDoExtrato.LIQUIDADO_EM;
        direcao = direcao != null ? direcao : OrdenacaoDoExtrato.Direcao.DESC;
        pagina = Math.max(pagina, 0);
        tamanho = Math.clamp(tamanho <= 0 ? TAMANHO_PADRAO : tamanho, 1, TAMANHO_MAXIMO);

        documentoCedente = vazioComoNulo(documentoCedente);
        moedaLiquidacao = vazioComoNulo(moedaLiquidacao);
    }

    /**
     * String vazia e' o que um formulario manda quando o campo nao foi
     * preenchido; tratar como filtro produziria zero resultados sem motivo
     * aparente.
     */
    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    int deslocamento() {
        return pagina * tamanho;
    }
}
