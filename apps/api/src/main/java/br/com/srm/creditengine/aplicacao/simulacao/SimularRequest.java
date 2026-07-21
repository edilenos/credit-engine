package br.com.srm.creditengine.aplicacao.simulacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.srm.creditengine.negocio.precificacao.SolicitacaoDeSimulacao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de simulacao.
 *
 * <p>Validacao ESTRUTURAL apenas — presenca, formato, escala, tamanho do lote.
 * Se o tipo existe, se o vencimento e' posterior a operacao e se ha cotacao
 * para o par sao regras de dominio, e ficam no servico: valem para qualquer
 * chamador, inclusive o registro de cessao do PBI-26, que nao passa por este
 * DTO.
 *
 * @param titulos          lote a precificar
 * @param moedaTitulo      moeda dos titulos
 * @param moedaLiquidacao  moeda do desembolso; igual a do titulo em operacao
 *                         de moeda unica
 * @param dataOperacao     data-base; ausente significa hoje
 */
public record SimularRequest(

        // Sem @NotEmpty: lote vazio e' recusado pelo dominio, com
        // LoteVazioException, e responde 422 — igual ao registro de cessao. Com
        // @NotEmpty aqui, a excecao de dominio virava codigo morto no caminho
        // HTTP, e os dois endpoints respondiam status diferentes para o mesmo
        // erro. O teto continua estrutural (400): "grande demais" e' propriedade
        // do payload, nao do negocio.
        @NotNull(message = "titulos e obrigatorio")
        @Size(max = LOTE_MAXIMO, message = "lote limitado a " + LOTE_MAXIMO + " recebiveis")
        @Schema(description = "Recebiveis a precificar. Lote vazio e recusado com 422.")
        List<@Valid TituloRequest> titulos,

        @NotNull(message = "moedaTitulo e obrigatoria")
        @Pattern(regexp = "^[A-Z]{3}$", message = "moedaTitulo deve ser um codigo ISO 4217 com 3 letras maiusculas")
        @Schema(description = "Moeda em que os titulos estao denominados.", example = "BRL")
        String moedaTitulo,

        @NotNull(message = "moedaLiquidacao e obrigatoria")
        @Pattern(regexp = "^[A-Z]{3}$", message = "moedaLiquidacao deve ser um codigo ISO 4217 com 3 letras maiusculas")
        @Schema(description = "Moeda do desembolso. Diferente da moeda do titulo torna a "
                + "operacao cross-currency, e o cambio e' aplicado ao final.", example = "USD")
        String moedaLiquidacao,

        @Schema(description = "Data-base da precificacao. Ausente significa hoje.",
                example = "2026-07-20")
        LocalDate dataOperacao) {

    /**
     * Teto do lote.
     *
     * <p>O enunciado trata validacao de entrada como requisito de seguranca
     * (§5.2). Endpoint de calculo sem teto e' amplificador: um POST de 50 MB
     * vira meio milhao de potenciacoes decimais em precisao arbitraria.
     */
    public static final int LOTE_MAXIMO = 500;

    /** Data ausente significa hoje — o caso comum da mesa. */
    public LocalDate dataOperacaoOuHoje() {
        return dataOperacao != null ? dataOperacao : LocalDate.now();
    }

    public SolicitacaoDeSimulacao paraDominio() {
        return new SolicitacaoDeSimulacao(
                titulos.stream().map(TituloRequest::paraDominio).toList(),
                moedaTitulo,
                moedaLiquidacao,
                dataOperacaoOuHoje());
    }

    /**
     * Um recebivel do lote.
     *
     * @param tipoRecebivel  codigo do produto, conferido contra o cadastro
     * @param valorFace      valor nominal, na escala da moeda
     * @param dataVencimento quando o sacado paga
     */
    public record TituloRequest(

            @NotNull(message = "tipoRecebivel e obrigatorio")
            @Pattern(regexp = "^[A-Z0-9_]{1,40}$",
                    message = "tipoRecebivel aceita letras maiusculas, digitos e underscore")
            @Schema(description = "Codigo do produto. Consulte /api/v1/cadastros/tipos-recebivel.",
                    example = "DUPLICATA_MERCANTIL")
            String tipoRecebivel,

            @NotNull(message = "valorFace e obrigatorio")
            @DecimalMin(value = "0.01", message = "valorFace deve ser positivo")
            @Digits(integer = 17, fraction = 2,
                    message = "valorFace aceita no maximo 17 inteiros e 2 decimais")
            // multipleOf = 0.01 e' como o OpenAPI expressa "duas casas decimais"
            // de forma legivel por maquina. O `example` sozinho nao serve: JSON
            // nao preserva zero a direita, entao "100000.00" chega ao contrato
            // como 100000 e a escala desaparece.
            @Schema(description = "Valor nominal, com exatamente duas casas decimais. "
                    + "O total do lote tambem precisa caber em 17 inteiros e 2 decimais.",
                    example = "100000.00", multipleOf = 0.01)
            BigDecimal valorFace,

            @NotNull(message = "dataVencimento e obrigatoria")
            @Schema(description = "Vencimento do titulo. Anterior a data da operacao e "
                    + "recusado com 422.", example = "2026-09-04")
            LocalDate dataVencimento) {

        private SolicitacaoDeSimulacao.TituloASimular paraDominio() {
            return new SolicitacaoDeSimulacao.TituloASimular(
                    tipoRecebivel, valorFace, dataVencimento);
        }
    }
}
