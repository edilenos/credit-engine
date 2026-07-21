package br.com.srm.creditengine.negocio.cambio;

import br.com.srm.creditengine.negocio.RecursoNaoEncontradoException;

/** Codigo de moeda que nao existe no cadastro. */
public class MoedaDesconhecidaException extends RecursoNaoEncontradoException {

    private final String codigo;

    public MoedaDesconhecidaException(String codigo) {
        super("Moeda desconhecida: %s".formatted(codigo));
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
