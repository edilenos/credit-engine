package br.com.srm.creditengine.aplicacao.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.srm.creditengine.negocio.cessao.SolicitacaoDeCessao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de registro de cessao.
 *
 * <p>Validacao ESTRUTURAL apenas — presenca, formato, escala, tamanho do lote.
 * Existencia de cedente, tipo e moeda, e viabilidade do prazo, sao regras de
 * dominio e ficam no servico.
 *
 * @param documentoCedente CNPJ do cedente, so digitos
 * @param titulos          lote a ceder
 * @param moedaTitulo      moeda dos titulos
 * @param moedaLiquidacao  moeda do desembolso
 * @param dataOperacao     data-base; ausente significa hoje
 */
public record RegistrarOperacaoRequest(

        @NotBlank(message = "documentoCedente e obrigatorio")
        @Pattern(regexp = "^\\d{14}$", message = "documentoCedente deve ter 14 digitos")
        String documentoCedente,

        // Sem @NotEmpty: lote vazio e' recusado pelo dominio, com
        // LoteVazioException, e responde 422. O teto continua estrutural (400),
        // porque "grande demais" e' propriedade do payload, nao do negocio.
        @NotNull(message = "titulos e obrigatorio")
        @Size(max = LOTE_MAXIMO, message = "lote limitado a " + LOTE_MAXIMO + " recebiveis")
        List<@Valid TituloRequest> titulos,

        @NotNull(message = "moedaTitulo e obrigatoria")
        @Pattern(regexp = "^[A-Z]{3}$", message = "moedaTitulo deve ser um codigo ISO 4217 com 3 letras maiusculas")
        String moedaTitulo,

        @NotNull(message = "moedaLiquidacao e obrigatoria")
        @Pattern(regexp = "^[A-Z]{3}$", message = "moedaLiquidacao deve ser um codigo ISO 4217 com 3 letras maiusculas")
        String moedaLiquidacao,

        LocalDate dataOperacao) {

    /** Mesmo teto da simulacao: aqui o custo do lote grande inclui gravacao. */
    public static final int LOTE_MAXIMO = 500;

    public LocalDate dataOperacaoOuHoje() {
        return dataOperacao != null ? dataOperacao : LocalDate.now();
    }

    public SolicitacaoDeCessao paraDominio() {
        return new SolicitacaoDeCessao(
                documentoCedente,
                titulos.stream().map(TituloRequest::paraDominio).toList(),
                moedaTitulo,
                moedaLiquidacao,
                dataOperacaoOuHoje());
    }

    /**
     * Um recebivel do lote.
     *
     * @param tipoRecebivel    codigo do produto
     * @param numeroDocumento  identificacao do titulo no cedente
     * @param documentoSacado  CPF (11) ou CNPJ (14) de quem deve
     * @param valorFace        valor nominal
     * @param dataVencimento   quando o sacado paga
     */
    public record TituloRequest(

            @NotNull(message = "tipoRecebivel e obrigatorio")
            @Pattern(regexp = "^[A-Z0-9_]{1,40}$",
                    message = "tipoRecebivel aceita letras maiusculas, digitos e underscore")
            String tipoRecebivel,

            @NotBlank(message = "numeroDocumento e obrigatorio")
            @Size(max = 50, message = "numeroDocumento aceita no maximo 50 caracteres")
            String numeroDocumento,

            @NotBlank(message = "documentoSacado e obrigatorio")
            // Os parenteses nao sao estilo: sem eles a alternacao ligaria
            // "^\d{11}" com "\d{14}$" e aceitaria qualquer coisa comecando com
            // 11 digitos, inclusive um documento de 30.
            @Pattern(regexp = "^(\\d{11}|\\d{14})$",
                    message = "documentoSacado deve ter 11 digitos (CPF) ou 14 (CNPJ)")
            String documentoSacado,

            @NotNull(message = "valorFace e obrigatorio")
            @DecimalMin(value = "0.01", message = "valorFace deve ser positivo")
            @Digits(integer = 17, fraction = 2,
                    message = "valorFace aceita no maximo 17 inteiros e 2 decimais")
            BigDecimal valorFace,

            @NotNull(message = "dataVencimento e obrigatoria")
            LocalDate dataVencimento) {

        private SolicitacaoDeCessao.TituloACeder paraDominio() {
            return new SolicitacaoDeCessao.TituloACeder(
                    tipoRecebivel, numeroDocumento, documentoSacado, valorFace, dataVencimento);
        }
    }
}
