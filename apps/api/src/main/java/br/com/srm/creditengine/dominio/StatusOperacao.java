package br.com.srm.creditengine.dominio;

/**
 * Ciclo de vida da operacao. {@code LIQUIDADA} e {@code CANCELADA} sao
 * terminais: so {@code PENDENTE} aceita liquidacao.
 */
public enum StatusOperacao {
    PENDENTE,
    LIQUIDADA,
    CANCELADA;

    public boolean permiteLiquidacao() {
        return this == PENDENTE;
    }
}
