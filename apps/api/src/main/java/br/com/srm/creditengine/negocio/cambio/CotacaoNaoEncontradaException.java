package br.com.srm.creditengine.negocio.cambio;

import br.com.srm.creditengine.negocio.RecursoNaoEncontradoException;

/** Identificador de cotacao que nao existe. */
public class CotacaoNaoEncontradaException extends RecursoNaoEncontradoException {

    public CotacaoNaoEncontradaException(Long id) {
        super("Cotacao nao encontrada: %s".formatted(id));
    }
}
