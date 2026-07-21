package br.com.srm.creditengine.negocio.precificacao;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;
import br.com.srm.creditengine.persistencia.repositorio.MoedaRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.TipoRecebivelRepositorio;

/**
 * Precifica um lote sem gravar nada (PBI-24).
 *
 * <p>E' o mesmo caminho de calculo da cessao real — {@link PrecificadorDeOperacao}
 * — sem a escrita. Reimplementar a conta aqui criaria duas verdades: a simulacao
 * mostraria um numero e a efetivacao contrataria outro, que e' exatamente o que
 * o operador usa este endpoint para evitar.
 *
 * <h2>Nao persiste</h2>
 *
 * {@code readOnly = true} nao e' so dica de performance: coloca a sessao do
 * Hibernate em flush mode manual, entao mesmo uma escrita acidental nao chega ao
 * banco. O criterio de aceite ("nenhum registro e' persistido, incluindo
 * auditoria") fica garantido pela infraestrutura, nao pela disciplina de quem
 * mexer no codigo depois.
 *
 * <h2>Valida os cadastros antes de calcular</h2>
 *
 * Moeda e tipo sao conferidos na entrada. Sem isso, uma simulacao em moeda
 * unica com codigo inexistente passaria batido — o cambio so e' consultado
 * quando as moedas diferem — e o operador so descobriria o erro ao tentar
 * efetivar. Simulacao que nao antecipa a recusa nao serve para o que existe.
 */
@Service
public class ServicoDeSimulacao {

    private final PrecificadorDeOperacao precificador;
    private final TipoRecebivelRepositorio tipos;
    private final MoedaRepositorio moedas;

    public ServicoDeSimulacao(PrecificadorDeOperacao precificador,
                              TipoRecebivelRepositorio tipos,
                              MoedaRepositorio moedas) {
        this.precificador = precificador;
        this.tipos = tipos;
        this.moedas = moedas;
    }

    /**
     * @throws ReferenciaDesconhecidaException se algum tipo ou moeda nao existir
     * @throws LoteVazioException se o lote vier sem titulos
     * @throws VencimentoNoPassadoException se algum vencimento anteceder a operacao
     */
    @Transactional(readOnly = true)
    public PrecificacaoDaOperacao simular(SolicitacaoDeSimulacao solicitacao) {
        exigirMoeda(solicitacao.moedaTitulo());
        exigirMoeda(solicitacao.moedaLiquidacao());

        Map<String, TipoRecebivel> cadastrados = carregarTipos(solicitacao);

        List<ContextoDePrecificacao> contextos = solicitacao.titulos().stream()
                .map(titulo -> new ContextoDePrecificacao(
                        cadastrados.get(titulo.codigoTipoRecebivel()),
                        titulo.valorFace(),
                        solicitacao.dataOperacao(),
                        titulo.dataVencimento()))
                .toList();

        return precificador.precificar(contextos, solicitacao.moedaTitulo(),
                solicitacao.moedaLiquidacao(), solicitacao.dataOperacao());
    }

    /**
     * Carrega os tipos distintos do lote de uma vez.
     *
     * <p>Um lote de 500 titulos costuma usar dois ou tres produtos; buscar por
     * item faria 500 consultas para ler tres linhas.
     */
    private Map<String, TipoRecebivel> carregarTipos(SolicitacaoDeSimulacao solicitacao) {
        List<String> codigos = solicitacao.titulos().stream()
                .map(SolicitacaoDeSimulacao.TituloASimular::codigoTipoRecebivel)
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

    private void exigirMoeda(String codigo) {
        if (moedas.findByCodigo(codigo).isEmpty()) {
            throw ReferenciaDesconhecidaException.moeda(codigo);
        }
    }
}
