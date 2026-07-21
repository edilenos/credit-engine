package br.com.srm.creditengine.dominio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Politica unica de precisao decimal do sistema.
 *
 * <p>Precisao decimal e' criterio de avaliacao declarado do desafio. A politica
 * precisa ser tomada uma vez, aplicada em todo lugar e documentada — arredondar
 * em pontos diferentes com modos diferentes e' a origem classica de divergencia
 * de centavos em sistema financeiro, e o tipo de defeito que ninguem consegue
 * reproduzir depois.
 *
 * <p>Esta classe existe para que nenhuma constante de escala ou modo de
 * arredondamento apareca solta no codigo. Ela tambem oferece as operacoes, e
 * nao so os valores: {@link #dividir} existe porque a sobrecarga
 * {@code BigDecimal.divide(BigDecimal)} lanca {@link ArithmeticException} em
 * dizima, e depender de cada chamador lembrar disso e' depender de sorte.
 *
 * <h2>As tres escalas, e por que sao diferentes</h2>
 *
 * <table>
 *   <tr><th>Escala</th><th>Onde</th><th>Por que</th></tr>
 *   <tr>
 *     <td>2</td><td>Valores monetarios</td>
 *     <td>O centavo e' a unidade minima de BRL e USD. Moeda com escala
 *         diferente usa a sua propria — ver {@link #comoMoeda(BigDecimal, int)}.</td>
 *   </tr>
 *   <tr>
 *     <td>6</td><td>Taxas: spread, taxa base</td>
 *     <td>Taxa entra em EXPONENCIACAO. Erro de arredondamento num valor
 *         permanece linear; num expoente, e' amplificado. Seis casas dao
 *         resolucao muito alem do que mesa de credito usa.</td>
 *   </tr>
 *   <tr>
 *     <td>10</td><td>Expoente do prazo</td>
 *     <td>Nao e' dinheiro nem taxa: e' fator de calculo, tipicamente dizima
 *         ({@code 46/252 = 0,1825396825...}). Preserva a reprodutibilidade da
 *         auditoria.</td>
 *   </tr>
 * </table>
 *
 * <h2>Por que HALF_EVEN e nao HALF_UP</h2>
 *
 * HALF_UP arredonda todo empate para cima. Numa carteira com milhares de
 * titulos, isso enviesa a soma sistematicamente a favor de um dos lados —
 * pouco por operacao, muito no agregado. HALF_EVEN (arredondamento bancario)
 * alterna o destino do empate conforme a paridade do ultimo digito mantido, e
 * o vies se cancela. E' o padrao de sistema financeiro pelo mesmo motivo.
 *
 * <h2>Quando arredondar</h2>
 *
 * Calculo intermediario mantem precisao alta, via {@link #CONTEXTO}. O
 * arredondamento para a escala final acontece <b>so na fronteira de saida</b>:
 * persistencia e resposta da API. Arredondar a cada passo intermediario
 * acumula erro em vez de conte-lo.
 */
public final class PrecisaoDecimal {

    /** Casas decimais de valores monetarios em BRL e USD. */
    public static final int ESCALA_MONETARIA = 2;

    /** Casas decimais de taxas — spread e taxa base. */
    public static final int ESCALA_TAXA = 6;

    /** Casas decimais do expoente do prazo, ja normalizado pela convencao. */
    public static final int ESCALA_EXPOENTE = 10;

    /** Modo unico de arredondamento do sistema. */
    public static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_EVEN;

    /**
     * Contexto dos calculos intermediarios.
     *
     * <p>{@link MathContext#DECIMAL128} e' o padrao IEEE 754 decimal128: 34
     * digitos significativos, ja com HALF_EVEN. A escolha nao e' arbitraria —
     * 34 digitos dao folga confortavel sobre os 19 das colunas
     * {@code NUMERIC(19,x)}, entao nenhum passo intermediario perde informacao
     * que a coluna final saberia guardar.
     */
    public static final MathContext CONTEXTO = MathContext.DECIMAL128;

    private PrecisaoDecimal() {
        // utilitario
    }

    /**
     * Divisao segura.
     *
     * <p><b>Use sempre esta, nunca {@code BigDecimal.divide(BigDecimal)}.</b> A
     * sobrecarga de um argumento lanca {@link ArithmeticException} quando o
     * resultado e' dizima — e {@code ValorFace / (1+i)^n}, que e' o coracao
     * deste sistema, produz dizima o tempo todo. O erro so aparece em runtime,
     * com a entrada especifica que a provoca.
     *
     * <p>Mantem a precisao do {@link #CONTEXTO}: quem arredonda para a escala
     * final e' a fronteira de saida, nao esta operacao.
     */
    public static BigDecimal dividir(BigDecimal dividendo, BigDecimal divisor) {
        return dividendo.divide(divisor, CONTEXTO);
    }

    /** Arredonda para escala monetaria padrao (2 casas). */
    public static BigDecimal comoMoeda(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, ARREDONDAMENTO);
    }

    /**
     * Arredonda usando a escala da moeda.
     *
     * <p>A escala vem de {@code moeda.escala_padrao}, nao de constante: BRL e
     * USD usam 2 casas, JPY usa 0 e KWD usa 3. Fixar 2 no codigo funcionaria
     * hoje e quebraria na primeira moeda nao centesimal.
     */
    public static BigDecimal comoMoeda(BigDecimal valor, int escalaDaMoeda) {
        return valor.setScale(escalaDaMoeda, ARREDONDAMENTO);
    }

    /** Arredonda para a escala de taxas (6 casas). */
    public static BigDecimal comoTaxa(BigDecimal valor) {
        return valor.setScale(ESCALA_TAXA, ARREDONDAMENTO);
    }

    /** Arredonda para a escala do expoente do prazo (10 casas). */
    public static BigDecimal comoExpoente(BigDecimal valor) {
        return valor.setScale(ESCALA_EXPOENTE, ARREDONDAMENTO);
    }
}
