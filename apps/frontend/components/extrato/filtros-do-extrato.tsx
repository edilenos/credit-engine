"use client";

import { CampoData, CampoSelecao } from "@/components/ui/campos";
import type { FiltroDoExtrato } from "@/types/extrato";
import type { Moeda } from "@/types/cadastros";

/**
 * Filtros do extrato — apresentacao pura.
 *
 * <p>Recebe os valores atuais e devolve mudancas; nao guarda estado proprio. O
 * estado mora na URL, e e' o container que o escreve.
 */
interface Props {
  filtro: FiltroDoExtrato;
  moedas: Moeda[];
  carregandoCadastros: boolean;
  temFiltroAtivo: boolean;
  onAplicar: (mudancas: Partial<FiltroDoExtrato>) => void;
  onLimpar: () => void;
}

export function FiltrosDoExtrato({
  filtro,
  moedas,
  carregandoCadastros,
  temFiltroAtivo,
  onAplicar,
  onLimpar,
}: Props) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <CampoData
          id="filtro-de"
          rotulo="Liquidado de"
          valor={filtro.de ?? ""}
          onChange={(valor) => onAplicar({ de: valor || undefined })}
        />
        <CampoData
          id="filtro-ate"
          rotulo="Liquidado até"
          valor={filtro.ate ?? ""}
          onChange={(valor) => onAplicar({ ate: valor || undefined })}
        />
        <CampoSelecao
          id="filtro-moeda"
          rotulo="Moeda de liquidação"
          valor={filtro.moedaLiquidacao ?? ""}
          desabilitado={carregandoCadastros}
          opcoes={moedas.map((m) => ({ valor: m.codigo, rotulo: `${m.codigo} — ${m.nome}` }))}
          onChange={(valor) => onAplicar({ moedaLiquidacao: valor || undefined })}
        />
        <CampoSelecao
          id="filtro-ordenacao"
          rotulo="Ordenar por"
          valor={filtro.ordenarPor ?? "LIQUIDADO_EM"}
          opcoes={[
            { valor: "LIQUIDADO_EM", rotulo: "Data da liquidação" },
            { valor: "VALOR_LIQUIDADO", rotulo: "Valor liquidado" },
            { valor: "CEDENTE", rotulo: "Cedente" },
            { valor: "OPERACAO", rotulo: "Operação" },
          ]}
          onChange={(valor) =>
            onAplicar({ ordenarPor: (valor || undefined) as FiltroDoExtrato["ordenarPor"] })
          }
        />
      </div>

      {temFiltroAtivo && (
        <button
          type="button"
          onClick={onLimpar}
          className="mt-3 text-sm font-medium text-slate-600 underline underline-offset-2 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          Limpar filtros
        </button>
      )}
    </section>
  );
}
