package br.com.srm.creditengine.integracao.cotacao;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP do provedor externo de cotacao.
 *
 * <p>O provedor e' <b>mockado</b> neste desafio: o que esta sendo demonstrado e'
 * o padrao de integracao resiliente, nao a integracao em si. Por padrao a URL
 * aponta para um host inexistente, e' isso que faz o circuit breaker e o
 * fallback serem observaveis logo apos subir o sistema.
 *
 * <p><b>Timeouts sao explicitos.</b> Chamada externa sem limite de tempo e' o
 * jeito mais rapido de transformar lentidao alheia em indisponibilidade
 * propria: a thread fica presa, o pool esgota, e o sistema inteiro para por
 * causa de um dependente. O default do JDK e' esperar indefinidamente.
 *
 * <p>Esta classe nao tem retry nem circuit breaker: ela so fala HTTP. A
 * resiliencia e' aplicada por fora, em {@link ProvedorDeCotacao}.
 */
@Component
public class ClienteHttpDeCotacao implements ClienteDeCotacao {

    private final RestClient http;
    private final String caminho;

    public ClienteHttpDeCotacao(
            @Value("${cambio.provedor.url-base:http://provedor-de-cotacao.invalido}") String urlBase,
            @Value("${cambio.provedor.caminho:/cotacoes/{origem}/{destino}}") String caminho,
            @Value("${cambio.provedor.timeout-conexao:2s}") Duration timeoutConexao,
            @Value("${cambio.provedor.timeout-leitura:3s}") Duration timeoutLeitura) {

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeoutConexao);
        fabrica.setReadTimeout(timeoutLeitura);

        this.http = RestClient.builder().baseUrl(urlBase).requestFactory(fabrica).build();
        this.caminho = caminho;
    }

    @Override
    public BigDecimal buscar(String moedaOrigem, String moedaDestino) {
        try {
            RespostaDoProvedor resposta = http.get()
                    .uri(caminho, moedaOrigem, moedaDestino)
                    .retrieve()
                    .body(RespostaDoProvedor.class);

            if (resposta == null || resposta.cotacao() == null) {
                throw new ProvedorIndisponivelException(moedaOrigem, moedaDestino,
                        new IllegalStateException("resposta sem cotacao"));
            }
            return resposta.cotacao();

        } catch (ProvedorIndisponivelException e) {
            throw e;
        } catch (RuntimeException e) {
            // Timeout, DNS, 5xx, corpo ilegivel: para quem chama e' tudo a mesma
            // coisa — nao ha cotacao. A causa vai na mensagem, para o log.
            throw new ProvedorIndisponivelException(moedaOrigem, moedaDestino, e);
        }
    }

    /** Contrato minimo esperado do provedor. */
    record RespostaDoProvedor(BigDecimal cotacao) {
    }
}
