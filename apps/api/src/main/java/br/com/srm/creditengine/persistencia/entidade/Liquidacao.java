package br.com.srm.creditengine.persistencia.entidade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Efetivacao financeira da operacao. Irreversivel e unica.
 *
 * <p>O {@code UNIQUE (operacao_id)} no banco e a segunda defesa contra
 * liquidacao dupla, e vale mesmo que a camada de negocio falhe. A terceira e a
 * chave de idempotencia: retry do cliente com a mesma chave devolve o resultado
 * original em vez de liquidar de novo.
 *
 * <p>Sem setters: liquidacao nao se corrige, se estorna.
 */
@Entity
@Table(name = "liquidacao")
public class Liquidacao extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operacao_id", nullable = false, unique = true)
    private Operacao operacao;

    @Column(name = "chave_idempotencia", nullable = false, length = 64)
    private String chaveIdempotencia;

    @Column(name = "valor_liquidado", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorLiquidado;

    /** Nulo quando a operacao e em moeda unica. Valor congelado. */
    @Column(name = "cotacao_aplicada", precision = 19, scale = 6)
    private BigDecimal cotacaoAplicada;

    @Column(name = "liquidado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime liquidadoEm;

    @Column(name = "liquidado_por", nullable = false, length = 80)
    private String liquidadoPor;

    protected Liquidacao() {
        // exigido pelo JPA
    }

    public Liquidacao(Operacao operacao, String chaveIdempotencia, BigDecimal valorLiquidado,
                      BigDecimal cotacaoAplicada, String liquidadoPor) {
        this.operacao = operacao;
        this.chaveIdempotencia = chaveIdempotencia;
        this.valorLiquidado = valorLiquidado;
        this.cotacaoAplicada = cotacaoAplicada;
        this.liquidadoPor = liquidadoPor;
    }

    @Override
    public Long getId() {
        return id;
    }

    public Operacao getOperacao() {
        return operacao;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public BigDecimal getValorLiquidado() {
        return valorLiquidado;
    }

    public BigDecimal getCotacaoAplicada() {
        return cotacaoAplicada;
    }

    public OffsetDateTime getLiquidadoEm() {
        return liquidadoEm;
    }

    public String getLiquidadoPor() {
        return liquidadoPor;
    }
}
