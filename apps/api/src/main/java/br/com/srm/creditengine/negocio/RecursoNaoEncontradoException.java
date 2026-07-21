package br.com.srm.creditengine.negocio;

/**
 * O recurso referenciado nao existe.
 *
 * <p>Existe para o tratamento global mapear 404 por <b>tipo</b>, e nao por
 * lista. A versao anterior enumerava cada excecao no {@code @ExceptionHandler},
 * e a lista crescia a cada PBI: bastava esquecer uma para ela cair no
 * tratamento de {@code ExcecaoDeNegocio} e virar 422 silenciosamente — erro
 * plausivel o suficiente para ninguem reparar.
 *
 * <p>Herdando daqui, excecao nova nasce com o status certo. E' o mesmo
 * principio que ja valia para {@link ExcecaoDeNegocio} e o 422.
 */
public abstract class RecursoNaoEncontradoException extends ExcecaoDeNegocio {

    protected RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
