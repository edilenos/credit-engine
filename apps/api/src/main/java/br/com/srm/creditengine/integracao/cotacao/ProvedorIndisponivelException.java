package br.com.srm.creditengine.integracao.cotacao;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * O provedor externo nao respondeu, respondeu erro, ou o circuito esta aberto.
 *
 * <p>E' excecao de negocio e nao falha tecnica porque, do ponto de vista da
 * aplicacao, "a cotacao de agora nao esta disponivel" e' um estado previsto do
 * mundo — nao um defeito. Quem chama decide se degrada para a ultima cotacao
 * conhecida ou propaga.
 */
public class ProvedorIndisponivelException extends ExcecaoDeNegocio {

    public ProvedorIndisponivelException(String moedaOrigem, String moedaDestino, Throwable causa) {
        super("Provedor de cotacao indisponivel para %s/%s: %s"
                .formatted(moedaOrigem, moedaDestino,
                        causa == null ? "circuito aberto" : causa.getMessage()));
    }
}
