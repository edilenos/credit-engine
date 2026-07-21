package br.com.srm.creditengine.dominio;

/**
 * Convencao de contagem de dias: converte o intervalo entre duas datas no
 * expoente da formula de valor presente.
 *
 * <p>Cada uma declara a {@link Periodicidade} que produz, porque o expoente so
 * e' valido na mesma unidade de capitalizacao da taxa. As implementacoes das
 * regras entram no PBI-19; aqui ficam apenas os codigos, que precisam espelhar
 * o CHECK de dominio das migracoes V1 e V2.
 */
public enum ConvencaoContagem {

    /** Dias corridos sobre 30. Padrao do projeto: casa com spread mensal. */
    ACT_30(Periodicidade.MENSAL),

    /** Regra 30/360, com os ajustes de borda para o dia 31. */
    COMERCIAL_30_360(Periodicidade.MENSAL),

    /** Dias corridos sobre 360. */
    ACT_360(Periodicidade.ANUAL),

    /** Dias corridos sobre 365. */
    ACT_365(Periodicidade.ANUAL),

    /** Dias uteis sobre 252, convencao de renda fixa brasileira. */
    BUS_252(Periodicidade.ANUAL),

    /** Expoente em dias corridos, inteiro. Exige taxa diaria. */
    TAXA_DIARIA(Periodicidade.DIARIA);

    private final Periodicidade periodicidadeEsperada;

    ConvencaoContagem(Periodicidade periodicidadeEsperada) {
        this.periodicidadeEsperada = periodicidadeEsperada;
    }

    public Periodicidade periodicidadeEsperada() {
        return periodicidadeEsperada;
    }

    public boolean compativelCom(Periodicidade periodicidade) {
        return periodicidadeEsperada == periodicidade;
    }
}
