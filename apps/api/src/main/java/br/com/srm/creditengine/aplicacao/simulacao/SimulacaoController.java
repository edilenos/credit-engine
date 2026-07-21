package br.com.srm.creditengine.aplicacao.simulacao;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.srm.creditengine.negocio.precificacao.PrecificacaoDaOperacao;
import br.com.srm.creditengine.negocio.precificacao.ServicoDeSimulacao;
import br.com.srm.creditengine.negocio.precificacao.SolicitacaoDeSimulacao;
import jakarta.validation.Valid;

/**
 * Simulacao de precificacao — camada de aplicacao.
 *
 * <p>Atende ao §3.4 do enunciado. So orquestra: DTO vira solicitacao de
 * dominio, resultado vira resposta HTTP. Sem regra e sem {@code try/catch} —
 * excecao de dominio vira status no tratador global.
 */
@RestController
@RequestMapping("/api/v1/simulacoes")
public class SimulacaoController {

    private final ServicoDeSimulacao simulacao;

    public SimulacaoController(ServicoDeSimulacao simulacao) {
        this.simulacao = simulacao;
    }

    /**
     * Precifica um lote sem criar nada.
     *
     * <p>{@code POST} e nao {@code GET} porque a entrada e' um lote estruturado,
     * nao uma chave de consulta: nao cabe em query string com qualquer higiene, e
     * lote grande estouraria o limite de URL. Ainda assim responde {@code 200},
     * nao {@code 201} — nenhum recurso passa a existir, entao nao ha
     * {@code Location} a devolver.
     */
    @PostMapping
    public SimulacaoResponse simular(@Valid @RequestBody SimularRequest requisicao) {
        SolicitacaoDeSimulacao solicitacao = requisicao.paraDominio();
        PrecificacaoDaOperacao precificacao = simulacao.simular(solicitacao);

        return SimulacaoResponse.de(precificacao, solicitacao.moedaTitulo(),
                solicitacao.moedaLiquidacao(), solicitacao.dataOperacao());
    }
}
