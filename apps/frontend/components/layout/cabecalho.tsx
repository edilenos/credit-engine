"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

/**
 * Cabecalho da aplicacao.
 *
 * <p>Virou client component quando a segunda tela chegou: marcar o item ativo
 * exige saber a rota atual. A navegacao so apareceu agora de proposito — com
 * uma tela so, um menu seria decoracao.
 */
const ROTAS = [
  { href: "/", rotulo: "Painel" },
  { href: "/transacoes", rotulo: "Transações" },
];

export function Cabecalho() {
  const caminho = usePathname();

  return (
    <header className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-8 gap-y-2 px-6 py-4">
        <div className="flex items-baseline gap-3">
          <span className="text-lg font-semibold tracking-tight">SRM Credit Engine</span>
          <span className="hidden text-sm text-slate-500 sm:inline dark:text-slate-400">
            Cessão de crédito multi-moeda
          </span>
        </div>

        <nav aria-label="Navegação principal" className="flex gap-1">
          {ROTAS.map((rota) => {
            const ativa = caminho === rota.href;
            return (
              <Link
                key={rota.href}
                href={rota.href}
                aria-current={ativa ? "page" : undefined}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  ativa
                    ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
                    : "text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
                }`}
              >
                {rota.rotulo}
              </Link>
            );
          })}
        </nav>
      </div>
    </header>
  );
}
