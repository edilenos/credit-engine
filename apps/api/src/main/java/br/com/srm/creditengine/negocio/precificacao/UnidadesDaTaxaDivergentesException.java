package br.com.srm.creditengine.negocio.precificacao;

import br.com.srm.creditengine.dominio.Periodicidade;
import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * Taxa base e spread estao cotados em unidades diferentes.
 *
 * <p>Somar 1% ao mes com 2,5% ao ano produz um numero sem significado, e a
 * soma acontece sem erro nenhum — BigDecimal nao sabe de unidade. E' o mesmo
 * modo de falha do risco R7, um nivel antes: ali a taxa nao casava com a
 * convencao, aqui as duas parcelas da taxa nao casam entre si.
 */
public class UnidadesDaTaxaDivergentesException extends ExcecaoDeNegocio {

    public UnidadesDaTaxaDivergentesException(Periodicidade daTaxaBase, Periodicidade doSpread) {
        super(("Taxa base cotada em %s e spread em %s nao podem ser somados. "
                + "As duas parcelas precisam estar na mesma unidade de capitalizacao.")
                .formatted(daTaxaBase, doSpread));
    }
}
