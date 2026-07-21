package br.com.srm.creditengine.persistencia.repositorio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.creditengine.persistencia.entidade.Liquidacao;

/**
 * Acesso as liquidacoes.
 *
 * <p>A busca por chave de idempotencia e o caminho que torna o retry seguro:
 * encontrando a chave, devolve-se o resultado original em vez de liquidar de
 * novo. E a defesa que atua <b>antes</b> do banco recusar por UNIQUE — a
 * constraint continua sendo a rede de seguranca para o caso de corrida.
 */
public interface LiquidacaoRepositorio extends JpaRepository<Liquidacao, Long> {

    Optional<Liquidacao> findByChaveIdempotencia(String chaveIdempotencia);

    Optional<Liquidacao> findByOperacaoId(Long operacaoId);

    boolean existsByOperacaoId(Long operacaoId);
}
