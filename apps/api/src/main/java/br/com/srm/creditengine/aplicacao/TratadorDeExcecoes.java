package br.com.srm.creditengine.aplicacao;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;
import br.com.srm.creditengine.negocio.RecursoNaoEncontradoException;
import br.com.srm.creditengine.negocio.liquidacao.ConflitoDeEstadoException;

/**
 * Tratamento global de excecoes (PBI-31).
 *
 * <p>Ponto unico de traducao de falha em resposta. Nenhum controller tem
 * {@code try/catch}: o enunciado pede resiliencia a erro inesperado tratado de
 * forma controlada (§5.1), e tratamento espalhado por endpoint garante que um
 * deles fique de fora.
 *
 * <h2>O mapeamento e por tipo, nao por lista</h2>
 *
 * <table border="1">
 *   <caption>Situacao para status</caption>
 *   <tr><th>Situacao</th><th>Status</th></tr>
 *   <tr><td>{@link RecursoNaoEncontradoException}</td><td>404</td></tr>
 *   <tr><td>{@link ConflitoDeEstadoException} e conflitos de commit</td><td>409</td></tr>
 *   <tr><td>{@link ExcecaoDeNegocio} (demais)</td><td>422</td></tr>
 *   <tr><td>Payload ou parametro malformado</td><td>400</td></tr>
 *   <tr><td>Qualquer outra coisa</td><td>500 generico</td></tr>
 * </table>
 *
 * Herdar da base certa basta para a excecao nova nascer com o status certo. A
 * versao anterior enumerava cada classe de 404, e a lista crescia a cada PBI —
 * esquecer uma a fazia cair no 422 sem ninguem perceber.
 *
 * <h2>Nada vaza</h2>
 *
 * Nenhuma resposta carrega stacktrace, nome de classe, SQL ou nome de tabela.
 * Isso vale especialmente para as excecoes de infraestrutura, cuja mensagem
 * traz nome de constraint e de coluna: elas sao <b>logadas</b> inteiras e
 * respondidas com texto fixo. Ha teste para isso.
 *
 * <h2>Todo erro carrega o id de correlacao</h2>
 *
 * O id vem do {@link FiltroDeCorrelacao}, esta no {@code MDC} e portanto em
 * toda linha de log da requisicao. Repeti-lo no corpo e' o que permite pegar o
 * id de uma reclamacao de cliente e achar o log exato.
 */
@RestControllerAdvice
public class TratadorDeExcecoes {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeExcecoes.class);

    /**
     * Monta a resposta e carimba a correlacao.
     *
     * <p>Ponto unico de construcao de propósito: um {@code ProblemDetail} criado
     * fora daqui sairia sem o id, e a falta so apareceria no dia em que alguem
     * precisasse rastrear justamente aquele erro.
     */
    private ProblemDetail problema(HttpStatusCode status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setProperty("correlationId", MDC.get(FiltroDeCorrelacao.CHAVE_MDC));
        return problema;
    }

    // ------------------------------------------------------------------ 404

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException excecao) {
        return problema(HttpStatus.NOT_FOUND, "Recurso nao encontrado", excecao.getMessage());
    }

    /** Rota inexistente. Sem isto, a resposta nao teria correlacao. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail rotaInexistente(NoResourceFoundException excecao) {
        return problema(HttpStatus.NOT_FOUND, "Rota nao encontrada",
                "Nao existe recurso para o caminho solicitado.");
    }

    // ------------------------------------------------------------------ 409

    /**
     * O estado atual do recurso nao admite a operacao.
     *
     * <p>409 e nao 422 porque o pedido esta correto — so nao cabe agora. O
     * cliente resolve relendo o recurso, nao corrigindo o payload.
     */
    @ExceptionHandler(ConflitoDeEstadoException.class)
    public ProblemDetail conflitoDeEstado(ConflitoDeEstadoException excecao) {
        return problema(HttpStatus.CONFLICT, "Conflito de estado", excecao.getMessage());
    }

    /**
     * Duas transacoes disputaram o mesmo recurso e uma perdeu.
     *
     * <p>Acontecem no <b>commit</b>, depois que o servico ja retornou: colisao
     * de {@code @Version} e violacao de {@code UNIQUE}. Nenhum servico consegue
     * captura-las, e sem este tratamento virariam 500 — fazendo liquidacao
     * concorrente, que e' cenario previsto e correto, parecer defeito.
     *
     * <p>A mensagem e' fixa porque a original traz nome de constraint e de
     * tabela. O detalhe fica no log, com a correlacao.
     */
    @ExceptionHandler({
            OptimisticLockingFailureException.class,
            DataIntegrityViolationException.class
    })
    public ProblemDetail conflitoDeConcorrencia(Exception excecao) {
        log.warn("Conflito de concorrencia: {}", excecao.getMessage());
        return problema(HttpStatus.CONFLICT, "Conflito de concorrencia",
                "A operacao foi alterada por outra requisicao. "
                        + "Releia o recurso e tente novamente.");
    }

    // ------------------------------------------------------------------ 422

    /**
     * Payload bem formado que viola regra de dominio.
     *
     * <p>Captura a base para que regra nova nasca com status correto sem
     * precisar lembrar de registra-la aqui. Os handlers mais especificos acima
     * tem precedencia.
     */
    @ExceptionHandler(ExcecaoDeNegocio.class)
    public ProblemDetail regraViolada(ExcecaoDeNegocio excecao) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY,
                "Regra de negocio violada", excecao.getMessage());
    }

    // ------------------------------------------------------------------ 400

    /** Corpo invalido. Lista todos os campos, nao apenas o primeiro. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail payloadInvalido(MethodArgumentNotValidException excecao) {
        Map<String, String> porCampo = new LinkedHashMap<>();
        excecao.getBindingResult().getFieldErrors()
                .forEach(erro -> porCampo.put(erro.getField(), erro.getDefaultMessage()));

        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST,
                "Payload invalido", "Requisicao invalida");
        problema.setProperty("campos", porCampo);
        return problema;
    }

    /** Validacao em parametro de metodo (query string, path). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail parametroInvalido(HandlerMethodValidationException excecao) {
        return problema(HttpStatus.BAD_REQUEST, "Parametro invalido",
                "Um ou mais parametros da requisicao sao invalidos.");
    }

    /**
     * JSON malformado, ou tipo incompativel com o campo.
     *
     * <p>A mensagem original do Jackson cita classe, campo e posicao no fluxo —
     * detalhe de implementacao que nao ajuda o cliente e descreve a estrutura
     * interna para quem estiver sondando.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail corpoIlegivel(HttpMessageNotReadableException excecao) {
        log.debug("Corpo ilegivel: {}", excecao.getMessage());
        return problema(HttpStatus.BAD_REQUEST, "Corpo da requisicao invalido",
                "O corpo enviado nao e um JSON valido para este endpoint.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoDeParametroInvalido(MethodArgumentTypeMismatchException excecao) {
        return problema(HttpStatus.BAD_REQUEST, "Parametro invalido",
                "O parametro '" + excecao.getName() + "' tem formato invalido.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail parametroAusente(MissingServletRequestParameterException excecao) {
        return problema(HttpStatus.BAD_REQUEST, "Parametro obrigatorio ausente",
                "O parametro '" + excecao.getParameterName() + "' e obrigatorio.");
    }

    // --------------------------------------------------------- 405, 415, 406

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail metodoNaoSuportado(HttpRequestMethodNotSupportedException excecao) {
        return problema(HttpStatus.METHOD_NOT_ALLOWED, "Metodo nao suportado",
                "O metodo " + excecao.getMethod() + " nao e aceito neste recurso.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail tipoDeMidiaNaoSuportado(HttpMediaTypeNotSupportedException excecao) {
        return problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de midia nao suportado",
                "Esta API aceita apenas application/json.");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ProblemDetail tipoDeMidiaNaoAceitavel(HttpMediaTypeNotAcceptableException excecao) {
        return problema(HttpStatus.NOT_ACCEPTABLE, "Formato nao disponivel",
                "Esta API responde apenas em application/json.");
    }

    // ------------------------------------------------------------------ 500

    /**
     * Rede de seguranca: qualquer coisa que os handlers acima nao cobriram.
     *
     * <p>Este e' o unico ponto do sistema que decide o que acontece com um
     * defeito. O stacktrace inteiro vai para o log <b>com o id de correlacao</b>;
     * o cliente recebe texto fixo e o mesmo id. E' o que permite atender uma
     * reclamacao com o id em maos e chegar direto na linha do log, sem que a
     * resposta tenha revelado nada da estrutura interna.
     *
     * <h2>Nem tudo que chega aqui e defeito</h2>
     *
     * O {@code @ExceptionHandler} e' resolvido <b>antes</b> do tratamento padrao
     * do Spring MVC, entao este metodo intercepta tambem as excecoes que o
     * proprio framework ja sabe traduzir. Sem a checagem de
     * {@link ErrorResponse}, um {@code Content-Type} errado — falha do cliente,
     * 415 — sairia como 500 dizendo que o servidor quebrou. Aconteceu: a
     * primeira versao deste tratador respondia 500 para {@code text/plain}, e so
     * apareceu ao sondar as bordas manualmente.
     *
     * <p>As excecoes de borda do Spring implementam {@code ErrorResponse} e
     * carregam o proprio status. Respeita-lo cobre tambem as que a versao atual
     * do framework ainda nao tem, sem precisar enumerar classe por classe.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail erroInesperado(Exception excecao) {
        if (excecao instanceof ErrorResponse resposta) {
            log.warn("Requisicao rejeitada na borda ({}): {}",
                    resposta.getStatusCode(), excecao.getMessage());
            return problema(resposta.getStatusCode(), "Requisicao invalida",
                    "A requisicao nao pode ser processada nesta forma.");
        }

        log.error("Erro inesperado ao atender a requisicao", excecao);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Informe o identificador de correlacao ao suporte.");
    }
}
