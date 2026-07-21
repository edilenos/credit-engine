package br.com.srm.creditengine.negocio.cambio;

import java.math.BigDecimal;

import br.com.srm.creditengine.persistencia.entidade.TaxaCambio;

/**
 * Resultado de uma conversao cambial.
 *
 * <p>Carrega a cotacao usada, e nao apenas o valor convertido, porque a
 * operacao precisa gravar qual cotacao foi aplicada. Sem isso, reprecificar no
 * futuro daria outro numero e a auditoria nao teria como reconstruir o que foi
 * contratado.
 *
 * @param valorConvertido valor na moeda de destino, ja arredondado na escala dela
 * @param cotacaoAplicada linha de cotacao efetivamente usada
 */
public record Conversao(BigDecimal valorConvertido, TaxaCambio cotacaoAplicada) {
}
