package br.com.srm.creditengine.negocio.precificacao;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * Nenhuma regra de risco responde pelo tipo de recebivel.
 *
 * <p>Existe para que o sistema falhe alto em vez de assumir spread zero. Um
 * produto sem regra precificado com premio de risco nulo sai mais caro para o
 * fundo que qualquer erro de arredondamento, e nao deixa rastro.
 */
public class EstrategiaDeSpreadNaoEncontradaException extends ExcecaoDeNegocio {

    public EstrategiaDeSpreadNaoEncontradaException(String codigoDoTipo) {
        super("Nenhuma regra de risco cadastrada para o tipo de recebivel: %s".formatted(codigoDoTipo));
    }
}
