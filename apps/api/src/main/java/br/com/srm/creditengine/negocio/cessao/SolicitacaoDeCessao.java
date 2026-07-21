package br.com.srm.creditengine.negocio.cessao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pedido de cessao em termos do dominio, sem nada de HTTP.
 *
 * <p>Mesma razao do equivalente da simulacao: resolver codigo em entidade e'
 * acesso a persistencia, e a camada de aplicacao so alcanca a persistencia
 * direto nas rotas de relatorio.
 *
 * @param documentoCedente CNPJ de quem cede os titulos
 * @param titulos          lote a adquirir, na ordem enviada
 * @param moedaTitulo      moeda em que os titulos estao denominados
 * @param moedaLiquidacao  moeda em que o fundo desembolsa
 * @param dataOperacao     data-base da precificacao
 */
public record SolicitacaoDeCessao(
        String documentoCedente,
        List<TituloACeder> titulos,
        String moedaTitulo,
        String moedaLiquidacao,
        LocalDate dataOperacao) {

    public SolicitacaoDeCessao {
        titulos = List.copyOf(titulos);
    }

    public boolean isCrossCurrency() {
        return !moedaTitulo.equals(moedaLiquidacao);
    }

    /**
     * Um recebivel do lote.
     *
     * <p>Alem do que a simulacao pede, a cessao exige identificar o titulo:
     * numero do documento e sacado. Simular e' hipotese e nao precisa apontar
     * para papel nenhum; ceder e' aquisicao de um credito especifico contra um
     * devedor especifico.
     *
     * @param codigoTipoRecebivel codigo do produto
     * @param numeroDocumento     identificacao do titulo no cedente
     * @param documentoSacado     CPF ou CNPJ de quem deve
     * @param valorFace           valor nominal
     * @param dataVencimento      quando o sacado paga
     */
    public record TituloACeder(
            String codigoTipoRecebivel,
            String numeroDocumento,
            String documentoSacado,
            BigDecimal valorFace,
            LocalDate dataVencimento) {
    }
}
