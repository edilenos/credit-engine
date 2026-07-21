package br.com.srm.creditengine.persistencia.entidade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import br.com.srm.creditengine.dominio.Periodicidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Taxa base do fundo, versionada por vigencia.
 *
 * <p>APPEND-ONLY: nunca sofre atualizacao. Cada mudanca insere linha nova, e a
 * vigente para uma data e' a mais recente com {@code vigenciaInicio <= data}.
 * Se fosse mutavel, reprecificar uma operacao passada daria resultado diferente
 * do que foi contratado. Por isso a entidade nao expoe setter.
 */
@Entity
@Table(name = "parametro_precificacao")
public class ParametroPrecificacao extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "taxa_base", nullable = false, precision = 9, scale = 6)
    private BigDecimal taxaBase;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicidade", nullable = false, length = 10)
    private Periodicidade periodicidade;

    @Column(name = "vigencia_inicio", nullable = false)
    private OffsetDateTime vigenciaInicio;

    protected ParametroPrecificacao() {
        // exigido pelo JPA
    }

    public ParametroPrecificacao(BigDecimal taxaBase, Periodicidade periodicidade,
                                 OffsetDateTime vigenciaInicio) {
        this.taxaBase = taxaBase;
        this.periodicidade = periodicidade;
        this.vigenciaInicio = vigenciaInicio;
    }

    @Override
    public Long getId() {
        return id;
    }

    public BigDecimal getTaxaBase() {
        return taxaBase;
    }

    public Periodicidade getPeriodicidade() {
        return periodicidade;
    }

    public OffsetDateTime getVigenciaInicio() {
        return vigenciaInicio;
    }
}
