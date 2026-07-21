package br.com.srm.creditengine.negocio.precificacao.spread;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import br.com.srm.creditengine.negocio.precificacao.ContextoDePrecificacao;
import br.com.srm.creditengine.negocio.precificacao.EstrategiaDeSpread;
import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;

/**
 * Regra de risco da duplicata mercantil.
 *
 * <p>Titulo lastreado em venda mercantil, com sacado identificado e obrigacao
 * comercial por tras. O risco e' o credito do sacado, que nao muda de natureza
 * conforme o prazo — por isso o premio e' o parametro do produto, sem ajuste.
 *
 * <p>Tambem nao ha limite de prazo: duplicata com vencimento longo continua
 * sendo duplicata. O contraste com {@link SpreadDeChequePreDatado} e' o ponto:
 * as duas regras sao diferentes de verdade, nao dois retornos da mesma tabela.
 */
@Component
public class SpreadDeDuplicataMercantil implements EstrategiaDeSpread {

    public static final String CODIGO = "DUPLICATA_MERCANTIL";

    @Override
    public boolean suporta(TipoRecebivel tipo) {
        return CODIGO.equals(tipo.getCodigo());
    }

    @Override
    public BigDecimal calcular(ContextoDePrecificacao contexto) {
        // O valor mora no banco para permitir ajuste sem deploy; a regra de que
        // ele se aplica sem ajuste mora aqui.
        return contexto.tipo().getSpread();
    }
}
