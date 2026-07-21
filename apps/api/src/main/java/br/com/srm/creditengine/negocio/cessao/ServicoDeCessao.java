package br.com.srm.creditengine.negocio.cessao;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.negocio.precificacao.ContextoDePrecificacao;
import br.com.srm.creditengine.negocio.precificacao.PrecificacaoDaOperacao;
import br.com.srm.creditengine.negocio.precificacao.PrecificacaoDoTitulo;
import br.com.srm.creditengine.negocio.precificacao.PrecificadorDeOperacao;
import br.com.srm.creditengine.negocio.precificacao.ReferenciaDesconhecidaException;
import br.com.srm.creditengine.persistencia.entidade.Cedente;
import br.com.srm.creditengine.persistencia.entidade.Moeda;
import br.com.srm.creditengine.persistencia.entidade.Operacao;
import br.com.srm.creditengine.persistencia.entidade.Recebivel;
import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;
import br.com.srm.creditengine.persistencia.repositorio.CedenteRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.MoedaRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.OperacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.TipoRecebivelRepositorio;

/**
 * Registro da operacao de cessao (PBI-27).
 *
 * <h2>Ou o lote inteiro entra, ou nada entra</h2>
 *
 * Um unico {@code @Transactional} cobre precificacao e gravacao. Um titulo com
 * tipo invalido, vencimento no passado ou prazo alem do limite da Strategy
 * derruba o lote inteiro — nao ha operacao meio registrada. Isso e' requisito
 * do paragrafo 3.3, nao preferencia: cessao parcial silenciosa significaria o
 * fundo desembolsando por uma carteira diferente da contratada.
 *
 * <p>Note que a atomicidade aqui e' <b>de graca</b>, e e' esse o argumento por
 * tras de recusar mensageria: sendo tudo um banco e uma transacao, o rollback e'
 * o comportamento padrao. Publicar cada titulo numa fila exigiria saga ou
 * dual-write para reconstruir a garantia que o {@code ROLLBACK} ja da.
 *
 * <h2>Precifica com o mesmo motor da simulacao</h2>
 *
 * {@link PrecificadorDeOperacao} e' o mesmo do PBI-24. Se a cessao recalculasse
 * por conta propria, o operador veria um numero na simulacao e contrataria
 * outro — e a divergencia apareceria como erro de centavo, o mais caro de
 * diagnosticar.
 */
@Service
public class ServicoDeCessao {

    private final PrecificadorDeOperacao precificador;
    private final OperacaoRepositorio operacoes;
    private final CedenteRepositorio cedentes;
    private final TipoRecebivelRepositorio tipos;
    private final MoedaRepositorio moedas;

    public ServicoDeCessao(PrecificadorDeOperacao precificador,
                           OperacaoRepositorio operacoes,
                           CedenteRepositorio cedentes,
                           TipoRecebivelRepositorio tipos,
                           MoedaRepositorio moedas) {
        this.precificador = precificador;
        this.operacoes = operacoes;
        this.cedentes = cedentes;
        this.tipos = tipos;
        this.moedas = moedas;
    }

    /**
     * Precifica o lote e registra a operacao como {@code PENDENTE}.
     *
     * @throws CedenteNaoEncontradoException  cedente inexistente
     * @throws ReferenciaDesconhecidaException tipo ou moeda inexistentes
     * @throws br.com.srm.creditengine.negocio.precificacao.LoteVazioException lote sem titulos
     */
    @Transactional
    public Operacao registrar(SolicitacaoDeCessao solicitacao) {
        Cedente cedente = cedentes.findByDocumento(solicitacao.documentoCedente())
                .orElseThrow(() -> new CedenteNaoEncontradoException(
                        solicitacao.documentoCedente()));

        Moeda moedaTitulo = exigirMoeda(solicitacao.moedaTitulo());
        Moeda moedaLiquidacao = exigirMoeda(solicitacao.moedaLiquidacao());
        Map<String, TipoRecebivel> cadastrados = carregarTipos(solicitacao);

        List<ContextoDePrecificacao> contextos = solicitacao.titulos().stream()
                .map(titulo -> new ContextoDePrecificacao(
                        cadastrados.get(titulo.codigoTipoRecebivel()),
                        titulo.valorFace(),
                        solicitacao.dataOperacao(),
                        titulo.dataVencimento()))
                .toList();

        PrecificacaoDaOperacao precificacao = precificador.precificar(
                contextos, solicitacao.moedaTitulo(), solicitacao.moedaLiquidacao(),
                solicitacao.dataOperacao());

        Operacao operacao = new Operacao(
                cedente, moedaTitulo, moedaLiquidacao,
                precificacao.cotacaoAplicada(),
                precificacao.valorFaceTotal(),
                precificacao.valorPresenteTotal(),
                precificacao.valorLiquidacao());

        // A correspondencia entre pedido e precificacao e' posicional: o
        // precificador preserva a ordem do lote. E' o mesmo contrato que o
        // campo `indice` da simulacao expoe ao cliente.
        List<PrecificacaoDoTitulo> precificados = precificacao.titulos();
        for (int i = 0; i < precificados.size(); i++) {
            operacao.adicionar(montarRecebivel(
                    solicitacao.titulos().get(i), precificados.get(i),
                    cadastrados));
        }

        return operacoes.save(operacao);
    }

    /**
     * Operacao com tudo que a resposta precisa, ja inicializado.
     *
     * <p>Devolver a entidade com associacoes LAZY nao resolvidas jogaria o
     * problema para o mapeamento, que roda fora da transacao.
     */
    @Transactional(readOnly = true)
    public Operacao porId(Long id) {
        return operacoes.findCompletaById(id)
                .orElseThrow(() -> new OperacaoNaoEncontradaException(id));
    }

    private Recebivel montarRecebivel(SolicitacaoDeCessao.TituloACeder pedido,
                                      PrecificacaoDoTitulo precificado,
                                      Map<String, TipoRecebivel> cadastrados) {
        return new Recebivel(
                cadastrados.get(pedido.codigoTipoRecebivel()),
                precificado.parametroAplicado(),
                pedido.numeroDocumento(),
                pedido.documentoSacado(),
                precificado.valorFace(),
                pedido.dataVencimento(),
                precificado.convencaoAplicada(),
                precificado.expoenteAplicado(),
                precificado.taxaBaseAplicada(),
                precificado.spreadAplicado(),
                precificado.valorPresente());
    }

    private Map<String, TipoRecebivel> carregarTipos(SolicitacaoDeCessao solicitacao) {
        List<String> codigos = solicitacao.titulos().stream()
                .map(SolicitacaoDeCessao.TituloACeder::codigoTipoRecebivel)
                .distinct()
                .toList();

        Map<String, TipoRecebivel> encontrados = tipos.findByCodigoIn(codigos).stream()
                .collect(Collectors.toMap(TipoRecebivel::getCodigo, Function.identity()));

        codigos.stream()
                .filter(codigo -> !encontrados.containsKey(codigo))
                .findFirst()
                .ifPresent(codigo -> {
                    throw ReferenciaDesconhecidaException.tipoRecebivel(codigo);
                });

        return encontrados;
    }

    private Moeda exigirMoeda(String codigo) {
        return moedas.findByCodigo(codigo)
                .orElseThrow(() -> ReferenciaDesconhecidaException.moeda(codigo));
    }
}
