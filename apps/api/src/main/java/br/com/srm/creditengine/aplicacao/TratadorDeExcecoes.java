package br.com.srm.creditengine.aplicacao;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.com.srm.creditengine.negocio.cambio.CotacaoIndisponivelException;
import br.com.srm.creditengine.negocio.cambio.CotacaoNaoEncontradaException;
import br.com.srm.creditengine.negocio.cambio.MoedaDesconhecidaException;
import br.com.srm.creditengine.negocio.cessao.CedenteNaoEncontradoException;
import br.com.srm.creditengine.negocio.cessao.OperacaoNaoEncontradaException;

/**
 * Tratamento global de excecoes.
 *
 * <p>Versao inicial, atendendo aos status que os criterios de aceite do PBI-14
 * exigem. O PBI-31 completa: cobertura de toda a hierarquia
 * {@code ExcecaoDeNegocio}, id de correlacao em cada resposta, e a garantia de
 * que erro inesperado nunca vaza stacktrace, SQL ou nome de tabela.
 *
 * <h2>Por que 400 e 422 sao coisas diferentes aqui</h2>
 *
 * <ul>
 *   <li><b>400</b> — o payload esta malformado: campo faltando, formato errado,
 *       escala impossivel. Vem da Bean Validation, na borda.</li>
 *   <li><b>422</b> — o payload esta bem formado e viola uma regra do dominio.
 *       Cotacao zero e sintaticamente valida e semanticamente impossivel.</li>
 * </ul>
 *
 * Essa separacao e' a razao de a regra "cotacao positiva" viver no servico e
 * nao como {@code @Positive} no DTO: como regra de dominio, ela vale para
 * qualquer chamador e responde 422.
 */
@RestControllerAdvice
public class TratadorDeExcecoes {

    /** Recurso pedido nao existe. */
    @ExceptionHandler({
            MoedaDesconhecidaException.class,
            CotacaoIndisponivelException.class,
            CotacaoNaoEncontradaException.class,
            CedenteNaoEncontradoException.class,
            OperacaoNaoEncontradaException.class
    })
    public ProblemDetail naoEncontrado(RuntimeException excecao) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, excecao.getMessage());
        problema.setTitle("Recurso nao encontrado");
        return problema;
    }

    /**
     * Payload bem formado que viola regra de dominio.
     *
     * <p>Captura a base {@code ExcecaoDeNegocio} para que regra nova nasca com
     * status correto sem precisar lembrar de registra-la aqui. Os casos de 404
     * acima tem precedencia por serem mais especificos.
     */
    @ExceptionHandler(br.com.srm.creditengine.negocio.ExcecaoDeNegocio.class)
    public ProblemDetail regraViolada(br.com.srm.creditengine.negocio.ExcecaoDeNegocio excecao) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, excecao.getMessage());
        problema.setTitle("Regra de negocio violada");
        return problema;
    }

    /** Payload malformado. Lista todos os campos invalidos, nao apenas o primeiro. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail payloadInvalido(MethodArgumentNotValidException excecao) {
        Map<String, String> porCampo = new LinkedHashMap<>();
        excecao.getBindingResult().getFieldErrors()
                .forEach(erro -> porCampo.put(erro.getField(), erro.getDefaultMessage()));

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Requisicao invalida");
        problema.setTitle("Payload invalido");
        problema.setProperty("campos", porCampo);
        return problema;
    }
}
