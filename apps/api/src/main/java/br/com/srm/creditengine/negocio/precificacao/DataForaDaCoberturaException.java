package br.com.srm.creditengine.negocio.precificacao;

import java.time.LocalDate;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/**
 * O intervalo pedido sai da faixa que o calendario de feriados conhece.
 *
 * <p>Falhar aqui e' deliberado. Sem esta checagem, um vencimento em 2031
 * contaria os feriados de 2031 como dias uteis — o expoente sairia maior, o
 * fator de desconto maior, e o fundo pagaria menos do que devia pelo titulo.
 * Erro silencioso e a favor de um dos lados.
 */
public class DataForaDaCoberturaException extends ExcecaoDeNegocio {

    public DataForaDaCoberturaException(LocalDate pedido, LocalDate inicio, LocalDate fim) {
        super(("Data %s esta fora da cobertura do calendario de feriados (%s a %s). "
                + "Estenda o calendario antes de operar neste horizonte.")
                .formatted(pedido, inicio, fim));
    }
}
