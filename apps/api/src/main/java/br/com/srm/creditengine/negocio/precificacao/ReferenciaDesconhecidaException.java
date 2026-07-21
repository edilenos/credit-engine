package br.com.srm.creditengine.negocio.precificacao;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * O payload referencia um cadastro que nao existe.
 *
 * <p>Um tipo e uma moeda inexistentes sao o mesmo erro sob dois nomes, e por
 * isso compartilham excecao e status: o corpo esta bem formado e aponta para
 * algo que o sistema nao conhece. Isso e' 422, nao 404 — o 404 diria que a URI
 * {@code /api/v1/simulacoes} nao existe, e ela existe.
 */
public class ReferenciaDesconhecidaException extends ExcecaoDeNegocio {

    private ReferenciaDesconhecidaException(String mensagem) {
        super(mensagem);
    }

    public static ReferenciaDesconhecidaException tipoRecebivel(String codigo) {
        return new ReferenciaDesconhecidaException(
                "Tipo de recebivel nao cadastrado: " + codigo);
    }

    public static ReferenciaDesconhecidaException moeda(String codigo) {
        return new ReferenciaDesconhecidaException("Moeda nao cadastrada: " + codigo);
    }
}
