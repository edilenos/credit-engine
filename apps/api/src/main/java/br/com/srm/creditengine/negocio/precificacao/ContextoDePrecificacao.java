package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;

/**
 * Tudo que uma regra de risco pode precisar para decidir o spread.
 *
 * <p>O contexto e' rico de proposito. Uma interface do tipo
 * {@code BigDecimal spread(TipoRecebivel)} seria um {@code Map} disfarcado de
 * padrao — e o enunciado nao pede uma tabela: pede que "cada tipo de recebivel
 * possui uma <b>regra</b> de risco diferente", chamando os percentuais de
 * "Exemplo". Regra pode depender de prazo e de valor; constante nao.
 *
 * @param tipo          produto sendo precificado
 * @param valorFace     valor nominal do titulo
 * @param dataOperacao  data em que a cessao acontece
 * @param dataVencimento vencimento do titulo
 */
public record ContextoDePrecificacao(
        TipoRecebivel tipo,
        BigDecimal valorFace,
        LocalDate dataOperacao,
        LocalDate dataVencimento) {

    public ContextoDePrecificacao {
        if (dataVencimento.isBefore(dataOperacao)) {
            throw new VencimentoNoPassadoException(dataOperacao, dataVencimento);
        }
    }

    /**
     * Dias corridos entre a operacao e o vencimento.
     *
     * <p>Dado bruto, sem convencao aplicada: quem normaliza para o expoente da
     * formula e' a convencao de contagem (PBI-19). Uma regra de risco pode
     * querer o prazo cru — "cheque acima de N dias nao e' aceito" e' uma regra
     * sobre dias corridos, nao sobre expoente.
     */
    public long prazoEmDiasCorridos() {
        return ChronoUnit.DAYS.between(dataOperacao, dataVencimento);
    }
}
