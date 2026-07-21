package br.com.srm.creditengine.negocio.cessao;

import br.com.srm.creditengine.negocio.RecursoNaoEncontradoException;

/** A operacao pedida nao existe. */
public class OperacaoNaoEncontradaException extends RecursoNaoEncontradoException {

    public OperacaoNaoEncontradaException(Long id) {
        super("Operacao nao encontrada: " + id);
    }
}
