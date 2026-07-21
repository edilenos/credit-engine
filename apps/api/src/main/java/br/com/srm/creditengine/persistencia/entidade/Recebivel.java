package br.com.srm.creditengine.persistencia.entidade;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Titulo individual dentro do lote, com os parametros de calculo congelados no
 * momento da operacao.
 *
 * <p>Escrito EM LOTE, por isso usa {@code SEQUENCE} e nao {@code IDENTITY}:
 * IDENTITY obriga o Hibernate a desabilitar batch de INSERT, porque precisa do
 * id gerado a cada linha.
 *
 * <p><b>O {@code allocationSize = 50} precisa bater com o {@code INCREMENT BY
 * 50} de {@code seq_recebivel} na migracao V2.</b> Divergencia entre os dois
 * gera colisao de id — e o erro classico dessa configuracao, e ha um teste de
 * schema travando o valor no banco.
 */
@Entity
@Table(name = "recebivel")
@SequenceGenerator(name = "seq_recebivel", sequenceName = "seq_recebivel", allocationSize = 50)
public class Recebivel extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_recebivel")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operacao_id", nullable = false)
    private Operacao operacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_recebivel_id", nullable = false)
    private TipoRecebivel tipo;

    /** Linhagem: qual parametro foi usado. O valor congelado vem abaixo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parametro_precificacao_id", nullable = false)
    private ParametroPrecificacao parametroPrecificacao;

    @Column(name = "numero_documento", nullable = false, length = 50)
    private String numeroDocumento;

    @Column(name = "sacado_documento", nullable = false, length = 14)
    private String sacadoDocumento;

    @Column(name = "valor_face", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorFace;

    /** Vencimento e dia, nao instante: LocalDate, nao OffsetDateTime. */
    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    /**
     * Convencao congelada. Nao se guarda "prazo em meses": com convencoes
     * multiplas, o mesmo intervalo de datas produz expoentes diferentes.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "convencao_aplicada", nullable = false, length = 20)
    private ConvencaoContagem convencaoAplicada;

    @Column(name = "expoente_aplicado", nullable = false, precision = 19, scale = 10)
    private BigDecimal expoenteAplicado;

    @Column(name = "taxa_base_aplicada", nullable = false, precision = 9, scale = 6)
    private BigDecimal taxaBaseAplicada;

    @Column(name = "spread_aplicado", nullable = false, precision = 9, scale = 6)
    private BigDecimal spreadAplicado;

    @Column(name = "valor_presente", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorPresente;

    protected Recebivel() {
        // exigido pelo JPA
    }

    public Recebivel(TipoRecebivel tipo, ParametroPrecificacao parametroPrecificacao,
                     String numeroDocumento, String sacadoDocumento, BigDecimal valorFace,
                     LocalDate dataVencimento, ConvencaoContagem convencaoAplicada,
                     BigDecimal expoenteAplicado, BigDecimal taxaBaseAplicada,
                     BigDecimal spreadAplicado, BigDecimal valorPresente) {
        this.tipo = tipo;
        this.parametroPrecificacao = parametroPrecificacao;
        this.numeroDocumento = numeroDocumento;
        this.sacadoDocumento = sacadoDocumento;
        this.valorFace = valorFace;
        this.dataVencimento = dataVencimento;
        this.convencaoAplicada = convencaoAplicada;
        this.expoenteAplicado = expoenteAplicado;
        this.taxaBaseAplicada = taxaBaseAplicada;
        this.spreadAplicado = spreadAplicado;
        this.valorPresente = valorPresente;
    }

    void vincularA(Operacao operacao) {
        this.operacao = operacao;
    }

    /** Desagio do titulo. Derivado, nao persistido. */
    public BigDecimal getDesagio() {
        return valorFace.subtract(valorPresente);
    }

    @Override
    public Long getId() {
        return id;
    }

    public Operacao getOperacao() {
        return operacao;
    }

    public TipoRecebivel getTipo() {
        return tipo;
    }

    public ParametroPrecificacao getParametroPrecificacao() {
        return parametroPrecificacao;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getSacadoDocumento() {
        return sacadoDocumento;
    }

    public BigDecimal getValorFace() {
        return valorFace;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public ConvencaoContagem getConvencaoAplicada() {
        return convencaoAplicada;
    }

    public BigDecimal getExpoenteAplicado() {
        return expoenteAplicado;
    }

    public BigDecimal getTaxaBaseAplicada() {
        return taxaBaseAplicada;
    }

    public BigDecimal getSpreadAplicado() {
        return spreadAplicado;
    }

    public BigDecimal getValorPresente() {
        return valorPresente;
    }
}
