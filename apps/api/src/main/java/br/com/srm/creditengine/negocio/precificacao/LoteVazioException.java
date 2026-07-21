package br.com.srm.creditengine.negocio.precificacao;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * Operacao sem nenhum recebivel.
 *
 * <p>Nao ha o que precificar nem o que ceder. Aceitar em silencio criaria uma
 * operacao de valor zero, que passaria pelas constraints de nao negatividade e
 * so apareceria depois como linha estranha no extrato.
 */
public class LoteVazioException extends ExcecaoDeNegocio {

    public LoteVazioException() {
        super("A operacao precisa conter ao menos um recebivel");
    }
}
