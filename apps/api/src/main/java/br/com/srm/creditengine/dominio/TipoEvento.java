package br.com.srm.creditengine.dominio;

/**
 * Fatos que a trilha de auditoria registra.
 *
 * <p>Nomes no passado de proposito: evento e' o que <b>aconteceu</b>, nao o que
 * se pretende fazer. A trilha nao e' fila de comandos.
 */
public enum TipoEvento {
    OPERACAO_REGISTRADA,
    OPERACAO_LIQUIDADA
}
