package br.com.srm.creditengine.persistencia.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.creditengine.dominio.EntidadeAuditada;
import br.com.srm.creditengine.persistencia.entidade.EventoAuditoria;

/**
 * Acesso a trilha de auditoria.
 *
 * <p>Somente leitura e insercao. {@code JpaRepository} expoe {@code delete} e
 * {@code save} de atualizacao herdados, mas o trigger da tabela recusa os dois
 * — a garantia de append-only e' do banco, nao da disciplina de quem chama.
 */
public interface EventoAuditoriaRepositorio extends JpaRepository<EventoAuditoria, Long> {

    List<EventoAuditoria> findByEntidadeAndEntidadeIdOrderByIdAsc(
            EntidadeAuditada entidade, Long entidadeId);
}
