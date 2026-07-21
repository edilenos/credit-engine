package br.com.srm.creditengine.negocio.cessao;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/** A operacao pedida nao existe. */
public class OperacaoNaoEncontradaException extends ExcecaoDeNegocio {

    public OperacaoNaoEncontradaException(Long id) {
        super("Operacao nao encontrada: " + id);
    }
}
