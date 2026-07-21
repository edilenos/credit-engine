package br.com.srm.creditengine.negocio.liquidacao;

import br.com.srm.creditengine.negocio.RecursoNaoEncontradoException;

/** A operacao existe mas ainda nao foi liquidada. */
public class LiquidacaoNaoEncontradaException extends RecursoNaoEncontradoException {

    public LiquidacaoNaoEncontradaException(Long operacaoId) {
        super("Operacao " + operacaoId + " ainda nao foi liquidada");
    }
}
