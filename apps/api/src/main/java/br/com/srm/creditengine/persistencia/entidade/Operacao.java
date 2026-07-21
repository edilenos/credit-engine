package br.com.srm.creditengine.persistencia.entidade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import br.com.srm.creditengine.dominio.StatusOperacao;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Cessao de um lote de recebiveis por um cedente. Unidade de negocio e de
 * transacao: ou o lote inteiro entra, ou nada entra.
 *
 * <p>Raiz do agregado. Escrita uma linha por requisicao, por isso usa
 * {@code IDENTITY} — nao ha lote para agrupar.
 */
@Entity
@Table(name = "operacao")
public class Operacao extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cedente_id", nullable = false)
    private Cedente cedente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moeda_titulo_id", nullable = false)
    private Moeda moedaTitulo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moeda_liquidacao_id", nullable = false)
    private Moeda moedaLiquidacao;

    /** Nulo quando a operacao e em moeda unica. O banco garante a coerencia. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxa_cambio_id")
    private TaxaCambio taxaCambio;

    @Column(name = "valor_face_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorFaceTotal;

    @Column(name = "valor_presente_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorPresenteTotal;

    /** Valor presente apos a conversao cambial, na moeda de liquidacao. */
    @Column(name = "valor_liquidacao", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorLiquidacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private StatusOperacao status = StatusOperacao.PENDENTE;

    /**
     * Optimistic locking. Duas liquidacoes concorrentes: uma vence, a outra
     * falha com conflito de versao e vira 409. E a primeira das tres defesas
     * contra liquidacao dupla; as outras duas sao constraints UNIQUE no banco.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Preenchido pelo {@code DEFAULT now()} da coluna.
     *
     * <p>O {@code @Generated} nao e' decorativo: sem ele o Hibernate nunca le o
     * valor de volta, e a resposta do POST sai com {@code criadoEm: null} para
     * uma linha que tem timestamp no banco. O GET seguinte mostra o valor
     * certo, entao a divergencia so aparece comparando as duas respostas — foi
     * assim que apareceu aqui.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @OneToMany(mappedBy = "operacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Recebivel> recebiveis = new ArrayList<>();

    protected Operacao() {
        // exigido pelo JPA
    }

    public Operacao(Cedente cedente, Moeda moedaTitulo, Moeda moedaLiquidacao,
                    TaxaCambio taxaCambio, BigDecimal valorFaceTotal,
                    BigDecimal valorPresenteTotal, BigDecimal valorLiquidacao) {
        this.cedente = cedente;
        this.moedaTitulo = moedaTitulo;
        this.moedaLiquidacao = moedaLiquidacao;
        this.taxaCambio = taxaCambio;
        this.valorFaceTotal = valorFaceTotal;
        this.valorPresenteTotal = valorPresenteTotal;
        this.valorLiquidacao = valorLiquidacao;
    }

    /** Mantem os dois lados da associacao coerentes. */
    public void adicionar(Recebivel recebivel) {
        recebiveis.add(recebivel);
        recebivel.vincularA(this);
    }

    public boolean isCrossCurrency() {
        return !moedaTitulo.equals(moedaLiquidacao);
    }

    /** Desagio da operacao. Derivado, nao persistido. */
    public BigDecimal getDesagio() {
        return valorFaceTotal.subtract(valorPresenteTotal);
    }

    public void marcarLiquidada() {
        this.status = StatusOperacao.LIQUIDADA;
    }

    public void cancelar() {
        this.status = StatusOperacao.CANCELADA;
    }

    @Override
    public Long getId() {
        return id;
    }

    public Cedente getCedente() {
        return cedente;
    }

    public Moeda getMoedaTitulo() {
        return moedaTitulo;
    }

    public Moeda getMoedaLiquidacao() {
        return moedaLiquidacao;
    }

    public TaxaCambio getTaxaCambio() {
        return taxaCambio;
    }

    public BigDecimal getValorFaceTotal() {
        return valorFaceTotal;
    }

    public BigDecimal getValorPresenteTotal() {
        return valorPresenteTotal;
    }

    public BigDecimal getValorLiquidacao() {
        return valorLiquidacao;
    }

    public StatusOperacao getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public List<Recebivel> getRecebiveis() {
        return Collections.unmodifiableList(recebiveis);
    }
}
