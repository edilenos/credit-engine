package br.com.srm.creditengine.negocio.precificacao;

import java.time.LocalDate;

import br.com.srm.creditengine.negocio.ExcecaoDeNegocio;

/** Titulo com vencimento anterior a data da operacao nao tem o que antecipar. */
public class VencimentoNoPassadoException extends ExcecaoDeNegocio {

    public VencimentoNoPassadoException(LocalDate dataOperacao, LocalDate dataVencimento) {
        super("Vencimento %s e anterior a data da operacao %s".formatted(dataVencimento, dataOperacao));
    }
}
