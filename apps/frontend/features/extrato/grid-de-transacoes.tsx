"use client";

import { FiltrosDoExtrato } from "@/components/extrato/filtros-do-extrato";
import { Paginacao } from "@/components/extrato/paginacao";
import { TabelaDeTransacoes } from "@/components/extrato/tabela-de-transacoes";
import { useCadastros } from "@/features/cadastros/use-cadastros";
import { useExtrato } from "@/features/extrato/use-extrato";
import { useFiltroNaUrl } from "@/features/extrato/use-filtro-na-url";

/**
 * Grid de Transacoes (PBI-37).
 *
 * <p>Container: liga o filtro que mora na URL a busca no servidor e passa dados
 * prontos para componentes de apresentacao. Nenhuma marcacao de tabela aqui, e
 * nenhum `fetch` tambem.
 */
export function GridDeTransacoes() {
  const { filtro, aplicar, irParaPagina, limpar, temFiltroAtivo } = useFiltroNaUrl();
  const { pagina, carregando, erro } = useExtrato(filtro);
  const cadastros = useCadastros();

  const semResultado = !carregando && !erro && pagina !== null && pagina.conteudo.length === 0;

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-xl font-semibold tracking-tight">Transações</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Histórico de liquidações. Os filtros ficam na URL — o link reproduz
          exatamente esta busca.
        </p>
      </header>

      <FiltrosDoExtrato
        filtro={filtro}
        moedas={cadastros.moedas}
        carregandoCadastros={cadastros.carregando}
        temFiltroAtivo={temFiltroAtivo}
        onAplicar={aplicar}
        onLimpar={limpar}
      />

      <section aria-live="polite">
        {erro && (
          <p
            role="alert"
            className="rounded-lg border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-900 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
          >
            {erro}
          </p>
        )}

        {/* Primeira carga: nao ha tabela para esmaecer ainda. */}
        {!erro && carregando && pagina === null && (
          <div className="rounded-lg border border-dashed border-slate-300 px-4 py-12 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
            Carregando transações…
          </div>
        )}

        {!erro && semResultado && (
          <div className="rounded-lg border border-dashed border-slate-300 px-4 py-12 text-center dark:border-slate-700">
            <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
              Nenhuma liquidação encontrada.
            </p>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {temFiltroAtivo
                ? "Tente ampliar o período ou remover algum filtro."
                : "Ainda não há operações liquidadas."}
            </p>
          </div>
        )}

        {!erro && pagina !== null && pagina.conteudo.length > 0 && (
          <>
            <TabelaDeTransacoes linhas={pagina.conteudo} desatualizado={carregando} />
            <Paginacao
              pagina={pagina.pagina}
              totalDePaginas={pagina.totalDePaginas}
              totalDeItens={pagina.totalDeItens}
              temProxima={pagina.temProxima}
              desabilitado={carregando}
              onIrParaPagina={irParaPagina}
            />
          </>
        )}
      </section>
    </div>
  );
}
