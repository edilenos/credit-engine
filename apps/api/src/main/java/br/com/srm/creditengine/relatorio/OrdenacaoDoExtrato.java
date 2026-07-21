package br.com.srm.creditengine.relatorio;

import java.util.Arrays;
import java.util.Locale;

/**
 * Colunas pelas quais o extrato pode ser ordenado.
 *
 * <h2>Por que uma lista branca, e nao um parametro</h2>
 *
 * Nome de coluna e nome de tabela sao <b>identificadores</b>, e identificador
 * nao pode ser vinculado como parametro: {@code ORDER BY ?} nao existe em SQL.
 * Sobra concatenar — e concatenar entrada de usuario em SQL e' exatamente a
 * porta de injecao que o resto da consulta fecha usando parametros nomeados.
 *
 * <p>O enum resolve porque o valor que chega da URL nunca vira SQL: ele so
 * <i>seleciona</i> uma constante escrita aqui. Valor desconhecido nao vira
 * fallback silencioso — vira erro, porque ordenar por outra coisa sem avisar
 * devolve uma pagina que o cliente vai interpretar errado.
 */
public enum OrdenacaoDoExtrato {

    LIQUIDADO_EM("l.liquidado_em"),
    VALOR_LIQUIDADO("l.valor_liquidado"),
    CEDENTE("c.razao_social"),
    OPERACAO("o.id");

    /** Trecho fixo, escrito no codigo. Nunca montado a partir de entrada. */
    private final String coluna;

    OrdenacaoDoExtrato(String coluna) {
        this.coluna = coluna;
    }

    String coluna() {
        return coluna;
    }

    public static OrdenacaoDoExtrato de(String valor) {
        if (valor == null || valor.isBlank()) {
            return LIQUIDADO_EM;
        }
        return Arrays.stream(values())
                .filter(op -> op.name().equalsIgnoreCase(valor.trim()))
                .findFirst()
                .orElseThrow(() -> new OrdenacaoInvalidaException(valor));
    }

    /** Direcao tambem e' identificador: mesmo tratamento. */
    public enum Direcao {
        ASC, DESC;

        public static Direcao de(String valor) {
            if (valor == null || valor.isBlank()) {
                return DESC;
            }
            try {
                return valueOf(valor.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException naoExiste) {
                throw new OrdenacaoInvalidaException(valor);
            }
        }
    }
}
