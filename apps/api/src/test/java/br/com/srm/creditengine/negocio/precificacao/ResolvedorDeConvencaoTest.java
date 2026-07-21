package br.com.srm.creditengine.negocio.precificacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import br.com.srm.creditengine.dominio.ConvencaoContagem;

/**
 * Fiacao das convencoes de contagem no contexto real.
 *
 * <p>A logica de cada convencao e testada sem Spring em
 * {@code ConvencoesDeContagemTest} — o PBI-23 moveu para la tudo que e' funcao
 * pura de duas datas. O que sobrou aqui e' o unico ponto que so o contexto pode
 * responder: se todas as implementacoes foram de fato descobertas por injecao.
 *
 * <p>Duplicar os casos de calculo nos dois niveis custaria segundos de startup
 * para reconferir uma divisao que ja foi conferida em milissegundos.
 */
@SpringBootTest
@DisplayName("Convencoes de contagem — fiacao")
class ResolvedorDeConvencaoTest {

    @Autowired
    private ResolvedorDeConvencao resolvedor;

    @Test
    @DisplayName("todas as convencoes do dominio tem implementacao registrada")
    void todasAsConvencoesRegistradas() {
        assertThat(resolvedor.convencoesRegistradas())
                .as("constante no enum sem classe correspondente falha so em runtime, "
                        + "na hora de precificar")
                .containsExactlyInAnyOrder(ConvencaoContagem.values());
    }
}
