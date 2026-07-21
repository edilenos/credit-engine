package br.com.srm.creditengine.aplicacao.cambio;

import br.com.srm.creditengine.negocio.cambio.ResultadoSincronizacao;

/**
 * Resposta da sincronizacao com o provedor externo.
 *
 * <p>O campo {@code degradado} e' parte do contrato, nao detalhe interno: o
 * cliente precisa saber que a cotacao devolvida veio do historico porque o
 * provedor falhou, e nao de uma consulta atual. Degradar em silencio faria a
 * mesa operar com dado velho sem ter como perceber.
 *
 * @param cotacao   a cotacao resultante
 * @param degradado se veio do historico por falha do provedor
 * @param motivo    quando degradado, o que impediu a atualizacao
 */
public record SincronizacaoResponse(CotacaoResponse cotacao, boolean degradado, String motivo) {

    public static SincronizacaoResponse de(ResultadoSincronizacao resultado) {
        return new SincronizacaoResponse(
                CotacaoResponse.de(resultado.cotacao()),
                resultado.degradado(),
                resultado.motivo());
    }
}
