package br.com.srm.creditengine.aplicacao.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.StatusOperacao;
import br.com.srm.creditengine.persistencia.entidade.Operacao;
import br.com.srm.creditengine.persistencia.entidade.Recebivel;

/**
 * Operacao registrada, na API.
 *
 * <p>Devolve os parametros congelados de cada recebivel, nao so os totais: e' o
 * comprovante do que foi contratado. Reprecificar amanha com os parametros de
 * entao daria outro numero, e a diferenca so seria explicavel com estes campos.
 *
 * @param id                  identificador, usado no Location
 * @param status              PENDENTE ao nascer; muda com a liquidacao (PBI-28)
 * @param documentoCedente    contraparte da cessao
 * @param moedaTitulo         moeda dos titulos
 * @param moedaLiquidacao     moeda do desembolso
 * @param valorFaceTotal      soma dos nominais
 * @param valorPresenteTotal  soma dos valores presentes, na moeda do titulo
 * @param desagioTotal        desconto total
 * @param valorLiquidacao     desembolso, na moeda de liquidacao
 * @param cotacaoAplicada     cotacao usada, ou null em moeda unica
 * @param criadoEm            quando a operacao foi registrada
 * @param recebiveis          o lote, na ordem em que foi enviado
 */
public record OperacaoResponse(
        Long id,
        StatusOperacao status,
        String documentoCedente,
        String moedaTitulo,
        String moedaLiquidacao,
        BigDecimal valorFaceTotal,
        BigDecimal valorPresenteTotal,
        BigDecimal desagioTotal,
        BigDecimal valorLiquidacao,
        BigDecimal cotacaoAplicada,
        OffsetDateTime criadoEm,
        List<RecebivelResponse> recebiveis) {

    public static OperacaoResponse de(Operacao operacao) {
        return new OperacaoResponse(
                operacao.getId(),
                operacao.getStatus(),
                operacao.getCedente().getDocumento(),
                operacao.getMoedaTitulo().getCodigo(),
                operacao.getMoedaLiquidacao().getCodigo(),
                operacao.getValorFaceTotal(),
                operacao.getValorPresenteTotal(),
                operacao.getDesagio(),
                operacao.getValorLiquidacao(),
                operacao.getTaxaCambio() != null ? operacao.getTaxaCambio().getCotacao() : null,
                operacao.getCriadoEm(),
                operacao.getRecebiveis().stream().map(RecebivelResponse::de).toList());
    }

    /**
     * Um titulo do lote, com o que foi aplicado a ele.
     *
     * @param numeroDocumento    identificacao no cedente
     * @param documentoSacado    quem deve
     * @param tipoRecebivel      codigo do produto
     * @param valorFace          nominal
     * @param valorPresente      quanto o fundo pagou por ele
     * @param desagio            face menos presente
     * @param dataVencimento     quando o sacado paga
     * @param convencaoAplicada  convencao que normalizou o prazo
     * @param expoenteAplicado   expoente usado
     * @param taxaBaseAplicada   taxa base congelada no momento da cessao
     * @param spreadAplicado     spread congelado, produzido pela Strategy
     */
    public record RecebivelResponse(
            String numeroDocumento,
            String documentoSacado,
            String tipoRecebivel,
            BigDecimal valorFace,
            BigDecimal valorPresente,
            BigDecimal desagio,
            LocalDate dataVencimento,
            ConvencaoContagem convencaoAplicada,
            BigDecimal expoenteAplicado,
            BigDecimal taxaBaseAplicada,
            BigDecimal spreadAplicado) {

        private static RecebivelResponse de(Recebivel recebivel) {
            return new RecebivelResponse(
                    recebivel.getNumeroDocumento(),
                    recebivel.getSacadoDocumento(),
                    recebivel.getTipo().getCodigo(),
                    recebivel.getValorFace(),
                    recebivel.getValorPresente(),
                    recebivel.getDesagio(),
                    recebivel.getDataVencimento(),
                    recebivel.getConvencaoAplicada(),
                    recebivel.getExpoenteAplicado(),
                    recebivel.getTaxaBaseAplicada(),
                    recebivel.getSpreadAplicado());
        }
    }
}
