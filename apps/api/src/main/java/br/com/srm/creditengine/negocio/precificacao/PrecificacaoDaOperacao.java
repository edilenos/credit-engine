package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;
import java.util.List;

import br.com.srm.creditengine.persistencia.entidade.TaxaCambio;

/**
 * Resultado da precificacao de um lote inteiro, ja com a conversao cambial
 * aplicada quando a operacao e' cross-currency.
 *
 * @param titulos             precificacao individual de cada recebivel, na moeda do titulo
 * @param valorFaceTotal      soma dos valores de face
 * @param valorPresenteTotal  soma dos valores presentes, na moeda do TITULO
 * @param valorLiquidacao     valor a desembolsar, na moeda de LIQUIDACAO. Igual
 *                            ao valor presente quando a operacao e' em moeda unica
 * @param cotacaoAplicada     cotacao usada na conversao, ou {@code null} em moeda
 *                            unica. Fica gravada na operacao: sem ela a auditoria
 *                            nao reconstroi o valor contratado
 */
public record PrecificacaoDaOperacao(
        List<PrecificacaoDoTitulo> titulos,
        BigDecimal valorFaceTotal,
        BigDecimal valorPresenteTotal,
        BigDecimal valorLiquidacao,
        TaxaCambio cotacaoAplicada) {

    public PrecificacaoDaOperacao {
        titulos = List.copyOf(titulos);
    }

    /** Desconto total sobre o valor de face, na moeda do titulo. */
    public BigDecimal desagioTotal() {
        return valorFaceTotal.subtract(valorPresenteTotal);
    }

    public boolean isCrossCurrency() {
        return cotacaoAplicada != null;
    }
}
