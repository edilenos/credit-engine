package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.Periodicidade;

/**
 * Converte o intervalo entre duas datas no expoente da formula de valor
 * presente. Segunda familia de Strategy do sistema.
 *
 * <p>O enunciado nao fixa convencao de contagem, e na pratica FIDCs usam
 * varias conforme o lastro do ativo. Cada uma tem <b>logica de contagem
 * diferente</b>, nao apenas denominador diferente — a regra 30/360 conta meses
 * comerciais com ajuste de borda, enquanto as demais contam dias corridos.
 *
 * <h2>A unidade produzida faz parte do contrato</h2>
 *
 * {@code (1 + taxa)^expoente} so e' valido quando o expoente esta na mesma
 * unidade de capitalizacao da taxa. Combinar spread de 1,5% <b>a.m.</b> com
 * uma convencao de saida anual <b>nao lanca excecao</b>: produz preco
 * plausivel e errado por ordem de grandeza. E' o modo de falha mais perigoso
 * do sistema, registrado como risco R7.
 *
 * <p>Por isso a unidade nao e' documentacao: vem de
 * {@link ConvencaoContagem#periodicidadeEsperada()}, e o
 * {@link ResolvedorDeConvencao} valida o par antes de calcular qualquer coisa.
 *
 * <p>Nenhuma convencao conhece a formula do valor presente — ela so devolve o
 * expoente. Compor taxa base, spread e expoente e' do motor de calculo.
 */
public interface ConvencaoDeContagem {

    /** Codigo que esta convencao implementa. */
    ConvencaoContagem codigo();

    /**
     * Expoente da formula, ja normalizado para a unidade de capitalizacao
     * declarada pelo codigo.
     */
    BigDecimal calcularExpoente(LocalDate inicio, LocalDate vencimento);

    /** Unidade em que o expoente sai. Delegada ao enum, fonte unica do mapa. */
    default Periodicidade periodicidadeEsperada() {
        return codigo().periodicidadeEsperada();
    }
}
