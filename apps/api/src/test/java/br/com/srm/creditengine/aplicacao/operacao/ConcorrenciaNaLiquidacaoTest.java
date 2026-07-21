package br.com.srm.creditengine.aplicacao.operacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import br.com.srm.creditengine.dominio.StatusOperacao;
import br.com.srm.creditengine.persistencia.repositorio.LiquidacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.OperacaoRepositorio;

/**
 * Concorrencia real na liquidacao (PBI-29).
 *
 * <p>Transforma "usei optimistic locking" em fato demonstrado. Afirmar em README
 * que a race condition foi tratada nao prova nada; N threads disputando a mesma
 * operacao, sim.
 *
 * <h2>Por que servidor de verdade, e nao MockMvc</h2>
 *
 * {@code MockMvc} nao sobe servidor e executa no thread do teste. Com
 * {@code RANDOM_PORT} ha Tomcat, pool de conexoes e transacoes independentes —
 * as condicoes em que o lock otimista de fato opera. E o status HTTP observado
 * e' o que um cliente veria, nao o que o {@code @ExceptionHandler} devolveria em
 * isolamento.
 *
 * <p>O cliente e' o {@link HttpClient} do JDK. No Boot 4 o
 * {@code TestRestTemplate} saiu para o modulo {@code spring-boot-resttestclient}
 * e mudou de pacote; adicionar dependencia nova para uma classe de teste custa
 * mais do que resolve, e o cliente do JDK ja e' thread-safe.
 *
 * <h2>Por que nao e' intermitente</h2>
 *
 * O criterio de aceite pede determinismo. As assercoes valem sob
 * <b>qualquer</b> intercalacao, inclusive serializacao completa:
 *
 * <ul>
 *   <li>se as threads correrem de verdade, as perdedoras batem no {@code @Version}
 *       ou na constraint;
 *   <li>se o escalonador as serializar, as seguintes leem status
 *       {@code LIQUIDADA} e param na checagem de estado.
 * </ul>
 *
 * Nos dois casos: uma linha de liquidacao, um {@code 201}, e o resto conflito. O
 * teste nao depende de a corrida acontecer — depende de o resultado ser correto
 * se ela acontecer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Concorrencia na liquidacao")
class ConcorrenciaNaLiquidacaoTest {

    private static final int THREADS = 8;
    private static final String CEDENTE = "11222333000181";

    @LocalServerPort private int porta;
    @Autowired private OperacaoRepositorio operacoes;
    @Autowired private LiquidacaoRepositorio liquidacoes;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @AfterEach
    void limpar() {
        liquidacoes.deleteAll();
        operacoes.deleteAll();
    }

    private URI url(String caminho) {
        return URI.create("http://localhost:" + porta + caminho);
    }

    private HttpResponse<String> postar(String caminho, String corpo) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(url(caminho))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();

        return http.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    private Long registrarOperacao() throws Exception {
        HttpResponse<String> resposta = postar("/api/v1/operacoes", """
                {
                  "documentoCedente": "%s",
                  "registradoPor": "operador.teste",
                  "moedaTitulo": "BRL",
                  "moedaLiquidacao": "BRL",
                  "dataOperacao": "2026-07-20",
                  "titulos": [{"tipoRecebivel":"DUPLICATA_MERCANTIL","numeroDocumento":"DUP-C",
                               "documentoSacado":"52998224725","valorFace":"100000.00",
                               "dataVencimento":"2026-09-04"}]
                }
                """.formatted(CEDENTE));

        assertThat(resposta.statusCode()).isEqualTo(201);

        String local = resposta.headers().firstValue("Location").orElseThrow();
        return Long.valueOf(local.substring(local.lastIndexOf('/') + 1));
    }

    /**
     * O que uma thread observou.
     *
     * <p>O {@code titulo} nao entra em assercao rigida de proposito — exigir um
     * titulo especifico tornaria o teste intermitente, porque sob serializacao
     * a perdedora para na checagem de estado em vez de colidir no commit. Ele
     * entra nas mensagens de falha, onde vira a evidencia de <b>qual</b> defesa
     * atuou.
     *
     * <p>Numa execucao real observou-se: 1x201 e 7x "Conflito de concorrencia".
     * Todas as oito passaram pela checagem de status — a disputa aconteceu de
     * fato, e foram o {@code @Version} e a constraint que a resolveram.
     */
    private record Desfecho(int status, String titulo) {
    }

    private static String tituloDe(String corpo) {
        int inicio = corpo.indexOf("\"title\":\"");
        if (inicio < 0) return "sem titulo";
        int abre = inicio + 9;
        return corpo.substring(abre, corpo.indexOf('"', abre));
    }

    /**
     * Dispara N liquidacoes simultaneas e devolve o que cada uma observou.
     *
     * <p>O {@code CountDownLatch} existe para as threads chegarem juntas ao
     * {@code POST}. Sem ele, cada uma sairia no tempo em que foi criada e a
     * disputa poderia simplesmente nao acontecer — o teste passaria por nao
     * exercitar nada.
     */
    private List<Desfecho> liquidarEmParalelo(Long operacaoId,
                                              java.util.function.IntFunction<String> chavePorThread)
            throws Exception {

        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch prontas = new CountDownLatch(THREADS);

        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Callable<Desfecho>> tarefas = java.util.stream.IntStream.range(0, THREADS)
                    .<Callable<Desfecho>>mapToObj(indice -> () -> {
                        prontas.countDown();
                        largada.await(10, TimeUnit.SECONDS);

                        var resposta = postar("/api/v1/operacoes/" + operacaoId + "/liquidacao",
                                """
                                {"chaveIdempotencia":"%s","liquidadoPor":"thread-%d"}
                                """.formatted(chavePorThread.apply(indice), indice));

                        return new Desfecho(resposta.statusCode(), tituloDe(resposta.body()));
                    })
                    .toList();

            List<Future<Desfecho>> futuros = tarefas.stream().map(pool::submit).toList();

            prontas.await(10, TimeUnit.SECONDS);
            largada.countDown();

            List<Desfecho> desfechos = new java.util.ArrayList<>();
            for (Future<Desfecho> futuro : futuros) {
                desfechos.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return desfechos;
        }
    }

    private static long contar(List<Desfecho> desfechos, int status) {
        return desfechos.stream().filter(desfecho -> desfecho.status() == status).count();
    }

    @Test
    @DisplayName("com 8 threads e chaves distintas, exatamente uma liquidacao e criada")
    void apenasUmaLiquidacaoComChavesDistintas() throws Exception {
        Long id = registrarOperacao();

        List<Desfecho> desfechos = liquidarEmParalelo(id, indice -> "chave-thread-" + indice);

        assertThat(contar(desfechos, 201))
                .as("desfechos: %s", desfechos)
                .isEqualTo(1);

        assertThat(contar(desfechos, 409))
                .as("as perdedoras recebem conflito, nao erro generico. Desfechos: %s", desfechos)
                .isEqualTo(THREADS - 1L);

        assertThat(liquidacoes.count())
                .as("a garantia que importa: uma operacao, uma liquidacao")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("nenhuma perdedora recebe 5xx: disputa e cenario previsto, nao defeito")
    void nenhumaPerdedoraRecebeErroDeServidor() throws Exception {
        Long id = registrarOperacao();

        List<Desfecho> desfechos = liquidarEmParalelo(id, indice -> "chave-sem-500-" + indice);

        assertThat(desfechos)
                .as("um 500 aqui significaria OptimisticLockingFailureException ou "
                        + "violacao de UNIQUE vazando sem tratamento")
                .allMatch(desfecho -> desfecho.status() < 500);
    }

    @Test
    @DisplayName("com a mesma chave em todas as threads, o retry concorrente nao duplica")
    void mesmaChaveEmTodasAsThreads() throws Exception {
        Long id = registrarOperacao();

        // Cenario do cliente que reenvia o mesmo pedido: mesma chave, N vezes,
        // ao mesmo tempo. A vencedora cria; as demais ou reencontram a chave e
        // devolvem o original (200), ou perdem a insercao e recebem 409.
        List<Desfecho> desfechos = liquidarEmParalelo(id, indice -> "chave-unica-para-todas");

        assertThat(contar(desfechos, 201))
                .as("desfechos: %s", desfechos)
                .isEqualTo(1);

        assertThat(desfechos)
                .as("cada resposta e' criacao, replay do original, ou conflito — "
                        + "nunca uma segunda liquidacao. Desfechos: %s", desfechos)
                .allMatch(desfecho -> desfecho.status() == 201
                        || desfecho.status() == 200
                        || desfecho.status() == 409);

        assertThat(liquidacoes.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("o estado final e consistente qualquer que tenha sido a ordem")
    void estadoFinalConsistente() throws Exception {
        Long id = registrarOperacao();

        liquidarEmParalelo(id, indice -> "chave-estado-" + indice);

        assertThat(operacoes.findById(id).orElseThrow().getStatus())
                .isEqualTo(StatusOperacao.LIQUIDADA);

        assertThat(liquidacoes.findByOperacaoId(id))
                .as("status LIQUIDADA sem comprovante, ou comprovante sem status, "
                        + "seria o estado parcial que a transacao existe para impedir")
                .isPresent()
                .hasValueSatisfying(liquidacao ->
                        assertThat(liquidacao.getValorLiquidado())
                                .isEqualByComparingTo(new java.math.BigDecimal("96284.58")));
    }
}
