package br.com.srm.creditengine.persistencia.entidade;

import java.time.OffsetDateTime;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import br.com.srm.creditengine.dominio.EntidadeAuditada;
import br.com.srm.creditengine.dominio.TipoEvento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Um fato registrado na trilha de auditoria.
 *
 * <p><b>Sem setters e sem construtor publico vazio.</b> A imutabilidade nao e'
 * estilo: a tabela tem trigger que recusa {@code UPDATE} e {@code DELETE}, e uma
 * entidade com setter permitiria ao codigo tentar algo que o banco vai negar —
 * descobrindo o erro no flush, longe de onde foi causado.
 *
 * <p>A referencia a entidade auditada e' <b>polimorfica</b>
 * ({@code entidade} + {@code entidadeId}), sem FK. Perde-se integridade
 * declarativa e ganha-se uma trilha unica em vez de uma tabela de auditoria por
 * tabela auditada. Para registro, e nao navegacao, a troca compensa — a decisao
 * esta no comentario da migracao {@code V2}.
 */
@Entity
@Table(name = "evento_auditoria")
public class EventoAuditoria extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_evento_auditoria")
    @jakarta.persistence.SequenceGenerator(
            name = "seq_evento_auditoria", sequenceName = "seq_evento_auditoria",
            allocationSize = 50)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entidade", nullable = false, length = 40)
    private EntidadeAuditada entidade;

    @Column(name = "entidade_id", nullable = false)
    private Long entidadeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 40)
    private TipoEvento tipoEvento;

    /**
     * Fotografia do fato, em JSON.
     *
     * <p>{@code jsonb} e nao {@code text}: a coluna e' consultavel por campo, o
     * que e' a diferenca entre uma trilha auditavel e um log que so serve para
     * ler com o olho.
     *
     * <p>O conteudo e' <b>selecionado</b>, nunca a entidade serializada inteira.
     * Despejar o objeto grava dado desnecessario hoje e vaza qualquer campo
     * sensivel que a entidade venha a ganhar amanha, sem ninguem perceber.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Generated(event = EventType.INSERT)
    @Column(name = "ocorrido_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime ocorridoEm;

    @Column(name = "ator", nullable = false, length = 80)
    private String ator;

    protected EventoAuditoria() {
        // exigido pelo JPA
    }

    public EventoAuditoria(EntidadeAuditada entidade, Long entidadeId, TipoEvento tipoEvento,
                           String payload, String ator) {
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.tipoEvento = tipoEvento;
        this.payload = payload;
        this.ator = ator;
    }

    @Override
    public Long getId() {
        return id;
    }

    public EntidadeAuditada getEntidade() {
        return entidade;
    }

    public Long getEntidadeId() {
        return entidadeId;
    }

    public TipoEvento getTipoEvento() {
        return tipoEvento;
    }

    public String getPayload() {
        return payload;
    }

    public OffsetDateTime getOcorridoEm() {
        return ocorridoEm;
    }

    public String getAtor() {
        return ator;
    }
}
