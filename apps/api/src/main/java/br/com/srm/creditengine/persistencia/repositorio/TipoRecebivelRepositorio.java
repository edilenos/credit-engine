package br.com.srm.creditengine.persistencia.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;

/**
 * Acesso aos tipos de recebivel.
 *
 * <p>O codigo e a chave que resolve a Strategy de spread (PBI-18), entao a
 * busca por codigo e o caminho quente deste repositorio.
 */
public interface TipoRecebivelRepositorio extends JpaRepository<TipoRecebivel, Long> {

    Optional<TipoRecebivel> findByCodigo(String codigo);

    List<TipoRecebivel> findByAtivoTrue();
}
