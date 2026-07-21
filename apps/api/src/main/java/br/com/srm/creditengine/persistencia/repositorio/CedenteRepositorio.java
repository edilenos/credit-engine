package br.com.srm.creditengine.persistencia.repositorio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.creditengine.persistencia.entidade.Cedente;

/** Acesso aos cedentes. O CNPJ e a chave de negocio. */
public interface CedenteRepositorio extends JpaRepository<Cedente, Long> {

    Optional<Cedente> findByDocumento(String documento);
}
