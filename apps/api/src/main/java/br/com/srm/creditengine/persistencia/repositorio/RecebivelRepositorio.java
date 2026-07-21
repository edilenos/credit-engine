package br.com.srm.creditengine.persistencia.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.creditengine.persistencia.entidade.Recebivel;

/**
 * Acesso aos recebiveis.
 *
 * <p>Recebivel nao existe fora do lote: a leitura natural e por operacao, e e
 * a unica chave estrangeira da tabela com indice proprio.
 */
public interface RecebivelRepositorio extends JpaRepository<Recebivel, Long> {

    List<Recebivel> findByOperacaoId(Long operacaoId);
}
