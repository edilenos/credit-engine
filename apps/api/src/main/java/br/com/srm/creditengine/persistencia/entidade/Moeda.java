package br.com.srm.creditengine.persistencia.entidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Moeda suportada, identificada pelo codigo ISO 4217. */
@Entity
@Table(name = "moeda")
public class Moeda extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, length = 3, columnDefinition = "bpchar")
    private String codigo;

    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    /**
     * Casas decimais da moeda, usada como escala de arredondamento. Orientada a
     * dado porque BRL e USD usam 2, mas JPY usa 0 e KWD usa 3 — {@code scale=2}
     * fixo no codigo quebraria na primeira moeda nao centesimal.
     */
    @Column(name = "escala_padrao", nullable = false)
    private Short escalaPadrao;

    protected Moeda() {
        // exigido pelo JPA
    }

    public Moeda(String codigo, String nome, short escalaPadrao) {
        this.codigo = codigo;
        this.nome = nome;
        this.escalaPadrao = escalaPadrao;
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

    public Short getEscalaPadrao() {
        return escalaPadrao;
    }
}
