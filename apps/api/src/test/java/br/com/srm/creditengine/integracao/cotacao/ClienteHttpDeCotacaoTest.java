package br.com.srm.creditengine.integracao.cotacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cobertura do cliente HTTP do provedor (PBI-16).
 *
 * <p>Existia um buraco: os timeouts sao criterio de aceite do PBI-15 e nao
 * eram verificados por nada. Configuracao de timeout que ninguem testa e'
 * configuracao que pode estar errada — e o sintoma so aparece em producao,
 * como thread presa e pool esgotado.
 *
 * <p>Os testes usam um {@link ServerSocket} de loopback em porta efemera, nao
 * a internet. E' deterministico e nao depende de servico externo: o criterio
 * "nao depender de rede real" veta dependencia de terceiros, nao o uso de
 * socket local — sem socket nenhum, seria impossivel provar que um timeout de
 * rede funciona.
 */
@DisplayName("Cliente HTTP do provedor de cotacao")
class ClienteHttpDeCotacaoTest {

    private ServerSocket servidor;
    private ExecutorService aceitador;

    @AfterEach
    void encerrarServidor() throws IOException {
        if (aceitador != null) {
            aceitador.shutdownNow();
        }
        if (servidor != null && !servidor.isClosed()) {
            servidor.close();
        }
    }

    private ClienteHttpDeCotacao clienteApontandoPara(int porta, Duration timeoutLeitura) {
        return new ClienteHttpDeCotacao(
                "http://127.0.0.1:" + porta,
                "/cotacoes/{origem}/{destino}",
                Duration.ofMillis(500),
                timeoutLeitura);
    }

    /** Aceita a conexao e nunca responde: e' assim que se provoca read timeout. */
    private int abrirServidorMudo() throws IOException {
        servidor = new ServerSocket(0);
        aceitador = Executors.newSingleThreadExecutor();
        aceitador.submit(() -> {
            while (!servidor.isClosed()) {
                try {
                    servidor.accept();
                    // conexao aceita e deliberadamente abandonada
                } catch (IOException encerrando) {
                    return;
                }
            }
        });
        return servidor.getLocalPort();
    }

    private int portaFechada() throws IOException {
        try (ServerSocket efemero = new ServerSocket(0)) {
            return efemero.getLocalPort();
        }
    }

    @Test
    @DisplayName("provedor que aceita e nao responde: desiste no timeout de leitura, nao trava")
    void desisteNoTimeoutDeLeitura() throws IOException {
        int porta = abrirServidorMudo();
        ClienteHttpDeCotacao cliente = clienteApontandoPara(porta, Duration.ofMillis(400));

        long inicio = System.nanoTime();

        assertThatThrownBy(() -> cliente.buscar("BRL", "USD"))
                .isInstanceOf(ProvedorIndisponivelException.class);

        Duration decorrido = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(decorrido)
                .as("sem timeout explicito o default do JDK e esperar para sempre; "
                        + "a thread ficaria presa e o pool esgotaria")
                .isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("porta fechada: traduz a recusa de conexao em erro de dominio")
    void portaFechadaViraErroDeDominio() throws IOException {
        ClienteHttpDeCotacao cliente = clienteApontandoPara(portaFechada(), Duration.ofMillis(400));

        assertThatThrownBy(() -> cliente.buscar("BRL", "USD"))
                .as("quem chama nao deve receber IOException nem detalhe de transporte")
                .isInstanceOf(ProvedorIndisponivelException.class)
                .hasMessageContaining("BRL")
                .hasMessageContaining("USD");
    }

    @Test
    @DisplayName("host inexistente: falha de DNS tambem vira erro de dominio")
    void hostInexistenteViraErroDeDominio() {
        ClienteHttpDeCotacao cliente = new ClienteHttpDeCotacao(
                "http://provedor-de-cotacao.invalido",
                "/cotacoes/{origem}/{destino}",
                Duration.ofMillis(500),
                Duration.ofMillis(500));

        assertThatThrownBy(() -> cliente.buscar("BRL", "USD"))
                .as("e a configuracao padrao do projeto: o provedor e mockado por ausencia")
                .isInstanceOf(ProvedorIndisponivelException.class);
    }
}
