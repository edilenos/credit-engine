package br.com.srm.creditengine.negocio.precificacao.convencao;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.PrecisaoDecimal;
import br.com.srm.creditengine.negocio.precificacao.ConvencaoDeContagem;

/**
 * Convencao comercial 30/360, produzindo MESES.
 *
 * <p>Nao herda de {@link ContagemPorDiasCorridos} porque nao conta dias
 * corridos: conta meses comerciais, em que todo mes tem 30 dias e todo ano tem
 * 360. E' a diferenca de <b>logica</b>, e nao so de denominador, que justifica
 * esta familia ser Strategy e nao um mapa de divisores.
 *
 * <p>Formula (convencao US/NASD), com os ajustes de borda que a tornam
 * traicoeira:
 *
 * <pre>
 *   se D1 = 31              -> D1 = 30
 *   se D2 = 31 e D1 &gt;= 30    -> D2 = 30
 *   dias = 360*(A2-A1) + 30*(M2-M1) + (D2-D1)
 * </pre>
 *
 * <p>Sem os ajustes, 31/01 a 31/03 daria 61 dias em vez de 60, e um mes
 * fechado apareceria como mes e um dia. O segundo ajuste depende do primeiro
 * ter acontecido — implementa-los na ordem errada produz resultado errado
 * apenas em datas especificas, que e' o pior tipo de bug.
 */
@Component
public class ContagemComercial30360 implements ConvencaoDeContagem {

    private static final BigDecimal DIAS_POR_MES_COMERCIAL = new BigDecimal("30");

    @Override
    public ConvencaoContagem codigo() {
        return ConvencaoContagem.COMERCIAL_30_360;
    }

    @Override
    public BigDecimal calcularExpoente(LocalDate inicio, LocalDate vencimento) {
        int d1 = inicio.getDayOfMonth();
        int d2 = vencimento.getDayOfMonth();

        // A ordem importa: o segundo ajuste testa D1 ja ajustado.
        if (d1 == 31) {
            d1 = 30;
        }
        if (d2 == 31 && d1 >= 30) {
            d2 = 30;
        }

        long dias = 360L * (vencimento.getYear() - inicio.getYear())
                + 30L * (vencimento.getMonthValue() - inicio.getMonthValue())
                + (d2 - d1);

        return PrecisaoDecimal.comoExpoente(
                PrecisaoDecimal.dividir(BigDecimal.valueOf(dias), DIAS_POR_MES_COMERCIAL));
    }
}
