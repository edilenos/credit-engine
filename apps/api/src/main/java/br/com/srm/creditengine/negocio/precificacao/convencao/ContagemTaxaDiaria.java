package br.com.srm.creditengine.negocio.precificacao.convencao;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import br.com.srm.creditengine.dominio.ConvencaoContagem;

/** Dias corridos sobre 1, produzindo DIAS. E a unica convencao cujo expoente e sempre inteiro, o que permite usar BigDecimal.pow(int) sem perda. */
@Component
public class ContagemTaxaDiaria extends ContagemPorDiasCorridos {

    private static final BigDecimal BASE = new BigDecimal("1");

    @Override
    public ConvencaoContagem codigo() {
        return ConvencaoContagem.TAXA_DIARIA;
    }

    @Override
    protected BigDecimal base() {
        return BASE;
    }
}
