package br.com.srm.creditengine.persistencia.entidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Empresa que cede o recebivel ao fundo em troca de liquidez imediata. */
@Entity
@Table(name = "cedente")
public class Cedente extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CNPJ somente digitos. Formatacao e' da camada de apresentacao. */
    @Column(name = "documento", nullable = false, length = 14)
    private String documento;

    @Column(name = "razao_social", nullable = false, length = 200)
    private String razaoSocial;

    /** Cedente inativo nao origina operacao nova; as antigas permanecem. */
    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected Cedente() {
        // exigido pelo JPA
    }

    public Cedente(String documento, String razaoSocial) {
        this.documento = documento;
        this.razaoSocial = razaoSocial;
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getDocumento() {
        return documento;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void inativar() {
        this.ativo = false;
    }
}
