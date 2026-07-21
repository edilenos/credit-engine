package br.com.srm.creditengine.aplicacao.simulacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.srm.creditengine.negocio.precificacao.SolicitacaoDeSimulacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
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

        @NotEmpty(message = "titulos deve conter ao menos um recebivel")
        @Size(max = LOTE_MAXIMO, message = "lote limitado a " + LOTE_MAXIMO + " recebiveis")
        List<@Valid TituloRequest> titulos,

        @NotNull(message = "moedaTitulo e obrigatoria")
        @Pattern(regexp = "^[A-Z]{3}$", message = "moedaTitulo deve ser um codigo ISO 4217 com 3 letras maiusculas")
        String moedaTitulo,

        @NotNull(message = "moedaLiquidacao e obrigatoria")
        @Pattern(regexp = "^[A-Z]{3}$", message = "moedaLiquidacao deve ser um codigo ISO 4217 com 3 letras maiusculas")
        String moedaLiquidacao,

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
            String tipoRecebivel,

            @NotNull(message = "valorFace e obrigatorio")
            @DecimalMin(value = "0.01", message = "valorFace deve ser positivo")
            @Digits(integer = 17, fraction = 2,
                    message = "valorFace aceita no maximo 17 inteiros e 2 decimais")
            BigDecimal valorFace,

            @NotNull(message = "dataVencimento e obrigatoria")
            LocalDate dataVencimento) {

        private SolicitacaoDeSimulacao.TituloASimular paraDominio() {
            return new SolicitacaoDeSimulacao.TituloASimular(
                    tipoRecebivel, valorFace, dataVencimento);
        }
    }
}
