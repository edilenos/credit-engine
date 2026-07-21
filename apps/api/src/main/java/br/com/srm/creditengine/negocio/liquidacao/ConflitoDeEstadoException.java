package br.com.srm.creditengine.negocio.liquidacao;

import br.com.srm.creditengine.dominio.StatusOperacao;
import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * A operacao existe, mas o estado dela nao admite liquidacao.
 *
 * <p>Vira 409 e nao 422: nao ha nada de errado com o pedido — liquidar e' uma
 * acao legitima, so nao <b>agora</b>. A distincao importa para o cliente, que
 * pode reagir a 409 relendo o recurso, e a 422 corrigindo o payload.
 */
public class ConflitoDeEstadoException extends ExcecaoDeNegocio {

    private ConflitoDeEstadoException(String mensagem) {
        super(mensagem);
    }

    public static ConflitoDeEstadoException jaLiquidada(Long operacaoId) {
        return new ConflitoDeEstadoException(
                "Operacao " + operacaoId + " ja foi liquidada");
    }

    public static ConflitoDeEstadoException statusNaoAdmiteLiquidacao(Long operacaoId,
                                                                     StatusOperacao status) {
        return new ConflitoDeEstadoException(
                "Operacao " + operacaoId + " esta " + status + " e nao pode ser liquidada");
    }

    /**
     * A chave ja foi usada para liquidar outra operacao.
     *
     * <p>Nao e' replay: e' colisao. Devolver a liquidacao original seria
     * responder sobre um recurso que o cliente nao pediu.
     */
    public static ConflitoDeEstadoException chaveEmUsoPorOutraOperacao(String chave,
                                                                      Long operacaoOriginal) {
        return new ConflitoDeEstadoException(
                "Chave de idempotencia '" + chave + "' ja foi usada na operacao "
                        + operacaoOriginal);
    }
}
