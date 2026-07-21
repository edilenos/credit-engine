package br.com.srm.creditengine.relatorio;

import java.util.Arrays;
import java.util.stream.Collectors;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * Ordenacao pedida fora da lista branca.
 *
 * <p>Estende {@code ExcecaoDeNegocio} apenas para reaproveitar o mapeamento
 * para 422 do tratador global — o relatorio nao passa pela camada de negocio,
 * mas a hierarquia de erro e' transversal.
 *
 * <p>A mensagem lista as opcoes validas em vez de so recusar: quem errou o nome
 * do campo precisa saber quais existem, e a lista nao e' segredo.
 */
public class OrdenacaoInvalidaException extends ExcecaoDeNegocio {

    public OrdenacaoInvalidaException(String pedido) {
        super("Ordenacao invalida: '" + pedido + "'. Valores aceitos: "
                + Arrays.stream(OrdenacaoDoExtrato.values())
                        .map(Enum::name)
                        .collect(Collectors.joining(", "))
                + "; direcao ASC ou DESC.");
    }
}
