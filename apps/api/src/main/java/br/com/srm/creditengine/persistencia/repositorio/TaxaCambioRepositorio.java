package br.com.srm.creditengine.persistencia.repositorio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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
     * Toda consulta aqui traz {@code moedaOrigem} e {@code moedaDestino}
     * carregadas, e isso <b>nao e' otimizacao</b>: e' requisito de corretude.
     *
     * <p>As duas associacoes sao LAZY, {@code open-in-view} esta desligado, e
     * {@code CotacaoResponse.de()} le o codigo das duas. Sem o fetch, a sessao
     * ja fechou quando o mapeamento roda e o endpoint responde 500.
     *
     * <p>O defeito existiu de verdade, do PBI-25 ate depois da v1.0.0, e passou
     * despercebido porque {@code CambioControllerTest} e' {@code @Transactional}
     * — ali a sessao fica aberta a requisicao inteira e o mapeamento funciona.
     * Quem cobre isso agora e' {@code CambioForaDaTransacaoTest}, sem transacao.
     *
     * <p>{@code JOIN FETCH} e' seguro com paginacao aqui porque as duas
     * associacoes sao {@code @ManyToOne}: elas nao multiplicam linhas, entao o
     * Hibernate nao precisa paginar em memoria.
     */
    @Override
    @EntityGraph(attributePaths = {"moedaOrigem", "moedaDestino"})
    Optional<TaxaCambio> findById(Long id);

    /**
     * O desempate por {@code id DESC} nao e detalhe: duas linhas podem
     * compartilhar a mesma vigencia quando uma cotacao e corrigida, e sem ele a
     * escolha entre elas seria indefinida. Em modelo append-only, corrigir e
     * acrescentar, e a ultima correcao e a que vale.
     */
    @Query("""
            SELECT t FROM TaxaCambio t
             JOIN FETCH t.moedaOrigem
             JOIN FETCH t.moedaDestino
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
             JOIN FETCH t.moedaOrigem
             JOIN FETCH t.moedaDestino
             WHERE t.moedaOrigem.codigo = :origem
               AND t.moedaDestino.codigo = :destino
             ORDER BY t.vigenciaInicio DESC, t.id DESC
            """)
    List<TaxaCambio> historicoDoPar(@Param("origem") String origem,
                                    @Param("destino") String destino);

    /**
     * Versao paginada do historico, que e a exposta pela API.
     *
     * <p>Colecao ilimitada nao e opcao: em modelo append-only o par mais
     * movimentado cresce sem teto, e uma resposta que devolve tudo degrada em
     * silencio ate o dia em que derruba o processo.
     *
     * <p>Sem {@code ORDER BY} na consulta: a ordenacao vem do {@link Pageable},
     * e declarar as duas coisas faria o Spring Data gerar SQL com ordenacao
     * duplicada.
     */
    @Query("""
            SELECT t FROM TaxaCambio t
             JOIN FETCH t.moedaOrigem
             JOIN FETCH t.moedaDestino
             WHERE t.moedaOrigem.codigo = :origem
               AND t.moedaDestino.codigo = :destino
            """)
    Page<TaxaCambio> historicoDoPar(@Param("origem") String origem,
                                    @Param("destino") String destino,
                                    Pageable pagina);
}
