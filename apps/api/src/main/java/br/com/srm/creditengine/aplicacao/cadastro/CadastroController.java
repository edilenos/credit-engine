package br.com.srm.creditengine.aplicacao.cadastro;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.srm.creditengine.negocio.cadastro.ServicoDeCadastros;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Dados de referencia — camada de aplicacao.
 *
 * <p>Alimenta os seletores do painel do operador (PBI-26). Colecoes pequenas e
 * limitadas por natureza: sao os produtos e as moedas que o fundo opera, nao
 * dados transacionais. Por isso nao sao paginadas — a regra de "nenhuma colecao
 * ilimitada" existe para o que cresce com o uso.
 */
@RestController
@RequestMapping("/api/v1/cadastros")
@Tag(name = "Cadastros", description = "Dados de referencia para os formularios")
public class CadastroController {

    private final ServicoDeCadastros cadastros;

    public CadastroController(ServicoDeCadastros cadastros) {
        this.cadastros = cadastros;
    }

    /** Produtos disponiveis para nova operacao. Desativados nao aparecem. */
    @GetMapping("/tipos-recebivel")
    public List<TipoRecebivelResponse> tiposDeRecebivel() {
        return cadastros.tiposAtivos().stream().map(TipoRecebivelResponse::de).toList();
    }

    @GetMapping("/moedas")
    public List<MoedaResponse> moedas() {
        return cadastros.moedas().stream().map(MoedaResponse::de).toList();
    }
}
