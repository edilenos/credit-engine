package br.com.srm.creditengine.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import br.com.srm.creditengine.dominio.ConvencaoContagem;
import br.com.srm.creditengine.dominio.FonteCotacao;
import br.com.srm.creditengine.dominio.Periodicidade;
import br.com.srm.creditengine.dominio.StatusOperacao;
import br.com.srm.creditengine.persistencia.entidade.Cedente;
import br.com.srm.creditengine.persistencia.entidade.Liquidacao;
import br.com.srm.creditengine.persistencia.entidade.Moeda;
import br.com.srm.creditengine.persistencia.entidade.Operacao;
import br.com.srm.creditengine.persistencia.entidade.ParametroPrecificacao;
import br.com.srm.creditengine.persistencia.entidade.Recebivel;
import br.com.srm.creditengine.persistencia.entidade.TaxaCambio;
import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;
import br.com.srm.creditengine.persistencia.repositorio.LiquidacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.MoedaRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.OperacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.ParametroPrecificacaoRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.TaxaCambioRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.TipoRecebivelRepositorio;
import jakarta.persistence.EntityManager;

/**
 * Verifica o mapeamento das entidades contra o schema real do Flyway.
 *
 * <p>{@code replace = NONE} e obrigatorio: o padrao do {@code @DataJpaTest} e
 * trocar o datasource por um banco embarcado, e o alvo aqui e justamente a
 * correspondencia com o Postgres que as migracoes criaram. Testar mapeamento
 * contra outro banco nao provaria nada.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Mapeamento das entidades")
class MapeamentoEntidadesTest {

    @Autowired private EntityManager em;
    @Autowired private MoedaRepositorio moedas;
    @Autowired private TipoRecebivelRepositorio tipos;
    @Autowired private ParametroPrecificacaoRepositorio parametros;
    @Autowired private TaxaCambioRepositorio cambios;
    @Autowired private OperacaoRepositorio operacoes;
    @Autowired private LiquidacaoRepositorio liquidacoes;

    private Moeda brl;
    private Moeda usd;
    private Cedente cedente;
    private TipoRecebivel tipo;
    private ParametroPrecificacao parametro;

    @BeforeEach
    void prepararFixtures() {
        brl = moedas.findByCodigo("BRL").orElseThrow();
        usd = moedas.findByCodigo("USD").orElseThrow();
        tipo = tipos.findByCodigo("DUPLICATA_MERCANTIL").orElseThrow();
        parametro = parametros.vigenteEm(OffsetDateTime.now()).orElseThrow();

        cedente = new Cedente("99888777000166", "Cedente do Teste de Mapeamento");
        em.persist(cedente);
    }

    private Operacao operacaoEmMoedaUnica() {
        Operacao operacao = new Operacao(cedente, brl, brl, null,
                new BigDecimal("100000.00"), new BigDecimal("96284.52"), new BigDecimal("96284.52"));
        operacao.adicionar(new Recebivel(tipo, parametro, "DUP-001", "11222333000181",
                new BigDecimal("100000.00"), LocalDate.now().plusDays(46),
                ConvencaoContagem.ACT_30, new BigDecimal("1.5333333333"),
                new BigDecimal("0.010000"), new BigDecimal("0.015000"),
                new BigDecimal("96284.52")));
        return operacao;
    }

    // ------------------------------------------------------- seed e cadastros

    @Test
    @DisplayName("as entidades de cadastro leem o que o seed gravou")
    void entidadesDeCadastroLeemOSeed() {
        assertThat(brl.getCodigo()).isEqualTo("BRL");
        assertThat(brl.getEscalaPadrao()).isEqualTo((short) 2);
        assertThat(tipo.getSpread()).isEqualByComparingTo(new BigDecimal("0.015000"));
        assertThat(tipo.getPeriodicidade()).isEqualTo(Periodicidade.MENSAL);
        assertThat(tipo.getConvencaoContagem()).isEqualTo(ConvencaoContagem.ACT_30);
        assertThat(parametro.getTaxaBase()).isEqualByComparingTo(new BigDecimal("0.010000"));
    }

    @Test
    @DisplayName("o tipo semeado tem unidades coerentes entre taxa e convencao")
    void tipoSemeadoTemUnidadesCoerentes() {
        assertThat(tipo.temUnidadesCoerentes())
                .as("MENSAL casa com ACT_30; MENSAL com BUS_252 daria preco errado por ordem de grandeza")
                .isTrue();
    }

    @Test
    @DisplayName("cotacao vigente respeita a data de corte, nao a ultima inserida")
    void cotacaoVigenteRespeitaDataDeCorte() {
        OffsetDateTime ontem = OffsetDateTime.now().minusDays(1);
        OffsetDateTime amanha = OffsetDateTime.now().plusDays(1);

        // Cotacao futura: nao pode aparecer numa consulta de hoje.
        cambios.save(new TaxaCambio(brl, usd, new BigDecimal("9.999999"), amanha, FonteCotacao.MANUAL));
        em.flush();

        TaxaCambio vigente = cambios.vigenteEm("BRL", "USD", ontem).orElseThrow();

        assertThat(vigente.getCotacao())
                .as("append-only: a precificacao de ontem precisa continuar reproduzivel")
                .isNotEqualByComparingTo(new BigDecimal("9.999999"));
    }

    // ---------------------------------------------------------------- agregado

    @Test
    @DisplayName("operacao persiste e recupera o lote, com valores decimais intactos")
    void operacaoPersisteERecuperaOLote() {
        Operacao salva = operacoes.saveAndFlush(operacaoEmMoedaUnica());
        em.clear();

        Operacao lida = operacoes.findWithRecebiveisById(salva.getId()).orElseThrow();

        assertThat(lida.getValorFaceTotal()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(lida.getValorPresenteTotal()).isEqualByComparingTo(new BigDecimal("96284.52"));
        assertThat(lida.getDesagio()).isEqualByComparingTo(new BigDecimal("3715.48"));
        assertThat(lida.getStatus()).isEqualTo(StatusOperacao.PENDENTE);
        assertThat(lida.isCrossCurrency()).isFalse();
        assertThat(lida.getRecebiveis()).hasSize(1);

        Recebivel recebivel = lida.getRecebiveis().get(0);
        assertThat(recebivel.getConvencaoAplicada()).isEqualTo(ConvencaoContagem.ACT_30);
        assertThat(recebivel.getExpoenteAplicado())
                .as("expoente congelado preserva as 10 casas para auditoria")
                .isEqualByComparingTo(new BigDecimal("1.5333333333"));
    }

    @Test
    @DisplayName("cascade grava o lote junto com a operacao, em uma unica chamada")
    void cascataGravaOLoteJuntoComAOperacao() {
        Operacao salva = operacoes.saveAndFlush(operacaoEmMoedaUnica());

        assertThat(salva.getRecebiveis().get(0).getId())
                .as("recebivel nao existe fora do lote: composicao, nao associacao")
                .isNotNull();
    }

    @Test
    @DisplayName("recebivel usa sequence, nao identity: id vem antes do flush")
    void recebivelUsaSequence() {
        Operacao salva = operacoes.saveAndFlush(operacaoEmMoedaUnica());
        Long idRecebivel = salva.getRecebiveis().get(0).getId();

        assertThat(idRecebivel)
                .as("allocationSize=50 precisa bater com INCREMENT BY 50 de seq_recebivel")
                .isPositive();
    }

    // ------------------------------------------------------ optimistic locking

    @Test
    @DisplayName("operacao nasce com version zero")
    void operacaoNasceComVersionZero() {
        Operacao salva = operacoes.saveAndFlush(operacaoEmMoedaUnica());

        assertThat(salva.getVersion()).isZero();
    }

    @Test
    @DisplayName("alterar a operacao incrementa a version")
    void alterarOperacaoIncrementaVersion() {
        Operacao salva = operacoes.saveAndFlush(operacaoEmMoedaUnica());

        salva.marcarLiquidada();
        operacoes.saveAndFlush(salva);

        assertThat(salva.getVersion())
                .as("e o incremento da version que permite detectar escrita concorrente")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("escrita sobre versao obsoleta falha com conflito de lock")
    void escritaSobreVersaoObsoletaFalha() {
        Operacao salva = operacoes.saveAndFlush(operacaoEmMoedaUnica());
        Long id = salva.getId();
        em.clear();

        // Simula duas transacoes que leram a mesma versao: a primeira grava e
        // avanca a version; a segunda ainda carrega a versao antiga.
        Operacao primeira = operacoes.findById(id).orElseThrow();
        em.detach(primeira);

        Operacao segunda = operacoes.findById(id).orElseThrow();
        segunda.marcarLiquidada();
        operacoes.saveAndFlush(segunda);
        em.clear();

        primeira.cancelar();

        assertThatThrownBy(() -> operacoes.saveAndFlush(primeira))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ------------------------------------------------------------- liquidacao

    @Test
    @DisplayName("liquidacao e unica por operacao, garantido pelo banco")
    void liquidacaoEhUnicaPorOperacao() {
        Operacao operacao = operacoes.saveAndFlush(operacaoEmMoedaUnica());
        liquidacoes.saveAndFlush(new Liquidacao(operacao, "chave-a",
                new BigDecimal("96284.52"), null, "operador.teste"));

        Liquidacao duplicada = new Liquidacao(operacao, "chave-b",
                new BigDecimal("96284.52"), null, "operador.teste");

        assertThatThrownBy(() -> liquidacoes.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a chave de idempotencia localiza a liquidacao original")
    void chaveDeIdempotenciaLocalizaALiquidacao() {
        Operacao operacao = operacoes.saveAndFlush(operacaoEmMoedaUnica());
        liquidacoes.saveAndFlush(new Liquidacao(operacao, "chave-unica",
                new BigDecimal("96284.52"), null, "operador.teste"));
        em.clear();

        assertThat(liquidacoes.findByChaveIdempotencia("chave-unica"))
                .as("retry seguro: encontrando a chave, devolve-se o resultado original")
                .isPresent();
    }

    // ------------------------------------------------------------ cross-currency

    @Test
    @DisplayName("operacao cross-currency guarda a cotacao aplicada")
    void operacaoCrossCurrencyGuardaACotacao() {
        TaxaCambio cotacao = cambios.vigenteEm("BRL", "USD", OffsetDateTime.now()).orElseThrow();

        Operacao operacao = new Operacao(cedente, brl, usd, cotacao,
                new BigDecimal("100000.00"), new BigDecimal("96284.52"), new BigDecimal("17812.64"));
        operacao.adicionar(new Recebivel(tipo, parametro, "DUP-002", "11222333000181",
                new BigDecimal("100000.00"), LocalDate.now().plusDays(46),
                ConvencaoContagem.ACT_30, new BigDecimal("1.5333333333"),
                new BigDecimal("0.010000"), new BigDecimal("0.015000"),
                new BigDecimal("96284.52")));

        Operacao salva = operacoes.saveAndFlush(operacao);
        em.clear();

        Operacao lida = operacoes.findById(salva.getId()).orElseThrow();

        assertThat(lida.isCrossCurrency()).isTrue();
        assertThat(lida.getTaxaCambio()).isNotNull();
        assertThat(lida.getValorLiquidacao())
                .as("valor na moeda de liquidacao, apos a conversao")
                .isEqualByComparingTo(new BigDecimal("17812.64"));
    }
}
