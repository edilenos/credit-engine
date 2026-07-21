package br.com.srm.creditengine.negocio.precificacao.convencao;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import br.com.srm.creditengine.dominio.ConvencaoContagem;

/** Dias corridos sobre 360, produzindo ANOS. Convencao comercial ACT/360, comum em papel de curto prazo. */
@Component
public class ContagemAct360 extends ContagemPorDiasCorridos {

    private static final BigDecimal BASE = new BigDecimal("360");

    @Override
    public ConvencaoContagem codigo() {
        return ConvencaoContagem.ACT_360;
    }

    @Override
    protected BigDecimal base() {
        return BASE;
    }
}
