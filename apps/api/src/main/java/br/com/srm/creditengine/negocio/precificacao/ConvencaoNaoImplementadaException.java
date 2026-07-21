package br.com.srm.creditengine.negocio.precificacao;

import br.com.srm.creditengine.dominio.ConvencaoContagem;

/**
 * O codigo de convencao existe no dominio, mas nenhuma implementacao esta
 * registrada.
 *
 * <p>Acontece quando o enum ganha uma constante antes da classe correspondente
 * — caso de BUS_252 ate o PBI-20. Falhar aqui e' melhor que cair num default
 * silencioso: um expoente calculado pela convencao errada produz preco errado
 * sem deixar rastro.
 */
public class ConvencaoNaoImplementadaException extends br.com.srm.creditengine.negocio.ExcecaoDeNegocio {

    public ConvencaoNaoImplementadaException(ConvencaoContagem convencao) {
        super("Convencao de contagem sem implementacao registrada: %s".formatted(convencao));
    }
}
