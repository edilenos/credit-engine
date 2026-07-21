package br.com.srm.creditengine.negocio.precificacao.convencao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import br.com.srm.creditengine.dominio.PrecisaoDecimal;
import br.com.srm.creditengine.negocio.precificacao.ConvencaoDeContagem;

/**
 * Base das convencoes que contam <b>dias corridos</b> e dividem por uma base
 * fixa.
 *
 * <p>Quatro das cinco convencoes deste projeto sao exatamente isso, diferindo
 * so no denominador: ACT/30 produz meses, ACT/360 e ACT/365 produzem anos, e a
 * taxa diaria divide por 1. Duplicar a contagem em quatro classes so criaria
 * quatro lugares para o mesmo bug de fuso ou de borda.
 *
 * <p>A excecao e' a regra 30/360, que conta meses comerciais com ajuste de
 * borda e por isso nao herda daqui.
 */
public abstract class ContagemPorDiasCorridos implements ConvencaoDeContagem {

    /** Denominador que normaliza os dias para a unidade da convencao. */
    protected abstract BigDecimal base();

    @Override
    public BigDecimal calcularExpoente(LocalDate inicio, LocalDate vencimento) {
        long dias = ChronoUnit.DAYS.between(inicio, vencimento);

        return PrecisaoDecimal.comoExpoente(
                PrecisaoDecimal.dividir(BigDecimal.valueOf(dias), base()));
    }
}
