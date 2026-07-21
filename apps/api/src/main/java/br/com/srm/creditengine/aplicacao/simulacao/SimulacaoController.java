package br.com.srm.creditengine.aplicacao.simulacao;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Simulacao", description = "Precificacao sem efeito colateral")
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
    @Operation(
            summary = "Simula a precificacao de um lote",
            description = """
                    Precifica cada titulo e devolve o detalhamento item a item mais os \
                    totais. **Nada e gravado** — nem operacao, nem cotacao, nem trilha de \
                    auditoria.

                    Usa POST, e nao GET, porque a entrada e um lote estruturado: nao cabe \
                    em query string com qualquer higiene. Ainda assim responde 200 e nao \
                    201, porque nenhum recurso passa a existir.""")
    @ApiResponse(responseCode = "200",
            description = "Lote precificado. Nenhum recurso foi criado, portanto sem Location.")
    @PostMapping
    public SimulacaoResponse simular(@Valid @RequestBody SimularRequest requisicao) {
        SolicitacaoDeSimulacao solicitacao = requisicao.paraDominio();
        PrecificacaoDaOperacao precificacao = simulacao.simular(solicitacao);

        return SimulacaoResponse.de(precificacao, solicitacao.moedaTitulo(),
                solicitacao.moedaLiquidacao(), solicitacao.dataOperacao());
    }
}
