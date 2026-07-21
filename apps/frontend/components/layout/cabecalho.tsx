/**
 * Cabecalho da aplicacao.
 *
 * Apresentacao pura: sem estado, sem `fetch`, sem regra. Recebe tudo por prop
 * ou nao recebe nada.
 */
export function Cabecalho() {
  return (
    <header className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      <div className="mx-auto flex max-w-6xl items-baseline gap-3 px-6 py-4">
        <span className="text-lg font-semibold tracking-tight">SRM Credit Engine</span>
        <span className="text-sm text-slate-500 dark:text-slate-400">
          Cessao de credito multi-moeda
        </span>
      </div>
    </header>
  );
}
