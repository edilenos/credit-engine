package br.com.srm.creditengine.persistencia.entidade;

import org.hibernate.Hibernate;

/**
 * Base de identidade das entidades.
 *
 * <p>Nao e' {@code @MappedSuperclass}: nao declara estado persistente, so o
 * contrato de igualdade. As estrategias de geracao de id diferem entre as
 * entidades (IDENTITY para escrita unitaria, SEQUENCE para escrita em lote),
 * entao o campo fica em cada uma.
 *
 * <p>A igualdade usa o id e trata o caso transiente: instancia ainda sem id
 * nunca e' igual a outra, mesmo que os campos coincidam. O {@code hashCode}
 * e' constante por classe de proposito — id atribuido apos a insercao mudaria
 * o hash de um objeto ja dentro de um {@code HashSet}, que e' o bug classico
 * de entidade JPA em colecao.
 */
public abstract class EntidadeBase {

    public abstract Long getId();

    /**
     * Igualdade por id, segura para proxy.
     *
     * <p>Usa {@link Hibernate#getClass(Object)} e nao {@code getClass()}: numa
     * associacao LAZY ainda nao inicializada, {@code getClass()} devolve a
     * classe gerada pelo Hibernate, nao a da entidade. Comparar com
     * {@code getClass()} faz duas referencias a mesma linha parecerem
     * diferentes — foi exatamente assim que {@code Operacao.isCrossCurrency()}
     * passou a mentir para operacao em moeda unica.
     *
     * <p>Instancia ainda sem id nunca e igual a outra, mesmo que os campos
     * coincidam: sem identidade persistida, nao ha o que comparar.
     */
    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof EntidadeBase entidade)) {
            return false;
        }
        if (!Hibernate.getClass(this).equals(Hibernate.getClass(entidade))) {
            return false;
        }
        return getId() != null && getId().equals(entidade.getId());
    }

    /**
     * Constante por classe, de proposito: o id so existe apos a insercao, e um
     * hash que mudasse nesse momento tornaria inalcancavel um objeto ja dentro
     * de um {@code HashSet}. E o bug classico de entidade JPA em colecao.
     */
    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
