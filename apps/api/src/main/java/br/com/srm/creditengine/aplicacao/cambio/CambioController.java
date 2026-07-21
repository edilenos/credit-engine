package br.com.srm.creditengine.aplicacao.cambio;

import java.net.URI;
import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.srm.creditengine.negocio.cambio.ServicoDeCambio;
import br.com.srm.creditengine.negocio.cambio.ServicoDeSincronizacaoDeCambio;
import br.com.srm.creditengine.persistencia.entidade.TaxaCambio;
import jakarta.validation.Valid;

/**
 * Endpoints de cotacao de cambio — camada de aplicacao.
 *
 * <p>So orquestra: converte DTO em chamada de servico e resultado em resposta
 * HTTP. Nenhuma regra de negocio mora aqui, e nenhum {@code try/catch} —
 * excecoes de dominio viram status pelo tratador global.
 *
 * <p>Atende ao paragrafo 3.1 do enunciado (endpoint de atualizacao manual de
 * taxas) com os verbos e status semanticos que o 3.4 cobra.
 */
@RestController
@RequestMapping("/api/v1/cambio/taxas")
@Tag(name = "Cambio", description = "Cotacoes com historico e vigencia")
public class CambioController {

    private final ServicoDeCambio cambio;
    private final ServicoDeSincronizacaoDeCambio sincronizacao;

    public CambioController(ServicoDeCambio cambio, ServicoDeSincronizacaoDeCambio sincronizacao) {
        this.cambio = cambio;
        this.sincronizacao = sincronizacao;
    }

    /**
     * Registra uma cotacao. Nao atualiza: acrescenta.
     *
     * <p>{@code 201} com {@code Location} apontando para o recurso criado, que
     * e' consultavel de fato em {@code GET /api/v1/cambio/taxas/{id}} — Location
     * para endpoint inexistente e' contrato quebrado.
     */
    @PostMapping
    public ResponseEntity<CotacaoResponse> registrar(@Valid @RequestBody RegistrarCotacaoRequest requisicao,
                                                     UriComponentsBuilder uriBuilder) {
        TaxaCambio registrada = cambio.registrar(
                requisicao.moedaOrigem(),
                requisicao.moedaDestino(),
                requisicao.cotacao(),
                requisicao.vigenciaOuAgora(),
                requisicao.fonteOuManual());

        URI local = uriBuilder.path("/api/v1/cambio/taxas/{id}")
                .buildAndExpand(registrada.getId())
                .toUri();

        return ResponseEntity.created(local).body(CotacaoResponse.de(registrada));
    }

    /**
     * Cotacao vigente de um par numa data.
     *
     * <p>Sem o parametro {@code data}, assume agora. Par sem cotacao vigente
     * devolve {@code 404}: o recurso pedido nao existe naquele instante.
     */
    @GetMapping
    public CotacaoResponse vigente(
            @RequestParam String origem,
            @RequestParam String destino,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime data) {

        OffsetDateTime momento = data != null ? data : OffsetDateTime.now();
        return CotacaoResponse.de(cambio.cotacaoVigente(origem, destino, momento));
    }

    /**
     * Historico de cotacoes de um par, sempre paginado.
     *
     * <p>O tamanho maximo de pagina esta limitado na configuracao: sem teto, o
     * cliente contornaria a paginacao pedindo {@code size=1000000}.
     */
    @GetMapping("/historico")
    public Page<CotacaoResponse> historico(
            @RequestParam String origem,
            @RequestParam String destino,
            @PageableDefault(size = 20, sort = "vigenciaInicio", direction = Sort.Direction.DESC)
            Pageable pagina) {

        return cambio.historico(origem, destino, pagina).map(CotacaoResponse::de);
    }

    /** Cotacao por identificador. Existe para o {@code Location} do POST ser real. */
    @GetMapping("/{id}")
    public CotacaoResponse porId(@PathVariable Long id) {
        return CotacaoResponse.de(cambio.porId(id));
    }

    /**
     * Sincroniza a cotacao do par com o provedor externo (mockado).
     *
     * <p>Responde {@code 200} mesmo quando o provedor falha: nesse caso o corpo
     * traz {@code degradado: true} e a ultima cotacao conhecida. Devolver 5xx
     * seria enganoso — a requisicao foi atendida, so que com dado do historico.
     *
     * <p>Se nao houver nenhuma cotacao anterior para o par, ai sim ha falha de
     * verdade e a resposta e' {@code 404}: nao existe o que degradar.
     */
    @PostMapping("/sincronizacao")
    public SincronizacaoResponse sincronizar(@RequestParam String origem,
                                             @RequestParam String destino) {
        return SincronizacaoResponse.de(sincronizacao.sincronizar(origem, destino));
    }
}
