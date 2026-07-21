package br.com.srm.creditengine.relatorio;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Uma pagina do extrato.
 *
 * <p>Nao usa {@code Page} do Spring Data de proposito: aqui nao ha repositorio
 * JPA, e devolver um tipo do Data acoplaria o contrato publico a uma estrutura
 * que serializa metadados demais — {@code pageable}, {@code sort},
 * {@code numberOfElements} — que o cliente nao pediu e que mudam de formato
 * entre versoes.
 *
 * @param conteudo       linhas da pagina
 * @param totalDeItens   total que atende ao filtro, nao o tamanho da pagina
 * @param pagina         indice atual, base zero
 * @param tamanho        itens por pagina
 */
public record PaginaDoExtrato(
        List<LinhaDoExtrato> conteudo,
        @Schema(description = "Total de registros que atendem ao filtro, "
                + "independente da pagina. E' o que permite ao cliente paginar.",
                example = "137")
        long totalDeItens,
        @Schema(description = "Indice da pagina, base zero.", example = "0")
        int pagina,
        @Schema(description = "Itens por pagina. Limitado a 100.", example = "20")
        int tamanho) {

    public PaginaDoExtrato {
        conteudo = List.copyOf(conteudo);
    }

    /**
     * Derivados, e por isso precisam de {@code @JsonProperty}.
     *
     * <p>Em record, o Jackson serializa os <b>componentes</b>; metodo extra com
     * nome no estilo de acessor de record nao entra. Sem a anotacao os dois
     * campos simplesmente sumiam da resposta — sem erro, sem aviso.
     *
     * <p>As anotacoes do Jackson continuam em {@code com.fasterxml.jackson.annotation}
     * mesmo no Jackson 3, cujo databind e' {@code tools.jackson}. Os dois
     * pacotes convivem de proposito.
     */
    @JsonProperty("totalDePaginas")
    @Schema(description = "Quantidade de paginas para o filtro atual.", example = "7")
    public int totalDePaginas() {
        return tamanho == 0 ? 0 : (int) Math.ceil((double) totalDeItens / tamanho);
    }

    @JsonProperty("temProxima")
    @Schema(description = "Se ha proxima pagina.", example = "true")
    public boolean temProxima() {
        return pagina + 1 < totalDePaginas();
    }
}
