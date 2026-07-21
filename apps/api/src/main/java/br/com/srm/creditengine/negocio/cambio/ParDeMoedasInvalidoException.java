package br.com.srm.creditengine.negocio.cambio;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/** Cotacao de uma moeda para ela mesma nao e' cotacao. */
public class ParDeMoedasInvalidoException extends ExcecaoDeNegocio {

    public ParDeMoedasInvalidoException(String codigo) {
        super("Origem e destino sao a mesma moeda: %s".formatted(codigo));
    }
}
