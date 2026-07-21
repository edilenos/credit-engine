package br.com.srm.creditengine.aplicacao.operacao;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.srm.creditengine.negocio.cessao.ServicoDeCessao;
import br.com.srm.creditengine.negocio.liquidacao.ResultadoDaLiquidacao;
import br.com.srm.creditengine.negocio.liquidacao.ServicoDeLiquidacao;
import br.com.srm.creditengine.persistencia.entidade.Operacao;
import jakarta.validation.Valid;

/**
 * Operacoes de cessao — camada de aplicacao.
 *
 * <p>So orquestra: DTO vira solicitacao de dominio, entidade vira resposta. A
 * transacao, a precificacao e o rollback do lote sao do
 * {@link ServicoDeCessao}; nao ha regra nem {@code try/catch} aqui.
 */
@RestController
@RequestMapping("/api/v1/operacoes")
@Tag(name = "Operacoes", description = "Cessao de credito e liquidacao")
public class OperacaoController {

    private final ServicoDeCessao cessao;
    private final ServicoDeLiquidacao liquidacao;

    public OperacaoController(ServicoDeCessao cessao, ServicoDeLiquidacao liquidacao) {
        this.cessao = cessao;
        this.liquidacao = liquidacao;
    }

    /**
     * Registra a cessao de um lote.
     *
     * <p>{@code 201} com {@code Location} apontando para um recurso que existe
     * de fato — o {@code GET} abaixo. Location para endpoint inexistente e'
     * contrato quebrado.
     */
    @Operation(summary = "Registra a cessao de um lote",
            description = """
                    Precifica o lote, congela os parametros aplicados e cria a operacao \
                    como PENDENTE. Tudo em uma transacao: ou o lote inteiro entra, ou nada \
                    entra — um titulo invalido derruba os demais.""")
    @ApiResponse(responseCode = "201",
            description = "Operacao criada. O Location aponta para o GET correspondente.")
    @PostMapping
    public ResponseEntity<OperacaoResponse> registrar(
            @Valid @RequestBody RegistrarOperacaoRequest requisicao,
            UriComponentsBuilder uriBuilder) {

        Operacao registrada = cessao.registrar(requisicao.paraDominio());

        URI local = uriBuilder.path("/api/v1/operacoes/{id}")
                .buildAndExpand(registrada.getId())
                .toUri();

        return ResponseEntity.created(local).body(OperacaoResponse.de(registrada));
    }

    @GetMapping("/{id}")
    public OperacaoResponse porId(@PathVariable Long id) {
        return OperacaoResponse.de(cessao.porId(id));
    }

    /**
     * Liquida a operacao.
     *
     * <p>{@code 201} quando a liquidacao acontece agora; {@code 200} quando a
     * chave ja tinha sido usada nesta operacao e o corpo e' o comprovante
     * original. Os dois devolvem o mesmo conteudo — e' o que idempotencia
     * significa — e o status distingue o que de fato ocorreu, sem obrigar o
     * cliente a inspecionar o corpo para descobrir.
     *
     * <p>Conflito de estado, colisao de versao e violacao de UNIQUE viram
     * {@code 409} no tratador global. Nenhum deles e' {@code 500}: liquidacao
     * concorrente e' cenario previsto, nao defeito.
     */
    @Operation(summary = "Liquida a operacao",
            description = """
                    Marca a operacao como LIQUIDADA e grava o comprovante, em uma \n                    transacao. Idempotente: repetir a chave devolve o comprovante \n                    original com 200 em vez de 201, sem liquidar de novo.

                    Chave ja usada em OUTRA operacao e colisao, nao repeticao, e \n                    responde 409.""")
    @ApiResponse(responseCode = "201", description = "Liquidacao efetuada agora.")
    @ApiResponse(responseCode = "200",
            description = "A chave ja havia sido usada nesta operacao; corpo e o comprovante original.")
    @PostMapping("/{id}/liquidacao")
    public ResponseEntity<LiquidacaoResponse> liquidar(
            @PathVariable Long id,
            @Valid @RequestBody LiquidarRequest requisicao,
            UriComponentsBuilder uriBuilder) {

        ResultadoDaLiquidacao resultado = liquidacao.liquidar(
                id, requisicao.chaveIdempotencia(), requisicao.liquidadoPor());

        LiquidacaoResponse corpo = LiquidacaoResponse.de(resultado.liquidacao());

        if (resultado.replay()) {
            return ResponseEntity.ok(corpo);
        }

        URI local = uriBuilder.path("/api/v1/operacoes/{id}/liquidacao")
                .buildAndExpand(id)
                .toUri();

        return ResponseEntity.created(local).body(corpo);
    }

    @GetMapping("/{id}/liquidacao")
    public LiquidacaoResponse liquidacaoDa(@PathVariable Long id) {
        return LiquidacaoResponse.de(liquidacao.porOperacao(id));
    }
}
