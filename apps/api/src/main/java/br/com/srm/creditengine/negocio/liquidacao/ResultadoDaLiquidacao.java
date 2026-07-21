package br.com.srm.creditengine.negocio.liquidacao;

import br.com.srm.creditengine.persistencia.entidade.Liquidacao;

/**
 * Liquidacao efetuada, com a informacao de se ela aconteceu agora.
 *
 * <p>{@code replay} distingue "liquidei" de "ja estava liquidada com esta
 * chave". As duas devolvem o mesmo corpo — e' isso que idempotencia significa —
 * mas o cliente merece saber qual das duas foi, e a camada de aplicacao usa o
 * sinal para escolher entre {@code 201} e {@code 200}.
 *
 * @param liquidacao registro, novo ou recuperado
 * @param replay     verdadeiro quando a chave ja tinha sido usada nesta operacao
 */
public record ResultadoDaLiquidacao(Liquidacao liquidacao, boolean replay) {

    static ResultadoDaLiquidacao nova(Liquidacao liquidacao) {
        return new ResultadoDaLiquidacao(liquidacao, false);
    }

    static ResultadoDaLiquidacao repetida(Liquidacao liquidacao) {
        return new ResultadoDaLiquidacao(liquidacao, true);
    }
}
