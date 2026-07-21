package br.com.srm.creditengine.negocio.precificacao.convencao;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import br.com.srm.creditengine.dominio.ConvencaoContagem;

/** Dias corridos sobre 365, produzindo ANOS. ACT/365 fixo: nao ajusta para ano bissexto, ao contrario de ACT/ACT. */
@Component
public class ContagemAct365 extends ContagemPorDiasCorridos {

    private static final BigDecimal BASE = new BigDecimal("365");

    @Override
    public ConvencaoContagem codigo() {
        return ConvencaoContagem.ACT_365;
    }

    @Override
    protected BigDecimal base() {
        return BASE;
    }
}
