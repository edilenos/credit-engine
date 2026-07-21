package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;

/**
 * Seleciona a regra de risco do produto e a executa.
 *
 * <p>Recebe a lista de estrategias por injecao. <b>Nao ha {@code if} nem
 * {@code switch} por tipo aqui</b>: cada regra declara o que suporta, e a
 * selecao e' polimorfica. E' o que faz a extensao funcionar — registrar um
 * produto novo e' criar uma classe anotada com {@code @Component}, e nada
 * neste arquivo nem no motor de calculo precisa mudar.
 *
 * <p>Um {@code switch} aqui teria o efeito oposto: cada produto novo exigiria
 * editar este arquivo, e o padrao Strategy viraria uma indirecao sem ganho.
 */
@Component
public class ResolvedorDeSpread {

    private final List<EstrategiaDeSpread> estrategias;

    public ResolvedorDeSpread(List<EstrategiaDeSpread> estrategias) {
        this.estrategias = List.copyOf(estrategias);
    }

    /**
     * Premio de risco do produto no contexto informado.
     *
     * @throws EstrategiaDeSpreadNaoEncontradaException se nenhuma regra responder
     *         pelo tipo — nunca devolve zero silencioso
     */
    public BigDecimal spreadPara(ContextoDePrecificacao contexto) {
        return estrategiaPara(contexto.tipo()).calcular(contexto);
    }

    private EstrategiaDeSpread estrategiaPara(TipoRecebivel tipo) {
        return estrategias.stream()
                .filter(estrategia -> estrategia.suporta(tipo))
                .findFirst()
                .orElseThrow(() -> new EstrategiaDeSpreadNaoEncontradaException(tipo.getCodigo()));
    }

    /** Quantas regras estao registradas. Existe para o teste de extensibilidade. */
    public int quantidadeDeEstrategias() {
        return estrategias.size();
    }
}
