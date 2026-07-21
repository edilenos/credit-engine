package br.com.srm.creditengine.negocio.precificacao;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * O prazo pedido esta fora do que o produto admite.
 *
 * <p>Nao e' erro de formato: o titulo e' valido, a data e' valida, e mesmo
 * assim a mesa nao opera aquele prazo naquele produto. Recusar e' mais honesto
 * que precificar com um spread punitivo — o segundo esconde a decisao dentro
 * de um numero.
 */
public class PrazoInviavelException extends ExcecaoDeNegocio {

    public PrazoInviavelException(String codigoDoTipo, long prazoEmDias, long prazoMaximo) {
        super("Produto %s admite no maximo %d dias de prazo; pedido: %d"
                .formatted(codigoDoTipo, prazoMaximo, prazoEmDias));
    }
}
