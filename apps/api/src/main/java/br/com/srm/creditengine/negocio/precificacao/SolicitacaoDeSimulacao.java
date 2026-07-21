package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pedido de simulacao em termos do dominio, sem nada de HTTP.
 *
 * <p>Existe para que o controller nao precise resolver codigos em entidades:
 * essa resolucao e' acesso a persistencia, e a camada de aplicacao so alcanca a
 * persistencia direto nas rotas de relatorio (§3.6 do enunciado), que nao e' o
 * caso aqui.
 *
 * @param titulos          recebiveis a precificar, na ordem em que foram pedidos
 * @param moedaTitulo      moeda em que os titulos estao denominados
 * @param moedaLiquidacao  moeda em que o fundo desembolsa
 * @param dataOperacao     data-base da precificacao
 */
public record SolicitacaoDeSimulacao(
        List<TituloASimular> titulos,
        String moedaTitulo,
        String moedaLiquidacao,
        LocalDate dataOperacao) {

    public SolicitacaoDeSimulacao {
        titulos = List.copyOf(titulos);
    }

    public boolean isCrossCurrency() {
        return !moedaTitulo.equals(moedaLiquidacao);
    }

    /**
     * Um recebivel do lote.
     *
     * @param codigoTipoRecebivel codigo do produto, resolvido contra o cadastro
     * @param valorFace           valor nominal
     * @param dataVencimento      quando o sacado paga
     */
    public record TituloASimular(
            String codigoTipoRecebivel,
            BigDecimal valorFace,
            LocalDate dataVencimento) {
    }
}
