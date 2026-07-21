package br.com.srm.creditengine.persistencia.repositorio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.srm.creditengine.persistencia.entidade.TaxaCambio;

/**
 * Acesso as cotacoes de cambio.
 *
 * <p>A tabela e append-only: a cotacao vigente para uma data e a mais recente
 * com {@code vigenciaInicio <= data}, e nao simplesmente a ultima inserida.
 * Buscar "a mais recente" sem a data como corte quebraria a reprodutibilidade
 * de operacoes passadas.
 */
public interface TaxaCambioRepositorio extends JpaRepository<TaxaCambio, Long> {

    /**
     * O desempate por {@code id DESC} nao e detalhe: duas linhas podem
     * compartilhar a mesma vigencia quando uma cotacao e corrigida, e sem ele a
     * escolha entre elas seria indefinida. Em modelo append-only, corrigir e
     * acrescentar, e a ultima correcao e a que vale.
     */
    @Query("""
            SELECT t FROM TaxaCambio t
             WHERE t.moedaOrigem.codigo = :origem
               AND t.moedaDestino.codigo = :destino
               AND t.vigenciaInicio <= :momento
             ORDER BY t.vigenciaInicio DESC, t.id DESC
            """)
    List<TaxaCambio> buscarVigente(@Param("origem") String origem,
                                   @Param("destino") String destino,
                                   @Param("momento") OffsetDateTime momento,
                                   Limit limite);

    /** Atalho para a consulta acima, ja limitada a uma linha. */
    default Optional<TaxaCambio> vigenteEm(String origem, String destino, OffsetDateTime momento) {
        return buscarVigente(origem, destino, momento, Limit.of(1)).stream().findFirst();
    }

    /** Historico completo do par, do mais recente para o mais antigo. */
    @Query("""
            SELECT t FROM TaxaCambio t
             WHERE t.moedaOrigem.codigo = :origem
               AND t.moedaDestino.codigo = :destino
             ORDER BY t.vigenciaInicio DESC, t.id DESC
            """)
    List<TaxaCambio> historicoDoPar(@Param("origem") String origem,
                                    @Param("destino") String destino);
}
