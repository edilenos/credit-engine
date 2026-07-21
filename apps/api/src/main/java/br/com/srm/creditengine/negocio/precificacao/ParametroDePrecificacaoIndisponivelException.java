package br.com.srm.creditengine.negocio.precificacao;

import java.time.OffsetDateTime;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * Nao ha taxa base vigente na data pedida.
 *
 * <p>Sem taxa base nao ha como precificar. Assumir zero devolveria o proprio
 * valor de face descontado apenas pelo spread — um preco alto demais, sem
 * nenhum sinal de que faltou parametro.
 */
public class ParametroDePrecificacaoIndisponivelException extends ExcecaoDeNegocio {

    public ParametroDePrecificacaoIndisponivelException(OffsetDateTime momento) {
        super("Nao ha taxa base vigente em %s".formatted(momento));
    }
}
