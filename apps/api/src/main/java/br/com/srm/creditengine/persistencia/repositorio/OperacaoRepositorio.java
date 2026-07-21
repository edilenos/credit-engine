package br.com.srm.creditengine.persistencia.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import br.com.srm.creditengine.dominio.StatusOperacao;
import br.com.srm.creditengine.persistencia.entidade.Operacao;

/**
 * Acesso as operacoes de cessao.
 *
 * <p>Repositorio do dominio, usado pela camada de negocio. O Extrato de
 * Liquidacao (PBI-35) <b>nao</b> passa por aqui: e consulta analitica, usa SQL
 * nativo e vive em pacote proprio, exercendo a excecao de duas camadas que o
 * paragrafo 3.6 do enunciado autoriza.
 */
public interface OperacaoRepositorio extends JpaRepository<Operacao, Long> {

    /**
     * Carrega a operacao com o lote em uma unica consulta.
     *
     * <p>O {@code EntityGraph} existe porque as associacoes sao LAZY e
     * {@code open-in-view} esta desligado: sem ele, acessar os recebiveis fora
     * da transacao levantaria {@code LazyInitializationException}, e acessa-los
     * dentro dispararia N+1.
     */
    @EntityGraph(attributePaths = "recebiveis")
    Optional<Operacao> findWithRecebiveisById(Long id);

    /**
     * Carrega a operacao com tudo que a resposta da API toca.
     *
     * <p>O grafo lista associacao por associacao de proposito. Faltando uma, o
     * mapeamento para DTO acontece com a sessao ja fechada e levanta
     * {@code LazyInitializationException} — falha que nenhum teste
     * {@code @Transactional} pega, porque neles a sessao continua aberta a
     * requisicao inteira. Ja aconteceu uma vez neste projeto, nos endpoints de
     * cambio.
     */
    @EntityGraph(attributePaths = {
            "cedente", "moedaTitulo", "moedaLiquidacao", "taxaCambio",
            "recebiveis", "recebiveis.tipo"
    })
    Optional<Operacao> findCompletaById(Long id);

    List<Operacao> findByCedenteIdAndStatus(Long cedenteId, StatusOperacao status);
}
