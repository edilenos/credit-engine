package br.com.srm.creditengine.negocio.precificacao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.Periodicidade;
import br.com.srm.creditengine.persistencia.entidade.ParametroPrecificacao;
import br.com.srm.creditengine.persistencia.repositorio.ParametroPrecificacaoRepositorio;

/**
 * Calcula o valor presente de um titulo — o coracao do sistema.
 *
 * <pre>
 *   Valor Presente = Valor Face / (1 + Taxa Base + Spread) ^ Expoente
 * </pre>
 *
 * <p>O motor <b>compoe</b>, nao decide: o spread vem da Strategy do produto
 * (PBI-18), o expoente vem da convencao de contagem (PBI-19/20), a taxa base
 * vem do parametro vigente, e a politica decimal vem de
 * {@link PrecisaoDecimal}. E' essa divisao que permite trocar a regra de risco
 * de um produto, ou a convencao de um contrato, sem tocar nesta classe.
 *
 * <h2>Potenciacao com expoente fracionario</h2>
 *
 * {@code BigDecimal.pow()} so aceita expoente {@code int}, e quatro das seis
 * convencoes produzem expoente fracionario — e' o caso normal, nao a excecao.
 * Um titulo de 46 dias sob ACT/30 tem expoente 1,5333.
 *
 * <p>A solucao e' {@link BigDecimalMath#pow(BigDecimal, BigDecimal, java.math.MathContext)},
 * que faz potenciacao decimal em precisao arbitraria e deterministica. A
 * alternativa seria {@code Math.pow} com {@code double}: erro relativo na casa
 * de 1e-16, numericamente irrelevante, mas indefensavel sob um criterio de
 * avaliacao chamado "precisao decimal" — e nao reproduzivel bit a bit, porque
 * {@code Math.pow} pode usar intrinsecos de plataforma.
 *
 * <h2>O que NAO se faz aqui</h2>
 *
 * Nao se arredonda o prazo para meses inteiros. Nao e' simplificacao tecnica,
 * e' mudanca de preco: um titulo de R$ 100.000 a 2,5% a.m. com 46 dias vale
 * 96.284,58 com o expoente exato e 95.181,44 com o prazo arredondado para 2
 * meses — 1,10% do valor de face, ordens de grandeza acima de qualquer erro de
 * ponto flutuante.
 *
 * <p>A conversao cambial tambem nao acontece aqui: e' aplicada <b>depois</b> do
 * valor presente estar calculado (PBI-22), porque a ordem muda o arredondamento.
 */
@Service
public class MotorDePrecificacao {

    private final ResolvedorDeSpread spreads;
    private final ResolvedorDeConvencao convencoes;
    private final ParametroPrecificacaoRepositorio parametros;

    public MotorDePrecificacao(ResolvedorDeSpread spreads,
                               ResolvedorDeConvencao convencoes,
                               ParametroPrecificacaoRepositorio parametros) {
        this.spreads = spreads;
        this.convencoes = convencoes;
        this.parametros = parametros;
    }

    /**
     * Precifica um titulo com os parametros vigentes na data da operacao.
     *
     * @throws ParametroDePrecificacaoIndisponivelException se nao houver taxa base vigente
     * @throws UnidadeIncompativelException se taxa e convencao nao estiverem na mesma unidade
     * @throws EstrategiaDeSpreadNaoEncontradaException se o produto nao tiver regra de risco
     */
    @Transactional(readOnly = true)
    public PrecificacaoDoTitulo precificar(ContextoDePrecificacao contexto) {
        ParametroPrecificacao parametro = parametroVigenteEm(contexto);
        BigDecimal taxaBase = parametro.getTaxaBase();
        BigDecimal spread = spreads.spreadPara(contexto);

        Periodicidade unidade = unidadeCoerente(parametro, contexto);
        ConvencaoContagem convencao = contexto.tipo().getConvencaoContagem();

        BigDecimal expoente = convencoes.expoente(
                convencao, unidade, contexto.dataOperacao(), contexto.dataVencimento());

        BigDecimal valorPresente = CalculadoraDeValorPresente.descontar(
                contexto.valorFace(), taxaBase.add(spread), expoente);

        return new PrecificacaoDoTitulo(
                contexto.valorFace(), valorPresente, taxaBase, spread, convencao, expoente);
    }

    /**
     * Taxa base e spread precisam estar na MESMA unidade antes de serem somados.
     *
     * <p>Somar 1% ao mes com 2,5% ao ano produz um numero sem significado, e
     * nenhuma excecao acontece sozinha. A checagem contra a convencao (risco R7)
     * fica no resolvedor; esta aqui e' a que garante que a propria soma faz
     * sentido.
     */
    private Periodicidade unidadeCoerente(ParametroPrecificacao parametro,
                                          ContextoDePrecificacao contexto) {
        Periodicidade daTaxaBase = parametro.getPeriodicidade();
        Periodicidade doSpread = contexto.tipo().getPeriodicidade();

        if (daTaxaBase != doSpread) {
            throw new UnidadesDaTaxaDivergentesException(daTaxaBase, doSpread);
        }
        return daTaxaBase;
    }

    private ParametroPrecificacao parametroVigenteEm(ContextoDePrecificacao contexto) {
        // Vigente na data da operacao significa "ja valia quando o dia comecou".
        // A leitura conservadora evita que um parametro cadastrado durante o dia
        // se aplique retroativamente a operacoes ja fechadas naquele dia.
        OffsetDateTime momento = contexto.dataOperacao().atStartOfDay().atOffset(ZoneOffset.UTC);

        return parametros.vigenteEm(momento)
                .orElseThrow(() -> new ParametroDePrecificacaoIndisponivelException(momento));
    }
}
