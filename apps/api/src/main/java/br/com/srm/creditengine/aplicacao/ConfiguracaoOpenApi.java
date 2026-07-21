package br.com.srm.creditengine.aplicacao;

import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Contrato OpenAPI da API (PBI-33).
 *
 * <h2>Os erros sao documentados globalmente, nao endpoint a endpoint</h2>
 *
 * O springdoc infere verbo, parametros e schema de sucesso a partir das
 * assinaturas, mas nao enxerga o {@link TratadorDeExcecoes}: os status de erro
 * nascem de {@code @ExceptionHandler}, nao das assinaturas dos controllers.
 *
 * <p>Anotar cada metodo com {@code @ApiResponse} para 400, 404, 409 e 422 seria
 * repetir a mesma tabela dezenas de vezes, e a copia esquecida ficaria mentindo
 * no dia em que um status mudasse. Como o tratamento e' global, a documentacao
 * dele tambem e': o {@link OpenApiCustomizer} acrescenta as respostas de erro a
 * todas as operacoes, a partir de um unico lugar.
 *
 * <p>Cada status so entra onde faz sentido — 409 nao aparece em {@code GET}.
 */
@Configuration
public class ConfiguracaoOpenApi {

    private static final String PROBLEMA = "ProblemDetail";
    private static final String REFERENCIA = "#/components/schemas/" + PROBLEMA;

    private final String porta;

    public ConfiguracaoOpenApi(@Value("${server.port:8081}") String porta) {
        this.porta = porta;
    }

    @Bean
    public OpenAPI contratoDaApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SRM Credit Engine")
                        .version("v1")
                        .description("""
                                Plataforma de cessao de credito multi-moeda para FIDC.

                                O fundo adquire recebiveis com desagio, precifica por tipo de \
                                titulo, aplica cambio quando a liquidacao e' em outra moeda, e \
                                registra a operacao de forma auditavel.

                                **Precificacao:** `Valor Presente = Valor Face / (1 + Taxa Base \
                                + Spread) ^ Expoente`, com o expoente produzido pela convencao \
                                de contagem do produto.

                                **Cambio:** aplicado sempre no final, sobre o total, nunca \
                                titulo a titulo — a ordem muda o arredondamento.

                                **Erros** seguem RFC 7807 e trazem `correlationId`, que \
                                corresponde ao cabecalho `X-Correlation-Id` e a linha de log \
                                do servidor.""")
                        .contact(new Contact().name("SRM Asset")))
                .servers(List.of(new Server()
                        .url("http://localhost:" + porta)
                        .description("Ambiente local")));
    }

    /**
     * Acrescenta as respostas de erro a todas as operacoes.
     *
     * <p>Roda depois da varredura dos controllers, entao alcanca inclusive
     * endpoints criados depois desta classe — que e' o ponto de faze-lo aqui e
     * nao por anotacao.
     */
    @Bean
    public OpenApiCustomizer respostasDeErroPadrao() {
        return contrato -> {
            // O schema entra AQUI e nao no bean OpenAPI: o springdoc monta o
            // proprio Components a partir da varredura dos controllers e
            // substitui o que estivesse la. Registrado no bean, o $ref dos erros
            // apontava para um schema inexistente — contrato quebrado que
            // continuava respondendo 200.
            if (contrato.getComponents() == null) {
                contrato.setComponents(new Components());
            }
            contrato.getComponents().addSchemas(PROBLEMA, schemaDoProblema());

            contrato.getPaths().values().forEach(caminho ->
                    caminho.readOperationsMap().forEach(this::documentarErros));
        };
    }

    private void documentarErros(PathItem.HttpMethod metodo, Operation operacao) {
        ApiResponses respostas = operacao.getResponses();
        boolean escrita = metodo == PathItem.HttpMethod.POST
                || metodo == PathItem.HttpMethod.PUT
                || metodo == PathItem.HttpMethod.PATCH;

        acrescentar(respostas, "400",
                "Payload ou parametro malformado. O campo `campos` lista todos os "
                        + "campos invalidos, nao apenas o primeiro.");
        acrescentar(respostas, "404", "Recurso referenciado nao existe.");

        if (escrita) {
            acrescentar(respostas, "409",
                    "Conflito de estado ou de concorrencia. O pedido esta correto, mas o "
                            + "recurso mudou ou ja esta no estado final. Reler o recurso "
                            + "antes de repetir.");
            acrescentar(respostas, "413", "Corpo da requisicao acima do limite aceito.");
            acrescentar(respostas, "422",
                    "Payload bem formado que viola uma regra de negocio — prazo inviavel, "
                            + "valor fora de faixa, cadastro inexistente.");
        }

        acrescentar(respostas, "500",
                "Erro inesperado. O corpo nao detalha a causa; use o `correlationId` "
                        + "para localizar o log correspondente.");
    }

    /** Nao sobrescreve o que a operacao ja declarou explicitamente. */
    private void acrescentar(ApiResponses respostas, String status, String descricao) {
        if (respostas.containsKey(status)) {
            return;
        }
        respostas.addApiResponse(status, new ApiResponse()
                .description(descricao)
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new Schema<>().$ref(REFERENCIA)))));
    }

    /**
     * RFC 7807 com a extensao {@code correlationId}.
     *
     * <p>Descrito a mao porque {@code ProblemDetail} nao aparece em nenhuma
     * assinatura de controller — os handlers e' que o produzem, e o springdoc
     * varre assinaturas.
     */
    private Schema<?> schemaDoProblema() {
        Schema<?> problema = new Schema<>()
                .type("object")
                .description("Erro no formato RFC 7807.");

        problema.setProperties(Map.of(
                "type", new Schema<>().type("string").example("about:blank"),
                "title", new Schema<>().type("string").example("Regra de negocio violada"),
                "status", new Schema<>().type("integer").example(422),
                "detail", new Schema<>().type("string")
                        .example("Produto CHEQUE_PRE_DATADO admite no maximo 180 dias de "
                                + "prazo; pedido: 529"),
                "instance", new Schema<>().type("string").example("/api/v1/simulacoes"),
                "correlationId", new Schema<>().type("string")
                        .description("Mesmo valor do cabecalho X-Correlation-Id. Identifica "
                                + "a linha de log correspondente.")
                        .example("4f4c4276-4ab3-4c06-a58b-b48b8be3d52d"),
                "campos", new Schema<>().type("object")
                        .description("Presente em 400: mensagem por campo invalido.")
                        .example(Map.of("titulos[0].valorFace", "valorFace deve ser positivo"))));

        return problema;
    }
}
