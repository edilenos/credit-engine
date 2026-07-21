package br.com.srm.creditengine.dominio;

/**
 * Entidades que a trilha cobre.
 *
 * <p>Espelha o {@code CHECK (entidade IN (...))} de {@code evento_auditoria}.
 * Constante nova aqui sem migracao correspondente faz o INSERT falhar — que e'
 * o comportamento desejado: melhor quebrar na gravacao do que gravar um valor
 * que ninguem consegue interpretar depois.
 */
public enum EntidadeAuditada {
    OPERACAO,
    LIQUIDACAO
}
