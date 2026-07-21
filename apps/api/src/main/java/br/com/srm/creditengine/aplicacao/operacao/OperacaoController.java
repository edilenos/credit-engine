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

import br.com.srm.creditengine.negocio.cessao.ServicoDeCessao;
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
public class OperacaoController {

    private final ServicoDeCessao cessao;

    public OperacaoController(ServicoDeCessao cessao) {
        this.cessao = cessao;
    }

    /**
     * Registra a cessao de um lote.
     *
     * <p>{@code 201} com {@code Location} apontando para um recurso que existe
     * de fato — o {@code GET} abaixo. Location para endpoint inexistente e'
     * contrato quebrado.
     */
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
}
