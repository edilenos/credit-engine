package br.com.srm.creditengine.aplicacao;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Recusa corpos grandes demais antes de le-los.
 *
 * <h2>Por que o {@code @Size} do lote nao basta</h2>
 *
 * {@code @Size(max = 500)} na lista de titulos so e' avaliado <b>depois</b> de o
 * Jackson materializar o corpo inteiro em memoria. Um POST de 500 MB e' lido,
 * parseado e so entao rejeitado — a validacao protege o dominio, nao a memoria,
 * e a tarefa do PBI pede as duas coisas.
 *
 * <h2>Por que um filtro, e nao configuracao</h2>
 *
 * Nao ha propriedade que limite corpo JSON. {@code server.max-http-post-size}
 * esta <b>deprecada em nivel de erro</b> no Boot 4, e o substituto indicado
 * ({@code server.tomcat.max-http-form-post-size}) vale apenas para
 * {@code application/x-www-form-urlencoded} — verificado nos metadados de
 * configuracao, nao suposto.
 *
 * <p>O {@code Content-Length} cobre o caso normal, e a checagem custa nada.
 * Requisicao sem ele (chunked) passa adiante: contar bytes do fluxo exigiria
 * envolver o {@code InputStream}, e o teto de itens do lote ja limita o dano
 * nesse caminho. A escolha esta registrada aqui em vez de silenciosa.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FiltroDeTamanhoDeRequisicao extends OncePerRequestFilter {

    private final long limiteEmBytes;

    public FiltroDeTamanhoDeRequisicao(
            @Value("${app.limites.tamanho-maximo-do-corpo:1MB}") org.springframework.util.unit.DataSize limite) {
        this.limiteEmBytes = limite.toBytes();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest requisicao,
                                    @NonNull HttpServletResponse resposta,
                                    @NonNull FilterChain cadeia)
            throws ServletException, IOException {

        long declarado = requisicao.getContentLengthLong();

        if (declarado > limiteEmBytes) {
            responderExcedido(requisicao, resposta, declarado);
            return;
        }
        cadeia.doFilter(requisicao, resposta);
    }

    /**
     * Responde 413 sem passar pelo {@code @RestControllerAdvice}.
     *
     * <p>O filtro roda fora do {@code DispatcherServlet}, entao nao ha handler de
     * excecao para traduzir nada. O corpo e' montado a mao no mesmo formato dos
     * demais erros — inclusive com a correlacao, que o filtro anterior ja pos no
     * cabecalho — para o cliente nao ter que tratar dois formatos.
     */
    private void responderExcedido(HttpServletRequest requisicao,
                                   HttpServletResponse resposta,
                                   long declarado) throws IOException {
        resposta.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");

        String correlacao = resposta.getHeader(FiltroDeCorrelacao.CABECALHO);
        resposta.getWriter().write("""
                {"type":"about:blank","title":"Corpo da requisicao grande demais",\
                "status":413,\
                "detail":"O corpo enviado excede o limite de %d bytes.",\
                "instance":"%s",\
                "correlationId":"%s"}"""
                .formatted(limiteEmBytes, requisicao.getRequestURI(), correlacao));

        logger.warn("Corpo recusado por tamanho: " + declarado + " bytes em "
                + requisicao.getRequestURI());
    }
}
