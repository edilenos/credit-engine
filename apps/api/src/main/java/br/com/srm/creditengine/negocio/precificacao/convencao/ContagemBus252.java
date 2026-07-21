package br.com.srm.creditengine.negocio.precificacao.convencao;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.PrecisaoDecimal;
import br.com.srm.creditengine.negocio.precificacao.CalendarioDiasUteis;
import br.com.srm.creditengine.negocio.precificacao.ConvencaoDeContagem;

/**
 * Convencao de dias uteis sobre 252, produzindo ANOS.
 *
 * <p>E' a convencao de renda fixa brasileira: CDI, LTN, debenture indexada.
 * O 252 e' a media de dias uteis num ano civil brasileiro — nao uma constante
 * arbitraria, e' a contagem que o mercado padronizou.
 *
 * <p>Unica convencao que depende de dado externo. As outras cinco derivam o
 * expoente das proprias datas; esta precisa saber quais dias sao uteis, o que
 * significa feriado nacional, feriado movel atrelado a Pascoa, e a limitacao de
 * cobertura que vem junto. E' por isso que ela ficou separada no backlog: e' o
 * ponto de corte natural se o prazo apertar, e corta-la deixa as outras cinco
 * intactas.
 *
 * <p>A taxa precisa estar cotada ao ANO. Combinar spread mensal com esta
 * convencao produziria preco errado por ordem de grandeza, e o
 * {@code ResolvedorDeConvencao} recusa o par antes de calcular.
 */
@Component
public class ContagemBus252 implements ConvencaoDeContagem {

    private static final BigDecimal DIAS_UTEIS_POR_ANO = new BigDecimal("252");

    private final CalendarioDiasUteis calendario;

    public ContagemBus252(CalendarioDiasUteis calendario) {
        this.calendario = calendario;
    }

    @Override
    public ConvencaoContagem codigo() {
        return ConvencaoContagem.BUS_252;
    }

    @Override
    public BigDecimal calcularExpoente(LocalDate inicio, LocalDate vencimento) {
        long diasUteis = calendario.diasUteisEntre(inicio, vencimento);

        return PrecisaoDecimal.comoExpoente(
                PrecisaoDecimal.dividir(BigDecimal.valueOf(diasUteis), DIAS_UTEIS_POR_ANO));
    }
}
