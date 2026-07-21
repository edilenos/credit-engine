package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;

import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;

/**
 * Regra de risco de um tipo de recebivel — o padrao Strategy que o paragrafo
 * 3.2 do enunciado exige.
 *
 * <p>Cada implementacao responde por um produto e sabe apenas produzir o
 * premio de risco. <b>Nenhuma conhece a formula do valor presente</b>: quem
 * compoe taxa base, spread e expoente e' o motor de calculo (PBI-21). E' essa
 * fronteira que torna o padrao util — trocar a regra de risco de um produto
 * nao toca no calculo, e adicionar um produto nao toca em nada.
 *
 * <p>Uma regra pode recusar a operacao em vez de precificar. Risco que a mesa
 * nao aceita nao tem preco, e devolver um spread alto no lugar de recusar
 * esconderia a decisao dentro de um numero.
 */
public interface EstrategiaDeSpread {

    /** Se esta regra responde pelo produto informado. */
    boolean suporta(TipoRecebivel tipo);

    /**
     * Premio de risco a somar a taxa base.
     *
     * @throws PrazoInviavelException quando o produto nao admite o prazo pedido
     */
    BigDecimal calcular(ContextoDePrecificacao contexto);
}
