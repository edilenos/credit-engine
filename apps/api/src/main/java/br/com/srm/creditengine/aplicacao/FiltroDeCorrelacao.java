package br.com.srm.creditengine.aplicacao;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Atribui um id de correlacao a cada requisicao.
 *
 * <p>E' o que liga a resposta de erro que o cliente recebeu a linha de log
 * correspondente no servidor. Sem isso, "deu 500 as 14h32" e' tudo que se tem
 * para procurar num log concorrente.
 *
 * <p>O id vai para tres lugares: o {@code MDC} (para todo log da requisicao
 * carrega-lo), o cabecalho da resposta (para o cliente registrar) e o corpo do
 * {@code ProblemDetail} em caso de erro (para quem le a resposta nao precisar
 * inspecionar cabecalho).
 *
 * <p>Se o chamador ja enviou {@code X-Correlation-Id}, o valor e' <b>aceito</b>
 * em vez de sobrescrito: e' o que permite rastrear uma chamada atravessando
 * mais de um servico. Vem sanitizado — cabecalho e' entrada do usuario, e um
 * valor com quebra de linha injetaria uma linha falsa no log.
 *
 * <p>Roda com prioridade maxima: filtro que registra o id depois de outro que
 * ja logou deixa justamente o comeco da requisicao sem correlacao.
 *
 * <p>O PBI-34 constroi o log estruturado em JSON sobre este {@code MDC}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroDeCorrelacao extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Correlation-Id";
    public static final String CHAVE_MDC = "correlationId";

    private static final int TAMANHO_MAXIMO = 64;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest requisicao,
                                    @NonNull HttpServletResponse resposta,
                                    @NonNull FilterChain cadeia)
            throws ServletException, IOException {

        String correlacao = sanitizar(requisicao.getHeader(CABECALHO));

        MDC.put(CHAVE_MDC, correlacao);
        resposta.setHeader(CABECALHO, correlacao);
        try {
            cadeia.doFilter(requisicao, resposta);
        } finally {
            // Sem a limpeza, o id vaza para a proxima requisicao atendida pela
            // mesma thread do pool — e os logs passam a apontar para a
            // requisicao errada, que e' pior do que nao ter correlacao.
            MDC.remove(CHAVE_MDC);
        }
    }

    private String sanitizar(String recebido) {
        if (recebido == null || recebido.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String limpo = recebido.replaceAll("[^A-Za-z0-9._-]", "");
        return limpo.isEmpty()
                ? UUID.randomUUID().toString()
                : limpo.substring(0, Math.min(limpo.length(), TAMANHO_MAXIMO));
    }
}
