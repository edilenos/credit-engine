package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.Periodicidade;

/**
 * Seleciona a convencao de contagem e produz o expoente, validando a unidade
 * antes de calcular.
 *
 * <p>Recebe as convencoes por injecao e indexa por codigo — sem {@code if} nem
 * {@code switch}. Registrar uma convencao nova e' criar uma classe anotada com
 * {@code @Component}; nada aqui nem no motor de calculo muda.
 *
 * <h2>A validacao de unidade e a razao de esta classe existir</h2>
 *
 * Poderia bastar um mapa de convencoes. O que justifica um resolvedor e' o
 * ponto de controle: {@code (1 + taxa)^expoente} so e' valido quando os dois
 * estao na mesma unidade de capitalizacao, e combinar taxa mensal com
 * convencao anual <b>nao lanca excecao naturalmente</b> — produz preco
 * plausivel e errado por ordem de grandeza.
 *
 * <p>Concentrar a checagem aqui significa que nenhum chamador pode esquece-la:
 * nao ha caminho para obter um expoente sem passar pela validacao do par.
 */
@Component
public class ResolvedorDeConvencao {

    private final Map<ConvencaoContagem, ConvencaoDeContagem> porCodigo =
            new EnumMap<>(ConvencaoContagem.class);

    public ResolvedorDeConvencao(List<ConvencaoDeContagem> convencoes) {
        convencoes.forEach(convencao -> porCodigo.put(convencao.codigo(), convencao));
    }

    /**
     * Expoente da formula para o intervalo, na unidade da convencao.
     *
     * @param periodicidadeDaTaxa unidade em que a taxa esta cotada; precisa
     *        casar com a que a convencao produz
     * @throws UnidadeIncompativelException se o par nao casar — sempre antes de
     *         qualquer calculo
     * @throws ConvencaoNaoImplementadaException se o codigo nao tiver
     *         implementacao registrada
     */
    public BigDecimal expoente(ConvencaoContagem convencao,
                               Periodicidade periodicidadeDaTaxa,
                               LocalDate inicio,
                               LocalDate vencimento) {

        if (!convencao.compativelCom(periodicidadeDaTaxa)) {
            throw new UnidadeIncompativelException(
                    convencao, periodicidadeDaTaxa, convencao.periodicidadeEsperada());
        }
        return implementacaoDe(convencao).calcularExpoente(inicio, vencimento);
    }

    private ConvencaoDeContagem implementacaoDe(ConvencaoContagem convencao) {
        ConvencaoDeContagem implementacao = porCodigo.get(convencao);
        if (implementacao == null) {
            throw new ConvencaoNaoImplementadaException(convencao);
        }
        return implementacao;
    }

    /** Codigos com implementacao registrada. Existe para o teste de extensibilidade. */
    public java.util.Set<ConvencaoContagem> convencoesRegistradas() {
        return java.util.Set.copyOf(porCodigo.keySet());
    }
}
