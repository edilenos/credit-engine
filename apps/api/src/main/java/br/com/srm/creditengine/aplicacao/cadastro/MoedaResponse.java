package br.com.srm.creditengine.aplicacao.cadastro;

import br.com.srm.creditengine.persistencia.entidade.Moeda;

/**
 * Moeda na API.
 *
 * @param codigo       ISO 4217
 * @param nome         rotulo para exibicao
 * @param escalaPadrao casas decimais da moeda, para o cliente formatar
 */
public record MoedaResponse(String codigo, String nome, Short escalaPadrao) {

    public static MoedaResponse de(Moeda moeda) {
        return new MoedaResponse(moeda.getCodigo(), moeda.getNome(), moeda.getEscalaPadrao());
    }
}
