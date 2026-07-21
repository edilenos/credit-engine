package br.com.srm.creditengine.negocio.precificacao.convencao;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import br.com.srm.creditengine.dominio.ConvencaoContagem;

/** Dias corridos sobre 30, produzindo MESES. Padrao do projeto: e a unica que casa direto com o spread cotado a.m. do enunciado. */
@Component
public class ContagemAct30 extends ContagemPorDiasCorridos {

    private static final BigDecimal BASE = new BigDecimal("30");

    @Override
    public ConvencaoContagem codigo() {
        return ConvencaoContagem.ACT_30;
    }

    @Override
    protected BigDecimal base() {
        return BASE;
    }
}
