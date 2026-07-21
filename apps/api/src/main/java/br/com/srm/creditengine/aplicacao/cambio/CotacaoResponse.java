package br.com.srm.creditengine.aplicacao.cambio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import br.com.srm.creditengine.dominio.FonteCotacao;
import br.com.srm.creditengine.persistencia.entidade.TaxaCambio;

/**
 * Representacao de uma cotacao na API.
 *
 * <p>DTO proprio em vez de expor a entidade. Serializar {@code TaxaCambio}
 * direto acoplaria o contrato publico ao schema, e as associacoes LAZY
 * quebrariam na serializacao fora da transacao — o mapeamento explicito toca
 * cada campo dentro dela.
 *
 * @param id             identificador, usado no Location do POST
 * @param moedaOrigem    codigo ISO 4217
 * @param moedaDestino   codigo ISO 4217
 * @param cotacao        unidades de destino por unidade de origem
 * @param vigenciaInicio a partir de quando esta cotacao vale
 * @param fonte          origem do dado
 */
public record CotacaoResponse(
        Long id,
        String moedaOrigem,
        String moedaDestino,
        BigDecimal cotacao,
        OffsetDateTime vigenciaInicio,
        FonteCotacao fonte) {

    public static CotacaoResponse de(TaxaCambio taxa) {
        return new CotacaoResponse(
                taxa.getId(),
                taxa.getMoedaOrigem().getCodigo(),
                taxa.getMoedaDestino().getCodigo(),
                taxa.getCotacao(),
                taxa.getVigenciaInicio(),
                taxa.getFonte());
    }
}
