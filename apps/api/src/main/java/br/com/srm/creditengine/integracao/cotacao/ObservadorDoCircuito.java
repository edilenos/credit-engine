package br.com.srm.creditengine.integracao.cotacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Registra em log cada transicao de estado do circuito.
 *
 * <p>Sem isso, a degradacao acontece em silencio: o sistema responde com dado
 * antigo e ninguem fica sabendo que o provedor caiu. A transicao para
 * {@code OPEN} e' o sinal mais util que essa integracao produz, e e' o que
 * deveria disparar alerta em producao.
 *
 * <p>Os campos saem em formato chave=valor, para que a mudanca para log
 * estruturado em JSON (PBI-34) nao exija reescrever a mensagem.
 *
 * <p>O Resilience4j ja publica metricas do circuito via Micrometer, que vem
 * transitivo do starter — elas aparecem em {@code /actuator/prometheus} quando
 * o PBI-34 expuser o endpoint.
 */
@Component
public class ObservadorDoCircuito {

    private static final Logger log = LoggerFactory.getLogger(ObservadorDoCircuito.class);

    private final CircuitBreakerRegistry registro;

    public ObservadorDoCircuito(CircuitBreakerRegistry registro) {
        this.registro = registro;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void assinarTransicoes() {
        registro.circuitBreaker(ProvedorDeCotacao.INSTANCIA)
                .getEventPublisher()
                .onStateTransition(evento -> {
                    var transicao = evento.getStateTransition();
                    log.warn("circuito_transicao instancia={} de={} para={}",
                            evento.getCircuitBreakerName(),
                            transicao.getFromState(),
                            transicao.getToState());
                })
                .onCallNotPermitted(evento ->
                        log.info("circuito_aberto_chamada_bloqueada instancia={}",
                                evento.getCircuitBreakerName()));
    }
}
