package br.com.srm.creditengine.negocio.liquidacao;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.dominio.StatusOperacao;
import br.com.srm.creditengine.negocio.auditoria.ServicoDeAuditoria;
import br.com.srm.creditengine.negocio.cessao.OperacaoNaoEncontradaException;
import br.com.srm.creditengine.persistencia.entidade.Liquidacao;
import br.com.srm.creditengine.persistencia.entidade.Operacao;
import br.com.srm.creditengine.persistencia.repositorio.LiquidacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.OperacaoRepositorio;

/**
 * Liquidacao da operacao (PBI-28).
 *
 * <h2>Tres defesas contra liquidacao dupla, deliberadamente redundantes</h2>
 *
 * <ol>
 *   <li><b>Chave de idempotencia</b> — retry do cliente reencontra o registro e
 *       devolve o resultado original, sem liquidar de novo. Atua antes de
 *       qualquer escrita;
 *   <li><b>{@code @Version} em {@code Operacao}</b> — duas transacoes que leem a
 *       mesma operacao e tentam marca-la liquidada colidem: a segunda encontra
 *       {@code WHERE version = ?} sem linha e falha. E' o que protege a janela
 *       entre ler o status e gravar;
 *   <li><b>{@code UNIQUE (operacao_id)}</b> — o banco recusa a segunda insercao
 *       mesmo que as duas anteriores falhem.
 * </ol>
 *
 * A redundancia e' o ponto. A checagem de status sozinha e'
 * <i>time-of-check to time-of-use</i>: entre ler {@code PENDENTE} e gravar,
 * outra transacao pode ter liquidado. O lock otimista fecha essa janela, e a
 * constraint fecha o caso de o lock ser removido por engano num refactor
 * futuro.
 *
 * <h2>Onde os conflitos viram resposta</h2>
 *
 * {@code OptimisticLockingFailureException} e a violacao de UNIQUE acontecem no
 * <b>commit</b>, depois que este metodo ja retornou. Nao ha como captura-las
 * aqui — quem as traduz em 409 e' o tratador global.
 */
@Service
public class ServicoDeLiquidacao {

    private final OperacaoRepositorio operacoes;
    private final LiquidacaoRepositorio liquidacoes;
    private final ServicoDeAuditoria auditoria;

    public ServicoDeLiquidacao(OperacaoRepositorio operacoes,
                               LiquidacaoRepositorio liquidacoes,
                               ServicoDeAuditoria auditoria) {
        this.operacoes = operacoes;
        this.liquidacoes = liquidacoes;
        this.auditoria = auditoria;
    }

    /**
     * Liquida a operacao, ou devolve a liquidacao ja feita com esta chave.
     *
     * @throws OperacaoNaoEncontradaException operacao inexistente
     * @throws ConflitoDeEstadoException      operacao ja liquidada, cancelada,
     *                                        ou chave usada em outra operacao
     */
    @Transactional
    public ResultadoDaLiquidacao liquidar(Long operacaoId, String chaveIdempotencia,
                                          String liquidadoPor) {
        Optional<Liquidacao> comAMesmaChave =
                liquidacoes.findByChaveIdempotencia(chaveIdempotencia);

        if (comAMesmaChave.isPresent()) {
            Liquidacao existente = comAMesmaChave.get();
            Long jaLiquidada = existente.getOperacao().getId();

            if (!jaLiquidada.equals(operacaoId)) {
                throw ConflitoDeEstadoException.chaveEmUsoPorOutraOperacao(
                        chaveIdempotencia, jaLiquidada);
            }
            return ResultadoDaLiquidacao.repetida(existente);
        }

        Operacao operacao = operacoes.findById(operacaoId)
                .orElseThrow(() -> new OperacaoNaoEncontradaException(operacaoId));

        if (!operacao.getStatus().permiteLiquidacao()) {
            throw operacao.getStatus() == StatusOperacao.LIQUIDADA
                    ? ConflitoDeEstadoException.jaLiquidada(operacaoId)
                    : ConflitoDeEstadoException.statusNaoAdmiteLiquidacao(
                            operacaoId, operacao.getStatus());
        }

        // A mudanca de status e' o que faz o @Version entrar em acao: o UPDATE
        // sai com WHERE version = ?, e a transacao concorrente que leu a mesma
        // versao nao encontra linha para atualizar.
        operacao.marcarLiquidada();

        Liquidacao liquidacao = new Liquidacao(
                operacao,
                chaveIdempotencia,
                operacao.getValorLiquidacao(),
                operacao.getTaxaCambio() != null ? operacao.getTaxaCambio().getCotacao() : null,
                liquidadoPor);

        Liquidacao registrada = liquidacoes.save(liquidacao);

        // Mesma transacao: se o commit falhar por colisao de versao ou por
        // violacao de UNIQUE, o evento cai junto. Trilha com liquidacao que o
        // banco recusou seria pior que trilha faltando.
        auditoria.registrarLiquidacao(registrada, liquidadoPor);

        return ResultadoDaLiquidacao.nova(registrada);
    }

    /** Liquidacao de uma operacao, se houver. */
    @Transactional(readOnly = true)
    public Liquidacao porOperacao(Long operacaoId) {
        return liquidacoes.findByOperacaoId(operacaoId)
                .orElseThrow(() -> new LiquidacaoNaoEncontradaException(operacaoId));
    }
}
