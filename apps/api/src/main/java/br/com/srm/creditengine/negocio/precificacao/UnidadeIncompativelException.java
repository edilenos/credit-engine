package br.com.srm.creditengine.negocio.precificacao;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.Periodicidade;

/**
 * A taxa esta cotada numa unidade e a convencao produz expoente em outra.
 *
 * <p>Este e' o erro que o sistema mais precisa levantar alto, porque sozinho
 * ele nao aparece: {@code (1 + 0,015)^0,127} — spread mensal com expoente
 * anual — e' uma conta perfeitamente executavel que devolve um numero
 * plausivel e errado por ordem de grandeza. Nenhuma excecao natural acontece,
 * nenhum teste de caminho feliz reprova, e o resultado vira preco de operacao.
 *
 * <p>E' o risco R7 do backlog, e a razao de a periodicidade viajar junto com a
 * taxa em vez de ser convencao implicita.
 */
public class UnidadeIncompativelException extends br.com.srm.creditengine.negocio.ExcecaoDeNegocio {

    public UnidadeIncompativelException(ConvencaoContagem convencao,
                                        Periodicidade periodicidadeDaTaxa,
                                        Periodicidade periodicidadeEsperada) {
        super(("Convencao %s produz expoente %s, mas a taxa esta cotada em %s. "
                + "Combinar unidades diferentes produz preco errado por ordem de grandeza.")
                .formatted(convencao, periodicidadeEsperada, periodicidadeDaTaxa));
    }
}
