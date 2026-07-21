package br.com.srm.creditengine.aplicacao.cadastro;

import java.math.BigDecimal;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.Periodicidade;
import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;

/**
 * Produto na API.
 *
 * <p>Expoe o spread cadastrado para o painel poder mostrar a taxa antes de
 * simular. Nao e' o spread final: a Strategy do produto pode ajusta-lo pelo
 * contexto, e quem manda e' o {@code spreadAplicado} que volta da simulacao.
 *
 * @param codigo        chave que resolve a Strategy
 * @param nome          rotulo para exibicao
 * @param spread        premio de risco cadastrado, na periodicidade abaixo
 * @param periodicidade unidade do spread
 * @param convencaoContagem convencao usada para normalizar o prazo
 */
public record TipoRecebivelResponse(
        String codigo,
        String nome,
        BigDecimal spread,
        Periodicidade periodicidade,
        ConvencaoContagem convencaoContagem) {

    public static TipoRecebivelResponse de(TipoRecebivel tipo) {
        return new TipoRecebivelResponse(
                tipo.getCodigo(),
                tipo.getNome(),
                tipo.getSpread(),
                tipo.getPeriodicidade(),
                tipo.getConvencaoContagem());
    }
}
