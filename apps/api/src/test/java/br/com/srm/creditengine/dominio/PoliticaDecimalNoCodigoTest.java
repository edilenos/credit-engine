package br.com.srm.creditengine.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guarda mecanica da politica de precisao decimal, varrendo o codigo-fonte.
 *
 * <p>Convencao escrita e' convencao que alguem fura. Estes testes sao o
 * equivalente, no codigo, do teste que varre {@code information_schema}
 * procurando colunas em ponto flutuante: transformam a regra em algo que
 * reprova o build.
 *
 * <p><b>Limite conhecido:</b> analise por expressao regular sobre texto, nao
 * parsing de AST. Pega o caso comum — {@code a.divide(b)} — e nao pega
 * {@code a.divide(b.add(c))}, onde ha parenteses aninhados. Um linter com AST
 * (PBI-39) faria melhor; enquanto ele nao existe, isto ja fecha o caminho mais
 * provavel.
 */
@DisplayName("Politica decimal aplicada ao codigo-fonte")
class PoliticaDecimalNoCodigoTest {

    private static final Path FONTES = Path.of("src", "main", "java");

    /** {@code .divide(algo)} sem virgula: a sobrecarga que estoura em dizima. */
    private static final Pattern DIVIDE_INSEGURO =
            Pattern.compile("\\.divide\\s*\\(\\s*[^,()]+\\s*\\)");

    /** Declaracao de tipo em ponto flutuante. */
    private static final Pattern PONTO_FLUTUANTE =
            Pattern.compile("\\b(double|float|Double|Float)\\s+\\w+\\s*[;=)]");

    private List<String> ocorrenciasDe(Pattern padrao) throws IOException {
        List<String> achados = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(FONTES)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
                Matcher m = padrao.matcher(semComentarios(conteudo));
                while (m.find()) {
                    achados.add(FONTES.relativize(arquivo) + " -> " + m.group().trim());
                }
            }
        }
        return achados;
    }

    /** Remove comentarios de bloco e de linha, para nao acusar prosa. */
    private String semComentarios(String fonte) {
        return fonte
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    @Test
    @DisplayName("nenhuma chamada a divide() sem escala e arredondamento explicitos")
    void nenhumDivideInseguro() throws IOException {
        assertThat(ocorrenciasDe(DIVIDE_INSEGURO))
                .as("BigDecimal.divide(x) lanca ArithmeticException em dizima, e VF/(1+i)^n "
                        + "produz dizima o tempo todo. Use PrecisaoDecimal.dividir()")
                .isEmpty();
    }

    @Test
    @DisplayName("nenhum tipo em ponto flutuante no codigo de producao")
    void nenhumPontoFlutuante() throws IOException {
        assertThat(ocorrenciasDe(PONTO_FLUTUANTE))
                .as("precisao decimal e criterio de avaliacao: dinheiro e taxa sempre em BigDecimal")
                .isEmpty();
    }

    @Test
    @DisplayName("o modo de arredondamento nao aparece solto fora da politica")
    void arredondamentoNaoApareceSolto() throws IOException {
        List<String> soltos = ocorrenciasDe(Pattern.compile("RoundingMode\\.\\w+")).stream()
                .filter(oco -> !oco.contains("PrecisaoDecimal.java"))
                .toList();

        assertThat(soltos)
                .as("arredondar em pontos diferentes com modos diferentes e a origem "
                        + "classica de divergencia de centavos")
                .isEmpty();
    }
}
