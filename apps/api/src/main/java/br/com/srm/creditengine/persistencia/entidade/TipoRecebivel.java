package br.com.srm.creditengine.persistencia.entidade;

import java.math.BigDecimal;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
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
 * Produto adquirido pelo fundo. O {@code codigo} e' a chave que resolve a
 * Strategy de spread (PBI-18).
 *
 * <p>O valor numerico do spread mora aqui, para ajuste sem deploy; a REGRA de
 * derivacao mora na Strategy. E' essa separacao que impede o padrao de virar um
 * {@code Map} disfarcado.
 */
@Entity
@Table(name = "tipo_recebivel")
public class TipoRecebivel extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, length = 40)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    /** Premio de risco. BigDecimal sempre: taxa entra em exponenciacao. */
    @Column(name = "spread", nullable = false, precision = 9, scale = 6)
    private BigDecimal spread;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicidade", nullable = false, length = 10)
    private Periodicidade periodicidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "convencao_contagem", nullable = false, length = 20)
    private ConvencaoContagem convencaoContagem;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected TipoRecebivel() {
        // exigido pelo JPA
    }

    public TipoRecebivel(String codigo, String nome, BigDecimal spread,
                         Periodicidade periodicidade, ConvencaoContagem convencaoContagem) {
        this.codigo = codigo;
        this.nome = nome;
        this.spread = spread;
        this.periodicidade = periodicidade;
        this.convencaoContagem = convencaoContagem;
    }

    /**
     * Indica se o par periodicidade/convencao deste produto e' coerente. Par
     * incoerente nao lanca erro no calculo: produz preco errado por ordem de
     * grandeza (risco R7).
     */
    public boolean temUnidadesCoerentes() {
        return convencaoContagem.compativelCom(periodicidade);
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getSpread() {
        return spread;
    }

    public Periodicidade getPeriodicidade() {
        return periodicidade;
    }

    public ConvencaoContagem getConvencaoContagem() {
        return convencaoContagem;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
