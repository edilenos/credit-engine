package br.com.srm.creditengine.integracao.cotacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Resiliencia da integracao com o provedor externo (PBI-15).
 *
 * <p>O cliente HTTP e substituido por um duble: o alvo aqui e o comportamento
 * do retry e do circuit breaker, e derrubar um servidor de verdade so tornaria
 * o teste lento e intermitente.
 *
 * <p>O circuito e resetado antes de cada teste — estado de circuito e global ao
 * contexto Spring, e um teste que o deixa aberto contamina o proximo.
 */
@SpringBootTest
@DisplayName("Resiliencia do provedor de cotacao")
class ResilienciaDoProvedorTest {

    @MockitoBean
    private ClienteDeCotacao cliente;

    @Autowired
    private ProvedorDeCotacao provedor;

    @Autowired
    private CircuitBreakerRegistry registro;

    private CircuitBreaker circuito;

    @BeforeEach
    void reiniciarCircuito() {
        circuito = registro.circuitBreaker(ProvedorDeCotacao.INSTANCIA);
        circuito.reset();
        reset(cliente);
    }

    private ProvedorIndisponivelException falha() {
        return new ProvedorIndisponivelException("BRL", "USD", new RuntimeException("timeout"));
    }

    @Test
    @DisplayName("falha transitoria e superada pelo retry, sem chegar a quem chamou")
    void falhaTransitoriaEhSuperadaPeloRetry() {
        when(cliente.buscar(anyString(), anyString()))
                .thenThrow(falha())
                .thenReturn(new BigDecimal("0.185000"));

        BigDecimal cotacao = provedor.cotacaoAtual("BRL", "USD");

        assertThat(cotacao)
                .as("a primeira tentativa falhou, a segunda funcionou: o usuario nao ve nada")
                .isEqualByComparingTo(new BigDecimal("0.185000"));
        verify(cliente, atLeast(2)).buscar("BRL", "USD");
    }

    @Test
    @DisplayName("esgotadas as tentativas, o retry desiste e propaga")
    void retryDesisteAposOLimite() {
        when(cliente.buscar(anyString(), anyString())).thenThrow(falha());

        assertThatThrownBy(() -> provedor.cotacaoAtual("BRL", "USD"))
                .isInstanceOf(ProvedorIndisponivelException.class);

        verify(cliente, atMost(3))
                .buscar("BRL", "USD");
    }

    @Test
    @DisplayName("com o provedor fora, o circuito abre depois do limiar")
    void circuitoAbreDepoisDoLimiar() {
        when(cliente.buscar(anyString(), anyString())).thenThrow(falha());

        // A janela exige 5 chamadas minimas; com retry externo, cada invocacao
        // registra 3 no circuito, entao duas invocacoes bastam para preencher.
        for (int i = 0; i < 4; i++) {
            try {
                provedor.cotacaoAtual("BRL", "USD");
            } catch (ProvedorIndisponivelException ignorada) {
                // esperado enquanto o circuito ainda esta fechado
            }
        }

        assertThat(circuito.getState())
                .as("falha sustentada precisa abrir o circuito, nao insistir para sempre")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("com o circuito aberto, o provedor nao e mais chamado")
    void circuitoAbertoParaDeChamarOProvedor() {
        circuito.transitionToOpenState();

        assertThatThrownBy(() -> provedor.cotacaoAtual("BRL", "USD"))
                .isInstanceOf(ProvedorIndisponivelException.class);

        verify(cliente, never())
                .buscar(anyString(), anyString());
    }

    @Test
    @DisplayName("sucesso mantem o circuito fechado")
    void sucessoMantemCircuitoFechado() {
        when(cliente.buscar(anyString(), anyString())).thenReturn(new BigDecimal("0.185000"));

        for (int i = 0; i < 10; i++) {
            provedor.cotacaoAtual("BRL", "USD");
        }

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
