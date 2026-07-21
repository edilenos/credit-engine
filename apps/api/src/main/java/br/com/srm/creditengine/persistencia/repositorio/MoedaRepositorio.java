package br.com.srm.creditengine.persistencia.repositorio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.creditengine.persistencia.entidade.Moeda;

/** Acesso as moedas suportadas. O codigo ISO 4217 e a chave de negocio. */
public interface MoedaRepositorio extends JpaRepository<Moeda, Long> {

    Optional<Moeda> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}
