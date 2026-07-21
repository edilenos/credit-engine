package br.com.srm.creditengine.relatorio;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Extrato de liquidacao (PBI-35).
 *
 * <h2>Duas camadas, de proposito</h2>
 *
 * Este controller chama {@link ConsultaDeExtrato} <b>direto</b>, sem passar por
 * servico de negocio. E' a excecao que o paragrafo 3.6 do enunciado autoriza, e
 * ela se justifica aqui porque nao ha regra a aplicar: o extrato le, projeta e
 * pagina. Um servico no meio seria repasse — a camada existiria no diagrama e
 * nao no comportamento.
 *
 * <p>A excecao vale para <b>relatorio</b>. Toda rota que altera estado continua
 * atravessando as tres camadas, e a separacao fisica deste pacote existe para
 * que a diferenca seja visivel sem ler o codigo inteiro.
 */
@RestController
@RequestMapping("/api/v1/relatorios/extrato-liquidacao")
@Tag(name = "Relatorios", description = "Consultas analiticas em SQL nativo")
public class ExtratoDeLiquidacaoController {

    private final ConsultaDeExtrato consulta;

    public ExtratoDeLiquidacaoController(ConsultaDeExtrato consulta) {
        this.consulta = consulta;
    }

    /**
     * Lista liquidacoes com filtros combinaveis e paginacao no servidor.
     *
     * <p>Nao ha variante sem paginacao: o tamanho e' limitado no
     * {@link FiltroDoExtrato}, entao {@code tamanho=1000000} devolve 100 e nao
     * a tabela inteira.
     */
    @Operation(
            summary = "Extrato de liquidacoes",
            description = """
                    Consulta analitica sobre liquidacoes, com filtros de periodo, \
                    cedente e moeda — todos opcionais e combinaveis entre si.

                    Paginada no servidor: a resposta traz `totalDeItens`, que e o \
                    total para o filtro e nao o tamanho da pagina. Nao existe forma \
                    de obter a colecao inteira; `tamanho` e limitado a 100.

                    `ordenarPor` aceita apenas `LIQUIDADO_EM`, `VALOR_LIQUIDADO`, \
                    `CEDENTE` ou `OPERACAO` — lista branca, porque nome de coluna \
                    nao pode ser parametro de SQL. Valor fora da lista responde 422 \
                    em vez de cair num padrao silencioso.""")
    @GetMapping
    public PaginaDoExtrato consultar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,

            @RequestParam(required = false) String documentoCedente,
            @RequestParam(required = false) String moedaLiquidacao,
            @RequestParam(required = false) String ordenarPor,
            @RequestParam(required = false) String direcao,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {

        return consulta.consultar(new FiltroDoExtrato(
                de, ate, documentoCedente, moedaLiquidacao,
                OrdenacaoDoExtrato.de(ordenarPor),
                OrdenacaoDoExtrato.Direcao.de(direcao),
                pagina, tamanho));
    }
}
