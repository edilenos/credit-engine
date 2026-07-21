package br.com.srm.creditengine.negocio.cambio;

import java.math.BigDecimal;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * Cotacao com valor nao positivo.
 *
 * <p>A regra vive no dominio, e nao apenas como Bean Validation no DTO, por
 * dois motivos: o servico precisa recusar o valor venha de onde vier — inclusive
 * do provedor externo (PBI-15), que nao passa por DTO nenhum — e porque uma
 * cotacao zero e' sintaticamente valida e semanticamente impossivel, o que a
 * torna violacao de regra (422) e nao erro de formato (400).
 */
public class CotacaoInvalidaException extends ExcecaoDeNegocio {

    public CotacaoInvalidaException(BigDecimal valor) {
        super("Cotacao precisa ser maior que zero, recebido: %s".formatted(valor));
    }
}
