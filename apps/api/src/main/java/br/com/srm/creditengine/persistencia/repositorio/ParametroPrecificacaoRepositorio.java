package br.com.srm.creditengine.persistencia.repositorio;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.srm.creditengine.persistencia.entidade.ParametroPrecificacao;

/**
 * Acesso a taxa base do fundo.
 *
 * <p>Append-only, como {@code taxa_cambio}: a vigente para uma data e a mais
 * recente com {@code vigenciaInicio <= data}.
 */
public interface ParametroPrecificacaoRepositorio extends JpaRepository<ParametroPrecificacao, Long> {

    @Query("""
            SELECT p FROM ParametroPrecificacao p
             WHERE p.vigenciaInicio <= :momento
             ORDER BY p.vigenciaInicio DESC
            """)
    Optional<ParametroPrecificacao> buscarVigente(@Param("momento") OffsetDateTime momento,
                                                  Limit limite);

    default Optional<ParametroPrecificacao> vigenteEm(OffsetDateTime momento) {
        return buscarVigente(momento, Limit.of(1));
    }
}
