"use client";

/**
 * Controles de paginacao — apresentacao pura.
 *
 * <p>Nao fatia nada: cada botao pede uma pagina ao container, que muda a URL e
 * dispara nova requisicao. E' o requisito explicito do enunciado, e o erro
 * comum aqui e' carregar tudo e paginar em memoria.
 */
interface Props {
  pagina: number;
  totalDePaginas: number;
  totalDeItens: number;
  temProxima: boolean;
  desabilitado: boolean;
  onIrParaPagina: (pagina: number) => void;
}

const botao =
  "rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium " +
  "hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40 " +
  "dark:border-slate-700 dark:hover:bg-slate-800";

export function Paginacao({
  pagina,
  totalDePaginas,
  totalDeItens,
  temProxima,
  desabilitado,
  onIrParaPagina,
}: Props) {
  return (
    <nav
      aria-label="Paginação do extrato"
      className="mt-4 flex flex-wrap items-center justify-between gap-3"
    >
      <p className="text-sm text-slate-600 dark:text-slate-400">
        {/* O total vem do servidor e e' do filtro, nao da pagina. */}
        <span className="tabular font-medium">{totalDeItens.toLocaleString("pt-BR")}</span>{" "}
        {totalDeItens === 1 ? "liquidação" : "liquidações"}
        {totalDePaginas > 0 && (
          <>
            {" · página "}
            <span className="tabular">{pagina + 1}</span> de{" "}
            <span className="tabular">{totalDePaginas}</span>
          </>
        )}
      </p>

      <div className="flex gap-2">
        <button
          type="button"
          className={botao}
          disabled={desabilitado || pagina === 0}
          onClick={() => onIrParaPagina(pagina - 1)}
        >
          Anterior
        </button>
        <button
          type="button"
          className={botao}
          disabled={desabilitado || !temProxima}
          onClick={() => onIrParaPagina(pagina + 1)}
        >
          Próxima
        </button>
      </div>
    </nav>
  );
}
