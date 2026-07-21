package br.com.srm.creditengine.negocio.cambio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.dominio.FonteCotacao;
import br.com.srm.creditengine.integracao.cotacao.ClienteDeCotacao;
import br.com.srm.creditengine.integracao.cotacao.ProvedorDeCotacao;
import br.com.srm.creditengine.integracao.cotacao.ProvedorIndisponivelException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Politica de degradacao da sincronizacao (PBI-15).
 *
 * <p>Complementa {@code ResilienciaDoProvedorTest}: la o alvo e o mecanismo —
 * retry e circuito; aqui e a decisao de negocio sobre o que fazer quando ele
 * falha.
 */
@SpringBootTest
@Transactional
@DisplayName("Sincronizacao de cambio")
class ServicoDeSincronizacaoDeCambioTest {

    @MockitoBean
    private ClienteDeCotacao cliente;

    @Autowired
    private ServicoDeSincronizacaoDeCambio sincronizacao;

    @Autowired
    private CircuitBreakerRegistry registro;

    @BeforeEach
    void reiniciarCircuito() {
        registro.circuitBreaker(ProvedorDeCotacao.INSTANCIA).reset();
    }

    @Test
    @DisplayName("provedor respondendo: registra a cotacao nova e nao marca degradado")
    void provedorRespondendoRegistraCotacaoNova() {
        when(cliente.buscar(anyString(), anyString())).thenReturn(new BigDecimal("0.195000"));

        ResultadoSincronizacao resultado = sincronizacao.sincronizar("BRL", "USD");

        assertThat(resultado.degradado()).isFalse();
        assertThat(resultado.cotacao().getCotacao()).isEqualByComparingTo(new BigDecimal("0.195000"));
        assertThat(resultado.cotacao().getFonte())
                .as("cotacao vinda de integracao precisa ser distinguivel de cadastro humano")
                .isEqualTo(FonteCotacao.PROVEDOR);
    }

    @Test
    @DisplayName("provedor fora: usa a ultima conhecida e marca a resposta como degradada")
    void provedorForaDegradaParaUltimaConhecida() {
        when(cliente.buscar(anyString(), anyString()))
                .thenThrow(new ProvedorIndisponivelException("BRL", "USD", new RuntimeException("timeout")));

        ResultadoSincronizacao resultado = sincronizacao.sincronizar("BRL", "USD");

        assertThat(resultado.degradado())
                .as("degradar em silencio seria pior que falhar: a mesa usaria dado velho sem saber")
                .isTrue();
        assertThat(resultado.motivo()).isNotBlank();
        assertThat(resultado.cotacao())
                .as("a ultima cotacao conhecida vem do seed")
                .isNotNull();
    }

    @Test
    @DisplayName("provedor fora e sem historico: erro de negocio explicito, nao NPE")
    void semHistoricoPropagaErroDeNegocio() {
        when(cliente.buscar(anyString(), anyString()))
                .thenThrow(new ProvedorIndisponivelException("USD", "XTZ", new RuntimeException("timeout")));

        // Par que nunca foi cotado: nao ha o que degradar.
        assertThatThrownBy(() -> sincronizacao.sincronizar("USD", "XTZ"))
                .isInstanceOf(CotacaoIndisponivelException.class);
    }

    @Test
    @DisplayName("sincronizar acrescenta ao historico, nao sobrescreve")
    void sincronizarAcrescentaAoHistorico() {
        when(cliente.buscar(anyString(), anyString())).thenReturn(new BigDecimal("0.196000"));
        int antes = contarHistorico();

        sincronizacao.sincronizar("BRL", "USD");

        assertThat(contarHistorico())
                .as("append-only vale tambem para cotacao vinda de integracao")
                .isEqualTo(antes + 1);
    }

    @Autowired
    private ServicoDeCambio cambio;

    private int contarHistorico() {
        return cambio.historico("BRL", "USD").size();
    }
}
