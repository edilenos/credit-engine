package br.com.srm.creditengine.relatorio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Uma liquidacao no extrato.
 *
 * <p>Projecao de leitura, nao entidade: os campos vem de quatro tabelas e nao
 * correspondem a nenhum agregado do dominio. Mapear isto com JPA exigiria uma
 * entidade que existe so para o relatorio, ou navegar associacoes LAZY linha a
 * linha — que e' o N+1 que o SQL nativo evita.
 *
 * @param liquidacaoId       identificador da liquidacao
 * @param operacaoId         operacao correspondente
 * @param liquidadoEm        quando foi liquidada
 * @param liquidadoPor       quem autorizou
 * @param documentoCedente   CNPJ do cedente
 * @param razaoSocialCedente nome do cedente
 * @param moedaTitulo        moeda em que os titulos estavam denominados
 * @param moedaLiquidacao    moeda do desembolso
 * @param valorFaceTotal     soma dos nominais, na moeda do titulo
 * @param valorPresenteTotal soma dos valores presentes, na moeda do titulo
 * @param desagioTotal       face menos presente
 * @param valorLiquidado     desembolso, na moeda de liquidacao
 * @param cotacaoAplicada    cotacao congelada, ou null em moeda unica
 * @param operacaoCriadaEm   quando a cessao foi registrada. A data-base da
 *                           precificacao nao e' coluna: entrou no calculo e
 *                           saiu congelada no expoente de cada recebivel
 */
public record LinhaDoExtrato(
        Long liquidacaoId,
        Long operacaoId,
        OffsetDateTime liquidadoEm,
        String liquidadoPor,
        String documentoCedente,
        String razaoSocialCedente,
        String moedaTitulo,
        String moedaLiquidacao,
        BigDecimal valorFaceTotal,
        BigDecimal valorPresenteTotal,
        BigDecimal desagioTotal,
        BigDecimal valorLiquidado,
        BigDecimal cotacaoAplicada,
        OffsetDateTime operacaoCriadaEm) {
}
