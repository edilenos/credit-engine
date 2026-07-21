package br.com.srm.creditengine.negocio.auditoria;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.dominio.EntidadeAuditada;
import br.com.srm.creditengine.dominio.TipoEvento;
import br.com.srm.creditengine.persistencia.entidade.EventoAuditoria;
import br.com.srm.creditengine.persistencia.entidade.Liquidacao;
import br.com.srm.creditengine.persistencia.entidade.Operacao;
import br.com.srm.creditengine.persistencia.repositorio.EventoAuditoriaRepositorio;
import tools.jackson.databind.ObjectMapper;

/**
 * Trilha de auditoria (PBI-30).
 *
 * <h2>Participa da transacao de quem chama</h2>
 *
 * {@code Propagation.MANDATORY} nao e' decorativo: obriga que exista uma
 * transacao aberta. E' o que garante que operacao e evento entrem ou saiam
 * juntos — se a cessao falhar depois de auditada, o evento desaparece no
 * rollback.
 *
 * <p>A tentacao aqui e' {@code REQUIRES_NEW}, "para nao perder a auditoria se a
 * operacao falhar". Seria o contrario do desejado: geraria evento de uma cessao
 * que nunca existiu, e a trilha passaria a mentir. Auditoria de fato que nao
 * aconteceu e' pior que auditoria ausente.
 *
 * <h2>O payload e' escolhido, nunca despejado</h2>
 *
 * Serializar a entidade inteira grava dado desnecessario hoje e vaza qualquer
 * campo sensivel que ela venha a ganhar amanha, sem ninguem perceber. Os
 * parametros congelados que reproduzem o calculo ja moram em
 * {@code operacao} e {@code recebivel}; ao evento cabe a linha do tempo —
 * quem fez o que, quando, e com que numeros de fechamento.
 */
@Service
public class ServicoDeAuditoria {

    private final EventoAuditoriaRepositorio eventos;
    private final ObjectMapper json;

    public ServicoDeAuditoria(EventoAuditoriaRepositorio eventos, ObjectMapper json) {
        this.eventos = eventos;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarCessao(Operacao operacao, String ator) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operacaoId", operacao.getId());
        payload.put("cedente", operacao.getCedente().getDocumento());
        payload.put("moedaTitulo", operacao.getMoedaTitulo().getCodigo());
        payload.put("moedaLiquidacao", operacao.getMoedaLiquidacao().getCodigo());
        payload.put("valorFaceTotal", operacao.getValorFaceTotal().toPlainString());
        payload.put("valorPresenteTotal", operacao.getValorPresenteTotal().toPlainString());
        payload.put("valorLiquidacao", operacao.getValorLiquidacao().toPlainString());
        payload.put("quantidadeDeRecebiveis", operacao.getRecebiveis().size());
        payload.put("status", operacao.getStatus().name());

        eventos.save(new EventoAuditoria(
                EntidadeAuditada.OPERACAO, operacao.getId(),
                TipoEvento.OPERACAO_REGISTRADA, serializar(payload), ator));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarLiquidacao(Liquidacao liquidacao, String ator) {
        Operacao operacao = liquidacao.getOperacao();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("liquidacaoId", liquidacao.getId());
        payload.put("operacaoId", operacao.getId());
        payload.put("valorLiquidado", liquidacao.getValorLiquidado().toPlainString());
        payload.put("moedaLiquidacao", operacao.getMoedaLiquidacao().getCodigo());
        // Chave nao e' segredo: identifica o pedido do cliente e e' o que
        // permite rastrear um retry ate a liquidacao que o originou.
        payload.put("chaveIdempotencia", liquidacao.getChaveIdempotencia());

        if (liquidacao.getCotacaoAplicada() != null) {
            payload.put("cotacaoAplicada", liquidacao.getCotacaoAplicada().toPlainString());
        }

        eventos.save(new EventoAuditoria(
                EntidadeAuditada.LIQUIDACAO, liquidacao.getId(),
                TipoEvento.OPERACAO_LIQUIDADA, serializar(payload), ator));
    }

    /**
     * Valores monetarios viram string com {@code toPlainString()}.
     *
     * <p>Serializados como numero, entrariam no {@code jsonb} sujeitos a
     * releitura como ponto flutuante — a mesma perda que o sistema inteiro
     * evita usando {@code BigDecimal}. Numa trilha de auditoria isso seria
     * especialmente ruim: o registro existe justamente para dizer qual foi o
     * numero.
     */
    private String serializar(Map<String, Object> payload) {
        return json.writeValueAsString(payload);
    }
}
