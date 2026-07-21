package br.com.srm.creditengine.negocio.cambio;

import br.com.srm.creditengine.persistencia.entidade.TaxaCambio;

/**
 * Resultado de uma sincronizacao com o provedor externo.
 *
 * @param cotacao   cotacao resultante — recem-obtida ou a ultima conhecida
 * @param degradado {@code true} quando o provedor falhou e a resposta veio do
 *                  historico. Nao e' detalhe de log: quem consome precisa saber
 *                  que esta operando com dado que pode estar velho, e a decisao
 *                  de aceitar ou nao e' de quem chama
 * @param motivo    quando degradado, o que impediu a atualizacao
 */
public record ResultadoSincronizacao(TaxaCambio cotacao, boolean degradado, String motivo) {

    public static ResultadoSincronizacao atualizada(TaxaCambio cotacao) {
        return new ResultadoSincronizacao(cotacao, false, null);
    }

    public static ResultadoSincronizacao degradada(TaxaCambio ultimaConhecida, String motivo) {
        return new ResultadoSincronizacao(ultimaConhecida, true, motivo);
    }
}
