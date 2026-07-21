package br.com.srm.creditengine.persistencia.calendario;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.negocio.precificacao.CalendarioDiasUteis;
import br.com.srm.creditengine.negocio.precificacao.DataForaDaCoberturaException;

/**
 * Calendario de dias uteis lido da tabela {@code feriado}.
 *
 * <p>Usa {@link JdbcClient} e nao JPA de proposito: e' consulta de referencia
 * somente leitura, sem agregado nem ciclo de vida. Uma entidade aqui pagaria o
 * custo do contexto de persistencia sem nenhum ganho.
 *
 * <p>A cobertura e' derivada dos anos presentes na tabela e mantida em cache:
 * a tabela e' semeada por migracao e nao muda em runtime. Se algum dia passar a
 * mudar, este cache e' o primeiro lugar a revisar.
 */
@Component
public class CalendarioDeFeriadosNoBanco implements CalendarioDiasUteis {

    private final JdbcClient jdbc;

    private volatile LocalDate inicioDaCobertura;
    private volatile LocalDate fimDaCobertura;

    public CalendarioDeFeriadosNoBanco(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public long diasUteisEntre(LocalDate inicio, LocalDate fim) {
        garantirCobertura(inicio);
        garantirCobertura(fim);

        Set<LocalDate> feriados = feriadosEntre(inicio, fim);

        long uteis = 0;
        for (LocalDate dia = inicio; dia.isBefore(fim); dia = dia.plusDays(1)) {
            if (ehDiaUtil(dia, feriados)) {
                uteis++;
            }
        }
        return uteis;
    }

    /**
     * Itera dia a dia em vez de calcular por aritmetica de semanas.
     *
     * <p>O intervalo e' limitado pela propria cobertura do calendario — poucos
     * milhares de dias no pior caso, microssegundos em memoria, com uma unica
     * consulta ao banco. A versao aritmetica seria mais rapida e bem mais
     * facil de errar nas bordas, e este e' um caminho onde errar por um dia
     * muda o preco.
     */
    private boolean ehDiaUtil(LocalDate dia, Set<LocalDate> feriados) {
        DayOfWeek diaDaSemana = dia.getDayOfWeek();
        return diaDaSemana != DayOfWeek.SATURDAY
                && diaDaSemana != DayOfWeek.SUNDAY
                && !feriados.contains(dia);
    }

    private Set<LocalDate> feriadosEntre(LocalDate inicio, LocalDate fim) {
        List<LocalDate> datas = jdbc.sql("""
                        SELECT data FROM feriado
                         WHERE data >= :inicio AND data < :fim
                        """)
                .param("inicio", inicio)
                .param("fim", fim)
                .query(LocalDate.class)
                .list();

        return new HashSet<>(datas);
    }

    @Override
    public LocalDate inicioDaCobertura() {
        carregarCoberturaSeNecessario();
        return inicioDaCobertura;
    }

    @Override
    public LocalDate fimDaCobertura() {
        carregarCoberturaSeNecessario();
        return fimDaCobertura;
    }

    private void garantirCobertura(LocalDate data) {
        carregarCoberturaSeNecessario();
        if (data.isBefore(inicioDaCobertura) || data.isAfter(fimDaCobertura)) {
            throw new DataForaDaCoberturaException(data, inicioDaCobertura, fimDaCobertura);
        }
    }

    /**
     * A cobertura sao os ANOS inteiros presentes na tabela, nao o intervalo
     * entre o primeiro e o ultimo feriado. Semear 2025-2030 significa cobrir de
     * 01/01/2025 a 31/12/2030, mesmo que o primeiro feriado do ano nao caia em
     * 1o de janeiro.
     */
    private void carregarCoberturaSeNecessario() {
        if (inicioDaCobertura != null) {
            return;
        }
        synchronized (this) {
            if (inicioDaCobertura != null) {
                return;
            }
            Integer primeiroAno = jdbc.sql("SELECT min(extract(year from data))::int FROM feriado")
                    .query(Integer.class).optional().orElse(null);
            Integer ultimoAno = jdbc.sql("SELECT max(extract(year from data))::int FROM feriado")
                    .query(Integer.class).optional().orElse(null);

            if (primeiroAno == null || ultimoAno == null) {
                throw new IllegalStateException(
                        "Tabela de feriados vazia: a convencao BUS_252 nao tem como contar dias uteis");
            }
            this.fimDaCobertura = LocalDate.of(ultimoAno, 12, 31);
            this.inicioDaCobertura = LocalDate.of(primeiroAno, 1, 1);
        }
    }
}
