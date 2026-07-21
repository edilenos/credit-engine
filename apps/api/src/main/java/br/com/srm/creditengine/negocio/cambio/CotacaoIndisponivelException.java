package br.com.srm.creditengine.negocio.cambio;

import java.time.OffsetDateTime;

import br.com.srm.creditengine.negocio.RecursoNaoEncontradoException;

/**
 * Nao ha cotacao vigente para o par de moedas na data pedida.
 *
 * <p>E' erro de negocio, nao ausencia de dado: o chamador pediu uma conversao
 * que o sistema nao tem como precificar. Devolver {@code null} ou zero faria a
 * falha viajar silenciosa ate virar valor errado em operacao — que e' o modo de
 * falha que este projeto trata como o mais perigoso.
 *
 * <p>Tambem e' o que acontece quando se pede o sentido inverso de um par
 * cotado: o sistema nao deriva cotacao por 1/x. Ver
 * {@link ServicoDeCambio} para o porque.
 */
public class CotacaoIndisponivelException extends RecursoNaoEncontradoException {

    private final String moedaOrigem;
    private final String moedaDestino;
    private final OffsetDateTime momento;

    public CotacaoIndisponivelException(String moedaOrigem, String moedaDestino, OffsetDateTime momento) {
        super("Nao ha cotacao vigente de %s para %s em %s".formatted(moedaOrigem, moedaDestino, momento));
        this.moedaOrigem = moedaOrigem;
        this.moedaDestino = moedaDestino;
        this.momento = momento;
    }

    public String getMoedaOrigem() {
        return moedaOrigem;
    }

    public String getMoedaDestino() {
        return moedaDestino;
    }

    public OffsetDateTime getMomento() {
        return momento;
    }
}
