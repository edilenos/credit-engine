package br.com.srm.creditengine.negocio.cambio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.dominio.FonteCotacao;
import br.com.srm.creditengine.dominio.PrecisaoDecimal;
import br.com.srm.creditengine.persistencia.entidade.Moeda;
import br.com.srm.creditengine.persistencia.entidade.TaxaCambio;
import br.com.srm.creditengine.persistencia.repositorio.MoedaRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.TaxaCambioRepositorio;

/**
 * Motor de cambio: guarda o historico de cotacoes e resolve qual valia em cada
 * momento.
 *
 * <p>Camada de negocio. Nao conhece HTTP nem DTO — os endpoints (PBI-14) e o
 * provedor externo com resiliencia (PBI-15) ficam por fora.
 *
 * <h2>Append-only</h2>
 *
 * Cotacao nunca e' sobrescrita. Registrar uma nova insere linha com a vigencia
 * informada, e as anteriores permanecem. A vigente para uma data e' a mais
 * recente com {@code vigenciaInicio <= data} — nao a ultima cadastrada em
 * termos absolutos. E' isso que mantem a precificacao de ontem reproduzivel:
 * se a cotacao fosse mutavel, recalcular uma operacao passada daria resultado
 * diferente do que foi contratado.
 *
 * <h2>Correcao de cotacao</h2>
 *
 * Duas linhas podem compartilhar a mesma vigencia. Nesse caso vence a inserida
 * por ultimo, resolvido por {@code id DESC} no desempate do repositorio. Nao e'
 * contorno: em modelo append-only, corrigir e' acrescentar, e a ultima
 * correcao e' a que vale.
 *
 * <h2>O sistema nao deriva cotacao inversa</h2>
 *
 * Pedir USD para BRL quando so existe BRL para USD e' erro de negocio, nao
 * motivo para calcular {@code 1/cotacao}. Duas razoes:
 *
 * <ol>
 *   <li><b>Cambio real nao e' reciproco.</b> Compra e venda tem precos
 *       diferentes. O proprio seed reflete isso: BRL para USD a 0,185 e USD
 *       para BRL a 5,40, quando 1/0,185 seria 5,4054. Derivar produziria uma
 *       cotacao que ninguem cotou.</li>
 *   <li><b>A divisao introduz arredondamento no caminho do dinheiro.</b>
 *       1/0,185 e' dizima, e o erro entraria antes mesmo da conversao.</li>
 * </ol>
 *
 * Os dois sentidos sao cadastrados de forma independente.
 */
@Service
public class ServicoDeCambio {

    private final TaxaCambioRepositorio cotacoes;
    private final MoedaRepositorio moedas;

    public ServicoDeCambio(TaxaCambioRepositorio cotacoes, MoedaRepositorio moedas) {
        this.cotacoes = cotacoes;
        this.moedas = moedas;
    }

    /**
     * Cotacao vigente do par na data informada.
     *
     * @throws CotacaoIndisponivelException se nao houver cotacao vigente ate a
     *         data — inclusive quando o par so existe no sentido contrario
     */
    @Transactional(readOnly = true)
    public TaxaCambio cotacaoVigente(String origem, String destino, OffsetDateTime momento) {
        return cotacoes.vigenteEm(origem, destino, momento)
                .orElseThrow(() -> new CotacaoIndisponivelException(origem, destino, momento));
    }

    /**
     * Converte um valor entre moedas usando a cotacao vigente na data.
     *
     * <p>O arredondamento usa a escala da moeda de DESTINO, lida de
     * {@code moeda.escala_padrao} — nao uma constante. BRL e USD usam 2 casas,
     * mas JPY usa 0 e KWD usa 3, e fixar {@code scale=2} quebraria na primeira
     * moeda nao centesimal.
     *
     * <p>O modo de arredondamento vem de {@link PrecisaoDecimal}, ponto unico da
     * politica: HALF_EVEN, que nao enviesa a soma de muitas conversoes para um
     * dos lados como HALF_UP faria.
     */
    @Transactional(readOnly = true)
    public Conversao converter(BigDecimal valor, String origem, String destino, OffsetDateTime momento) {
        TaxaCambio cotacao = cotacaoVigente(origem, destino, momento);
        int escalaDestino = cotacao.getMoedaDestino().getEscalaPadrao();

        BigDecimal convertido = PrecisaoDecimal.comoMoeda(
                valor.multiply(cotacao.getCotacao(), PrecisaoDecimal.CONTEXTO),
                escalaDestino);

        return new Conversao(convertido, cotacao);
    }

    /**
     * Registra uma cotacao. Nao atualiza nada: acrescenta.
     *
     * <p>As validacoes ficam aqui, e nao apenas no DTO da borda, porque o
     * provedor externo (PBI-15) tambem chama este metodo sem passar por DTO
     * nenhum. Invariante de dominio protegida so na camada web e' invariante
     * que o primeiro chamador alternativo fura.
     *
     * @throws MoedaDesconhecidaException   se algum dos codigos nao existir
     * @throws ParDeMoedasInvalidoException se origem e destino coincidirem
     * @throws CotacaoInvalidaException     se a cotacao nao for positiva
     */
    @Transactional
    public TaxaCambio registrar(String origem, String destino, BigDecimal cotacao,
                                OffsetDateTime vigenciaInicio, FonteCotacao fonte) {
        if (origem.equals(destino)) {
            throw new ParDeMoedasInvalidoException(origem);
        }
        if (cotacao == null || cotacao.signum() <= 0) {
            throw new CotacaoInvalidaException(cotacao);
        }
        Moeda moedaOrigem = buscarMoeda(origem);
        Moeda moedaDestino = buscarMoeda(destino);

        return cotacoes.save(new TaxaCambio(moedaOrigem, moedaDestino, cotacao, vigenciaInicio, fonte));
    }

    /** Historico completo do par, do mais recente para o mais antigo. */
    @Transactional(readOnly = true)
    public List<TaxaCambio> historico(String origem, String destino) {
        return cotacoes.historicoDoPar(origem, destino);
    }

    /**
     * Historico paginado. E' a forma que a API expoe: nenhuma resposta de
     * colecao pode ser ilimitada, porque o par mais movimentado cresce sem
     * teto num modelo append-only.
     */
    @Transactional(readOnly = true)
    public Page<TaxaCambio> historico(String origem, String destino, Pageable pagina) {
        return cotacoes.historicoDoPar(origem, destino, pagina);
    }

    /** Busca uma cotacao pelo identificador, para o {@code Location} do POST. */
    @Transactional(readOnly = true)
    public TaxaCambio porId(Long id) {
        return cotacoes.findById(id)
                .orElseThrow(() -> new CotacaoNaoEncontradaException(id));
    }

    private Moeda buscarMoeda(String codigo) {
        return moedas.findByCodigo(codigo)
                .orElseThrow(() -> new MoedaDesconhecidaException(codigo));
    }
}
