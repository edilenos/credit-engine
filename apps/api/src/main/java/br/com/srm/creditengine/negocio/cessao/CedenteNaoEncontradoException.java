package br.com.srm.creditengine.negocio.cessao;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * O cedente informado nao existe no cadastro.
 *
 * <p>Diferente de tipo ou moeda desconhecidos, que respondem 422: o cedente e'
 * a contraparte da operacao, e o padrao de uso e' o cliente ja ter o cadastro e
 * referencia-lo. Nao encontra-lo e' o caso classico de 404 — o recurso apontado
 * nao existe.
 */
public class CedenteNaoEncontradoException extends ExcecaoDeNegocio {

    public CedenteNaoEncontradoException(String documento) {
        super("Cedente nao encontrado para o documento " + documento);
    }
}
