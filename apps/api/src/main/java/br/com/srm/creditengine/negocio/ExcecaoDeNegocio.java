package br.com.srm.creditengine.negocio;

/**
 * Base das falhas de regra de negocio.
 *
 * <p>Distingue "o pedido nao pode ser atendido pelas regras do dominio" de
 * "algo quebrou": a primeira e' resposta legitima da aplicacao e vira 4xx; a
 * segunda e' defeito e vira 500. Sem essa separacao, o tratamento global de
 * excecoes (PBI-31) nao teria como decidir o status sem inspecionar mensagem.
 *
 * <p>Estende {@code RuntimeException} de proposito: regra de negocio violada
 * nao e' condicao recuperavel pelo chamador imediato, e checked exception aqui
 * so poluiria assinaturas ate a borda da aplicacao.
 */
public abstract class ExcecaoDeNegocio extends RuntimeException {

    protected ExcecaoDeNegocio(String mensagem) {
        super(mensagem);
    }
}
