package br.com.srm.creditengine.negocio.precificacao.spread;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.srm.creditengine.negocio.precificacao.ContextoDePrecificacao;
import br.com.srm.creditengine.negocio.precificacao.EstrategiaDeSpread;
import br.com.srm.creditengine.negocio.precificacao.PrazoInviavelException;
import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;

/**
 * Regra de risco do cheque pre-datado.
 *
 * <p>Difere da duplicata em natureza, nao so em percentual: cheque e' ordem de
 * pagamento, e o risco principal e' de devolucao — sem fundos, sustado,
 * prescrito. Esse risco tem <b>prazo maximo</b>, coisa que credito de sacado
 * nao tem.
 *
 * <p>A Lei 7.357/85 fixa 30 dias para apresentacao na mesma praca e 60 fora
 * dela, com prescricao 6 meses apos esse limite. Passado esse horizonte o
 * cheque deixa de ser instrumento executavel, e antecipar contra ele nao e'
 * operacao de credito: e' aposta. Por isso a regra <b>recusa</b> em vez de
 * cobrar um premio maior — spread punitivo esconderia a decisao dentro de um
 * numero.
 *
 * <p>O limite e' parametro de politica, nao constante de codigo: a mesa pode
 * ajusta-lo por {@code precificacao.cheque.prazo-maximo-dias}.
 */
@Component
public class SpreadDeChequePreDatado implements EstrategiaDeSpread {

    public static final String CODIGO = "CHEQUE_PRE_DATADO";

    private final long prazoMaximoEmDias;

    public SpreadDeChequePreDatado(
            @Value("${precificacao.cheque.prazo-maximo-dias:180}") long prazoMaximoEmDias) {
        this.prazoMaximoEmDias = prazoMaximoEmDias;
    }

    @Override
    public boolean suporta(TipoRecebivel tipo) {
        return CODIGO.equals(tipo.getCodigo());
    }

    @Override
    public BigDecimal calcular(ContextoDePrecificacao contexto) {
        long prazo = contexto.prazoEmDiasCorridos();
        if (prazo > prazoMaximoEmDias) {
            throw new PrazoInviavelException(CODIGO, prazo, prazoMaximoEmDias);
        }
        return contexto.tipo().getSpread();
    }
}
