package br.com.srm.creditengine.negocio.cambio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.srm.creditengine.dominio.FonteCotacao;
import br.com.srm.creditengine.integracao.cotacao.ProvedorDeCotacao;
import br.com.srm.creditengine.integracao.cotacao.ProvedorIndisponivelException;
import br.com.srm.creditengine.persistencia.entidade.TaxaCambio;

/**
 * Sincroniza cotacoes com o provedor externo, degradando de forma controlada.
 *
 * <p>Atende ao paragrafo 3.1 do enunciado: "integracao (mockada) de taxas".
 *
 * <h2>A politica de degradacao vive aqui, nao na integracao</h2>
 *
 * A camada de integracao sabe apenas dizer "nao consegui". O que fazer com
 * isso — falhar, usar dado antigo, usar um valor padrao — e' decisao de
 * negocio, e muda conforme o contexto. Colocar a decisao junto do cliente HTTP
 * amarraria a politica ao transporte.
 *
 * <p>A politica adotada: usar a ultima cotacao conhecida e <b>marcar a resposta
 * como degradada</b>. Degradar em silencio seria pior que falhar — quem
 * precifica passaria a usar dado velho sem saber.
 *
 * <p>Nao havendo cotacao alguma no historico, nao ha o que degradar: a falha e'
 * propagada como {@link CotacaoIndisponivelException}, erro de negocio
 * explicito. E' o caso em que o sistema honestamente nao tem resposta.
 */
@Service
public class ServicoDeSincronizacaoDeCambio {

    private static final Logger log = LoggerFactory.getLogger(ServicoDeSincronizacaoDeCambio.class);

    private final ProvedorDeCotacao provedor;
    private final ServicoDeCambio cambio;

    public ServicoDeSincronizacaoDeCambio(ProvedorDeCotacao provedor, ServicoDeCambio cambio) {
        this.provedor = provedor;
        this.cambio = cambio;
    }

    /**
     * Busca a cotacao corrente no provedor e a registra.
     *
     * <p>Registrar e' acrescentar: o historico permanece intacto, e a cotacao
     * antiga continua valendo para operacoes datadas antes desta.
     *
     * @throws CotacaoIndisponivelException se o provedor falhar e nao houver
     *         nenhuma cotacao anterior para o par
     */
    public ResultadoSincronizacao sincronizar(String moedaOrigem, String moedaDestino) {
        OffsetDateTime agora = OffsetDateTime.now();
        try {
            BigDecimal cotacao = provedor.cotacaoAtual(moedaOrigem, moedaDestino);
            TaxaCambio registrada = cambio.registrar(
                    moedaOrigem, moedaDestino, cotacao, agora, FonteCotacao.PROVEDOR);

            log.info("cotacao_sincronizada origem={} destino={} cotacao={}",
                    moedaOrigem, moedaDestino, cotacao);
            return ResultadoSincronizacao.atualizada(registrada);

        } catch (ProvedorIndisponivelException falha) {
            return degradarParaUltimaConhecida(moedaOrigem, moedaDestino, agora, falha);
        }
    }

    private ResultadoSincronizacao degradarParaUltimaConhecida(
            String moedaOrigem, String moedaDestino, OffsetDateTime agora,
            ProvedorIndisponivelException falha) {

        TaxaCambio ultima = cambio.cotacaoVigente(moedaOrigem, moedaDestino, agora);

        log.warn("cotacao_degradada origem={} destino={} vigencia_da_cotacao_usada={} motivo={}",
                moedaOrigem, moedaDestino, ultima.getVigenciaInicio(), falha.getMessage());

        return ResultadoSincronizacao.degradada(ultima, falha.getMessage());
    }
}
