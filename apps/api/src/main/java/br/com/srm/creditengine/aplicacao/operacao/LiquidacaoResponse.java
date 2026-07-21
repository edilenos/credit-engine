package br.com.srm.creditengine.aplicacao.operacao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import br.com.srm.creditengine.persistencia.entidade.Liquidacao;

/**
 * Comprovante de liquidacao.
 *
 * @param id                identificador da liquidacao
 * @param operacaoId        operacao liquidada
 * @param chaveIdempotencia chave usada; repeti-la devolve este mesmo corpo
 * @param valorLiquidado    quanto foi desembolsado, na moeda de liquidacao
 * @param cotacaoAplicada   cotacao congelada, ou null em moeda unica
 * @param liquidadoEm       quando
 * @param liquidadoPor      quem autorizou
 */
public record LiquidacaoResponse(
        Long id,
        Long operacaoId,
        String chaveIdempotencia,
        BigDecimal valorLiquidado,
        BigDecimal cotacaoAplicada,
        OffsetDateTime liquidadoEm,
        String liquidadoPor) {

    public static LiquidacaoResponse de(Liquidacao liquidacao) {
        return new LiquidacaoResponse(
                liquidacao.getId(),
                liquidacao.getOperacao().getId(),
                liquidacao.getChaveIdempotencia(),
                liquidacao.getValorLiquidado(),
                liquidacao.getCotacaoAplicada(),
                liquidacao.getLiquidadoEm(),
                liquidacao.getLiquidadoPor());
    }
}
