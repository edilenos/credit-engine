import { Suspense } from "react";

import { GridDeTransacoes } from "@/features/extrato/grid-de-transacoes";

/**
 * Grid de Transacoes.
 *
 * <p>O {@code Suspense} nao e' opcional: {@code useSearchParams} numa rota
 * pre-renderizada faz o Next exigir uma fronteira ate a qual a arvore possa ser
 * renderizada no cliente. Sem ele o {@code pnpm build} falha — o que e' bom,
 * porque a alternativa seria descobrir em producao.
 */
export default function Transacoes() {
  return (
    <Suspense
      fallback={
        <div className="rounded-lg border border-dashed border-slate-300 px-4 py-12 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
          Carregando transações…
        </div>
      }
    >
      <GridDeTransacoes />
    </Suspense>
  );
}
