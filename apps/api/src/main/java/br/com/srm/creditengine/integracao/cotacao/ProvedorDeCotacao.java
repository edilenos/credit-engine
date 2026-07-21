package br.com.srm.creditengine.integracao.cotacao;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Aplica resiliencia sobre o cliente do provedor externo.
 *
 * <p>Atende ao entregavel "Retries ou Circuit Breaker em chamadas externas" do
 * nivel Senior (paragrafo 6). Este e' o unico ponto de integracao externa do
 * sistema, entao e' aqui que o requisito se materializa.
 *
 * <h2>Ordem dos aspectos</h2>
 *
 * O Resilience4j encadeia como {@code Retry ( CircuitBreaker ( chamada ) )} —
 * o retry e' o mais externo. Consequencia deliberada: <b>cada tentativa conta
 * como uma chamada para o circuito</b>.
 *
 * <p>Isso e' o que se quer aqui. Falha transitoria e' absorvida pelo retry sem
 * chegar ao usuario; falha sustentada preenche a janela mais rapido e abre o
 * circuito mais cedo, que e' exatamente o comportamento desejado quando o
 * dependente esta fora. A alternativa — circuito por fora, contando uma
 * chamada logica — faria o sistema insistir por muito mais tempo contra um
 * servico morto.
 *
 * <h2>Fallback</h2>
 *
 * Com o circuito aberto, o metodo de fallback e' chamado <b>sem tocar na
 * rede</b> e converte a falha em {@link ProvedorIndisponivelException}. A
 * decisao de degradar para a ultima cotacao conhecida nao e' tomada aqui: e'
 * politica de negocio, e vive no servico de sincronizacao. Esta camada so sabe
 * dizer "nao consegui".
 */
@Component
public class ProvedorDeCotacao {

    private static final Logger log = LoggerFactory.getLogger(ProvedorDeCotacao.class);

    /**
     * Nome da instancia de retry e circuit breaker.
     *
     * <p>Publico porque e' contrato, nao detalhe: liga estas anotacoes ao bloco
     * {@code resilience4j.*.instances.provedorDeCotacao} do application.yaml, e
     * e' por ele que se obtem o circuito no registro. Nome divergente entre
     * codigo e configuracao nao falha o build — o Resilience4j apenas aplica os
     * defaults, e a resiliencia configurada vira decoracao silenciosa.
     */
    public static final String INSTANCIA = "provedorDeCotacao";

    private final ClienteDeCotacao cliente;

    public ProvedorDeCotacao(ClienteDeCotacao cliente) {
        this.cliente = cliente;
    }

    @Retry(name = INSTANCIA)
    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "indisponivel")
    public BigDecimal cotacaoAtual(String moedaOrigem, String moedaDestino) {
        return cliente.buscar(moedaOrigem, moedaDestino);
    }

    /**
     * Assinatura exigida pelo Resilience4j: mesmos parametros do metodo
     * original, mais a causa. Precisa ser acessivel ao proxy, por isso nao e'
     * private.
     */
    BigDecimal indisponivel(String moedaOrigem, String moedaDestino, Throwable causa) {
        log.warn("cotacao_indisponivel origem={} destino={} causa={}",
                moedaOrigem, moedaDestino, causa.toString());
        throw new ProvedorIndisponivelException(moedaOrigem, moedaDestino, causa);
    }
}
