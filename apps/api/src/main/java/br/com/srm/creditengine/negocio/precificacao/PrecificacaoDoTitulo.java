package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.persistencia.entidade.ParametroPrecificacao;

/**
 * Resultado da precificacao de um titulo, com todos os parametros usados.
 *
 * <p>Carrega os parametros congelados e nao apenas o valor presente, porque e'
 * isso que a operacao grava. Reprecificar no futuro com os parametros de entao
 * daria outro numero; a auditoria precisa reconstruir exatamente o que foi
 * contratado.
 *
 * <p>O desagio nao e' campo: e' {@code valorFace - valorPresente}, derivado.
 * Guardar dado derivado convida a divergencia entre a coluna e a conta.
 *
 * @param valorFace         valor nominal do titulo
 * @param valorPresente     quanto o fundo desembolsa, ja na escala da moeda
 * @param parametroAplicado registro de taxa base usado — a <b>linhagem</b>. O
 *                          valor vai congelado em {@code taxaBaseAplicada}; o
 *                          FK diz de qual registro ele veio, e um sem o outro
 *                          deixa a auditoria incompleta
 * @param taxaBaseAplicada  custo de oportunidade do fundo no momento
 * @param spreadAplicado    premio de risco produzido pela Strategy do produto
 * @param convencaoAplicada convencao que normalizou o prazo em expoente
 * @param expoenteAplicado  expoente efetivamente usado na formula
 */
public record PrecificacaoDoTitulo(
        BigDecimal valorFace,
        BigDecimal valorPresente,
        ParametroPrecificacao parametroAplicado,
        BigDecimal taxaBaseAplicada,
        BigDecimal spreadAplicado,
        ConvencaoContagem convencaoAplicada,
        BigDecimal expoenteAplicado) {

    /** Desconto aplicado ao valor de face. Derivado, nao persistido. */
    public BigDecimal desagio() {
        return valorFace.subtract(valorPresente);
    }

    /** Soma da taxa base com o spread — a taxa efetiva da operacao. */
    public BigDecimal taxaTotal() {
        return taxaBaseAplicada.add(spreadAplicado);
    }
}
