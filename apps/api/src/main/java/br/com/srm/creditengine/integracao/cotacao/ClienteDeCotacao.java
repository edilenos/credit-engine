package br.com.srm.creditengine.integracao.cotacao;

import java.math.BigDecimal;

/**
 * Chamada crua ao provedor externo de cotacao.
 *
 * <p>Interface separada de proposito: e' o ponto onde a resiliencia e'
 * aplicada por fora ({@link ProvedorDeCotacao}) e onde o teste substitui a rede
 * por um duble. Sem essa fronteira, testar retry e circuit breaker exigiria
 * derrubar um servidor de verdade.
 */
public interface ClienteDeCotacao {

    /**
     * @return cotacao corrente do par, em unidades de destino por unidade de origem
     * @throws ProvedorIndisponivelException em qualquer falha de comunicacao
     */
    BigDecimal buscar(String moedaOrigem, String moedaDestino);
}
