package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;

import br.com.srm.creditengine.dominio.PrecisaoDecimal;
import ch.obermuhlner.math.big.BigDecimalMath;

/**
 * A formula, isolada de tudo o mais.
 *
 * <pre>
 *   Valor Presente = Valor Face / (1 + Taxa Total) ^ Expoente
 * </pre>
 *
 * <p>Funcao pura: mesmos argumentos, mesmo resultado, sem banco, sem contexto e
 * sem relogio. Estava embutida no {@link MotorDePrecificacao} e saiu daqui
 * porque o coracao do calculo merece teste sem infraestrutura — subir o Spring
 * para conferir uma divisao e' caro e desnecessario.
 *
 * <p>A separacao tambem deixa a fronteira explicita: o motor <b>reune</b> os
 * parametros — taxa base vigente, spread da Strategy, expoente da convencao — e
 * esta classe apenas <b>aplica</b> a formula sobre eles.
 */
public final class CalculadoraDeValorPresente {

    private CalculadoraDeValorPresente() {
        // utilitario
    }

    /**
     * Desconta o valor de face pela taxa, ao longo do expoente.
     *
     * <p>A potenciacao usa {@link BigDecimalMath}, porque quatro das seis
     * convencoes produzem expoente fracionario e {@code BigDecimal.pow()} so
     * aceita {@code int}.
     *
     * <p>A divisao passa por {@link PrecisaoDecimal#dividir}, nunca pela
     * sobrecarga de um argumento: {@code VF / (1+i)^n} produz dizima o tempo
     * todo, e ali {@code BigDecimal} lanca {@code ArithmeticException}.
     *
     * @param valorFace valor nominal, positivo
     * @param taxaTotal taxa base somada ao spread, na mesma unidade do expoente
     * @param expoente  prazo ja normalizado pela convencao de contagem
     * @return valor presente arredondado na escala monetaria
     */
    public static BigDecimal descontar(BigDecimal valorFace, BigDecimal taxaTotal, BigDecimal expoente) {
        BigDecimal base = BigDecimal.ONE.add(taxaTotal);
        BigDecimal fator = BigDecimalMath.pow(base, expoente, PrecisaoDecimal.CONTEXTO);

        return PrecisaoDecimal.comoMoeda(PrecisaoDecimal.dividir(valorFace, fator));
    }
}
