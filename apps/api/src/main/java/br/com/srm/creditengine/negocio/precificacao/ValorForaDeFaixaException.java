package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;

import br.com.srm.creditengine.dominio.PrecisaoDecimal;
import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * O valor excede o que o sistema consegue representar.
 *
 * <p>Recusa de negocio, nao de formato: cada titulo do lote estava dentro da
 * faixa; foi a <b>soma</b> que passou. Nenhum campo isolado esta errado, entao
 * nao ha o que apontar num 400 por campo.
 */
public class ValorForaDeFaixaException extends ExcecaoDeNegocio {

    public ValorForaDeFaixaException(String oQue, BigDecimal valor) {
        super(oQue + " excede o maximo representavel de "
                + PrecisaoDecimal.VALOR_MAXIMO.toPlainString()
                + "; calculado: " + valor.toPlainString());
    }
}
