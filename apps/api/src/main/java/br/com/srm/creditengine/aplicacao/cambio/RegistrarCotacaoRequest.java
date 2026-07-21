package br.com.srm.creditengine.aplicacao.cambio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import br.com.srm.creditengine.dominio.FonteCotacao;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Payload de registro de cotacao.
 *
 * <p>A validacao aqui e ESTRUTURAL: campo presente, formato, escala. Regra de
 * dominio — cotacao positiva, par distinto, moeda existente — fica no
 * {@code ServicoDeCambio}, porque o provedor externo (PBI-15) chama o servico
 * sem passar por este DTO. Invariante protegida so na borda e invariante que o
 * primeiro chamador alternativo fura.
 *
 * <p>A distincao tambem define o status HTTP: falha estrutural e 400, violacao
 * de regra e 422.
 *
 * @param moedaOrigem     codigo ISO 4217 da moeda de origem
 * @param moedaDestino    codigo ISO 4217 da moeda de destino
 * @param cotacao         unidades de destino por unidade de origem
 * @param vigenciaInicio  a partir de quando vale; ausente significa agora
 * @param fonte           MANUAL ou PROVEDOR; ausente significa MANUAL
 */
public record RegistrarCotacaoRequest(

        @NotNull(message = "moedaOrigem e obrigatoria")
        @Pattern(regexp = "^[A-Z]{3}$", message = "moedaOrigem deve ser um codigo ISO 4217 com 3 letras maiusculas")
        String moedaOrigem,

        @NotNull(message = "moedaDestino e obrigatoria")
        @Pattern(regexp = "^[A-Z]{3}$", message = "moedaDestino deve ser um codigo ISO 4217 com 3 letras maiusculas")
        String moedaDestino,

        @NotNull(message = "cotacao e obrigatoria")
        @Digits(integer = 13, fraction = 6, message = "cotacao aceita no maximo 13 inteiros e 6 decimais")
        BigDecimal cotacao,

        OffsetDateTime vigenciaInicio,

        FonteCotacao fonte) {

    /** Vigencia ausente significa "a partir de agora". */
    public OffsetDateTime vigenciaOuAgora() {
        return vigenciaInicio != null ? vigenciaInicio : OffsetDateTime.now();
    }

    /** Fonte ausente significa cadastro humano. */
    public FonteCotacao fonteOuManual() {
        return fonte != null ? fonte : FonteCotacao.MANUAL;
    }
}
