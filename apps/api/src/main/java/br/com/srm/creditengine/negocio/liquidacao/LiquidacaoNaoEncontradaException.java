package br.com.srm.creditengine.negocio.liquidacao;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/** A operacao existe mas ainda nao foi liquidada. */
public class LiquidacaoNaoEncontradaException extends ExcecaoDeNegocio {

    public LiquidacaoNaoEncontradaException(Long operacaoId) {
        super("Operacao " + operacaoId + " ainda nao foi liquidada");
    }
}
