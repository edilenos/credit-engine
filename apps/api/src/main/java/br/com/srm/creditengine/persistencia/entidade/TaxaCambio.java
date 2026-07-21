package br.com.srm.creditengine.persistencia.entidade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import br.com.srm.creditengine.dominio.FonteCotacao;
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
import jakarta.persistence.Table;

/**
 * Cotacao de um par de moedas, versionada por vigencia.
 *
 * <p>APPEND-ONLY, pelo mesmo motivo de {@link ParametroPrecificacao}: a
 * precificacao de ontem precisa continuar reproduzivel.
 */
@Entity
@Table(name = "taxa_cambio")
public class TaxaCambio extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moeda_origem_id", nullable = false)
    private Moeda moedaOrigem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moeda_destino_id", nullable = false)
    private Moeda moedaDestino;

    /** Unidades da moeda de destino por unidade da moeda de origem. */
    @Column(name = "cotacao", nullable = false, precision = 19, scale = 6)
    private BigDecimal cotacao;

    @Column(name = "vigencia_inicio", nullable = false)
    private OffsetDateTime vigenciaInicio;

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte", nullable = false, length = 20)
    private FonteCotacao fonte;

    protected TaxaCambio() {
        // exigido pelo JPA
    }

    public TaxaCambio(Moeda moedaOrigem, Moeda moedaDestino, BigDecimal cotacao,
                      OffsetDateTime vigenciaInicio, FonteCotacao fonte) {
        this.moedaOrigem = moedaOrigem;
        this.moedaDestino = moedaDestino;
        this.cotacao = cotacao;
        this.vigenciaInicio = vigenciaInicio;
        this.fonte = fonte;
    }

    @Override
    public Long getId() {
        return id;
    }

    public Moeda getMoedaOrigem() {
        return moedaOrigem;
    }

    public Moeda getMoedaDestino() {
        return moedaDestino;
    }

    public BigDecimal getCotacao() {
        return cotacao;
    }

    public OffsetDateTime getVigenciaInicio() {
        return vigenciaInicio;
    }

    public FonteCotacao getFonte() {
        return fonte;
    }
}
