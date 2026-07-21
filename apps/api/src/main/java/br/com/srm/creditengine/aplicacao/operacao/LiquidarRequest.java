package br.com.srm.creditengine.aplicacao.operacao;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de liquidacao.
 *
 * <p>A chave vai no corpo e nao no cabecalho {@code Idempotency-Key}. A
 * convencao de mercado e' o cabecalho, e ela e' boa quando a idempotencia e'
 * transversal — vale para qualquer POST e o corpo nao muda. Aqui ela e' parte
 * do recurso: a coluna e' {@code NOT NULL UNIQUE} em {@code liquidacao}, o
 * comprovante carrega a chave, e ela e' consultavel depois. Sendo dado do
 * recurso, o corpo e' o lugar, e a requisicao fica legivel em um bloco so.
 *
 * @param chaveIdempotencia identificador do cliente para este pedido; repetir a
 *                          chave devolve a liquidacao original
 * @param liquidadoPor      quem autorizou. Viria da autenticacao num sistema
 *                          com login; aqui e' informado, e o campo existe para
 *                          a trilha de auditoria nao nascer anonima
 */
public record LiquidarRequest(

        @NotBlank(message = "chaveIdempotencia e obrigatoria")
        @Size(max = 64, message = "chaveIdempotencia aceita no maximo 64 caracteres")
        @Schema(description = "Identificador do cliente para este pedido. Repetir a chave devolve a liquidacao original, com 200 em vez de 201.", example = "liq-2026-07-21-0001")
        String chaveIdempotencia,

        @NotBlank(message = "liquidadoPor e obrigatorio")
        @Size(max = 80, message = "liquidadoPor aceita no maximo 80 caracteres")
        @Schema(description = "Quem autorizou a liquidacao. Vai para a trilha de auditoria.", example = "bruno.backoffice")
        String liquidadoPor) {
}
