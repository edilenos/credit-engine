package br.com.srm.creditengine.dominio;

/**
 * Unidade de capitalizacao em que uma taxa e' cotada.
 *
 * <p>Precisa casar com a unidade que a convencao de contagem produz. Combinar
 * taxa {@code MENSAL} com uma convencao de saida anual nao lanca erro: produz
 * preco plausivel e errado por ordem de grandeza. E' o risco R7 do backlog, e a
 * validacao do par acontece no motor de precificacao (PBI-19).
 */
public enum Periodicidade {
    DIARIA,
    MENSAL,
    ANUAL
}
