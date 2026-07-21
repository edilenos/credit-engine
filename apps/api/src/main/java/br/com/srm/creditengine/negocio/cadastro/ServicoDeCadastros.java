package br.com.srm.creditengine.negocio.cadastro;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srm.creditengine.persistencia.entidade.Moeda;
import br.com.srm.creditengine.persistencia.entidade.TipoRecebivel;
import br.com.srm.creditengine.persistencia.repositorio.MoedaRepositorio;
import br.com.srm.creditengine.persistencia.repositorio.TipoRecebivelRepositorio;

/**
 * Dados de referencia para preencher formularios (PBI-26).
 *
 * <p>O painel do operador precisa saber quais produtos e moedas existem. Fixar
 * essa lista no frontend contradiria a razao de o spread morar no banco: hoje um
 * produto novo entra por cadastro, e com a lista fixa passaria a exigir deploy
 * do frontend tambem.
 *
 * <p>Somente tipos <b>ativos</b> sao expostos. Produto desativado continua no
 * banco porque operacoes antigas o referenciam, mas oferecer para nova operacao
 * seria oferecer o que a mesa ja decidiu nao operar.
 */
@Service
public class ServicoDeCadastros {

    private final TipoRecebivelRepositorio tipos;
    private final MoedaRepositorio moedas;

    public ServicoDeCadastros(TipoRecebivelRepositorio tipos, MoedaRepositorio moedas) {
        this.tipos = tipos;
        this.moedas = moedas;
    }

    @Transactional(readOnly = true)
    public List<TipoRecebivel> tiposAtivos() {
        return tipos.findByAtivoTrue();
    }

    @Transactional(readOnly = true)
    public List<Moeda> moedas() {
        return moedas.findAll();
    }
}
