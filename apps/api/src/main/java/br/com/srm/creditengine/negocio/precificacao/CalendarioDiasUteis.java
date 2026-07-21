package br.com.srm.creditengine.negocio.precificacao;

import java.time.LocalDate;

/**
 * Fonte de dias uteis, usada pela convencao de contagem BUS_252.
 *
 * <p>E' uma porta, nao uma implementacao: hoje o calendario vem de uma tabela
 * semeada por migracao, e trocar por um servico externo (ANBIMA, B3) e'
 * implementar esta interface. A convencao de contagem nao muda.
 *
 * <p>Contagem em intervalo semiaberto {@code [inicio, fim)} — mesma convencao
 * que {@code ChronoUnit.DAYS.between} usa para os dias corridos das outras
 * convencoes. Misturar as duas produziria um dia de diferenca entre ACT e BUS
 * para o mesmo intervalo.
 */
public interface CalendarioDiasUteis {

    /**
     * Dias uteis em {@code [inicio, fim)}: exclui sabados, domingos e feriados.
     *
     * @throws DataForaDaCoberturaException se o intervalo sair da faixa que o
     *         calendario conhece — contar dias uteis com feriados faltando
     *         devolveria um numero maior que o real, em silencio
     */
    long diasUteisEntre(LocalDate inicio, LocalDate fim);

    /** Primeiro dia coberto pelo calendario. */
    LocalDate inicioDaCobertura();

    /** Ultimo dia coberto pelo calendario. */
    LocalDate fimDaCobertura();
}
