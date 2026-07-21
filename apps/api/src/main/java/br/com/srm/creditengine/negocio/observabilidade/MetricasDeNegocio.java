package br.com.srm.creditengine.negocio.observabilidade;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Metricas do negocio, nao da maquina (PBI-34).
 *
 * <p>"Quantas liquidacoes falharam por conflito" responde a uma pergunta que
 * alguem realmente faz as tres da manha; uso de CPU nao. Por isso o que esta
 * instrumentado aqui sao os fatos do dominio — o Actuator ja entrega JVM,
 * pool de conexoes e HTTP sem qualquer codigo nosso.
 *
 * <h2>Nada de identificador em tag</h2>
 *
 * Tag e' dimensao, e cada valor distinto cria uma serie temporal nova. Marcar
 * uma metrica com id de operacao, CNPJ ou chave de idempotencia produz
 * cardinalidade ilimitada — derruba o Prometheus e, de quebra, publica
 * documento de cliente numa base que costuma ser menos protegida que o banco.
 * As tags aqui sao todas de conjunto fechado e conhecido.
 */
@Component
public class MetricasDeNegocio {

    private final Counter operacoesRegistradas;
    private final Counter liquidacoesConcluidas;
    private final Counter liquidacoesEmConflito;
    private final Counter liquidacoesRepetidas;
    private final Timer tempoDePrecificacao;

    public MetricasDeNegocio(MeterRegistry registro) {
        this.operacoesRegistradas = Counter.builder("creditengine.operacoes.registradas")
                .description("Operacoes de cessao registradas com sucesso")
                .register(registro);

        this.liquidacoesConcluidas = liquidacao(registro, "concluida");
        this.liquidacoesEmConflito = liquidacao(registro, "conflito");
        this.liquidacoesRepetidas = liquidacao(registro, "repeticao");

        this.tempoDePrecificacao = Timer.builder("creditengine.precificacao.duracao")
                .description("Tempo para precificar um lote inteiro")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registro);
    }

    private Counter liquidacao(MeterRegistry registro, String resultado) {
        return Counter.builder("creditengine.liquidacoes")
                .description("Tentativas de liquidacao por desfecho")
                .tag("resultado", resultado)
                .register(registro);
    }

    public void operacaoRegistrada() {
        operacoesRegistradas.increment();
    }

    /** Liquidacao efetivada agora. */
    public void liquidacaoConcluida() {
        liquidacoesConcluidas.increment();
    }

    /**
     * Recusada por estado ou por disputa.
     *
     * <p>Separada da repeticao de proposito: conflito subindo significa duas
     * mesas operando o mesmo titulo, e e' incidente. Repeticao subindo significa
     * cliente com retry ativo, que e' o sistema funcionando como desenhado.
     * Somadas, as duas leituras ficariam indistinguiveis.
     */
    public void liquidacaoEmConflito() {
        liquidacoesEmConflito.increment();
    }

    /** Chave de idempotencia reapresentada: devolveu o comprovante original. */
    public void liquidacaoRepetida() {
        liquidacoesRepetidas.increment();
    }

    public void registrarTempoDePrecificacao(long nanos) {
        tempoDePrecificacao.record(nanos, TimeUnit.NANOSECONDS);
    }
}
