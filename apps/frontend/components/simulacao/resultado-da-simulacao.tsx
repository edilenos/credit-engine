import { cotacao, data, decimal, moeda, percentual } from "@/lib/format";
import type { CodigoMoeda } from "@/types/api";
import type { SimulacaoResponse } from "@/types/simulacao";

/**
 * Resultado da simulacao — apresentacao pura.
 *
 * O criterio de aceite pede que a **composicao** apareca, nao so o total: o
 * operador precisa ver de onde saiu o numero — taxa base, spread, convencao,
 * expoente e cotacao — antes de decidir efetivar. Mostrar so o valor final
 * pediria confianca cega em cima de uma conta que ele e' o responsavel por
 * conferir.
 */

interface Props {
  resultado: SimulacaoResponse;
  /** Deixa o bloco opaco enquanto uma simulacao mais nova esta em voo. */
  desatualizado: boolean;
}

function Linha({
  rotulo,
  valor,
  destaque,
  ajuda,
}: {
  rotulo: string;
  valor: string;
  destaque?: boolean;
  ajuda?: string;
}) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-2">
      <span
        className={
          destaque
            ? "text-sm font-medium text-slate-700 dark:text-slate-200"
            : "text-sm text-slate-500 dark:text-slate-400"
        }
      >
        {rotulo}
        {ajuda && (
          <span className="ml-1 text-xs text-slate-400 dark:text-slate-500">({ajuda})</span>
        )}
      </span>
      <span
        className={
          destaque
            ? "tabular text-lg font-semibold text-slate-900 dark:text-slate-50"
            : "tabular text-sm text-slate-700 dark:text-slate-300"
        }
      >
        {valor}
      </span>
    </div>
  );
}

export function ResultadoDaSimulacao({ resultado, desatualizado }: Props) {
  const item = resultado.itens[0];
  const moedaTitulo = resultado.moedaTitulo as CodigoMoeda;
  const moedaLiquidacao = resultado.moedaLiquidacao as CodigoMoeda;

  return (
    <div
      // aria-busy avisa o leitor de tela que o numero exibido esta sendo
      // substituido, em vez de deixar a mudanca acontecer em silencio.
      aria-busy={desatualizado}
      className={`rounded-lg border border-slate-200 bg-white p-6 transition-opacity dark:border-slate-800 dark:bg-slate-950 ${
        desatualizado ? "opacity-50" : "opacity-100"
      }`}
    >
      <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
        Resultado
      </h2>

      <div className="mt-4 divide-y divide-slate-100 dark:divide-slate-800">
        <Linha
          rotulo="Valor de face"
          valor={moeda(resultado.valorFaceTotal, moedaTitulo)}
        />
        <Linha
          rotulo="Valor presente"
          valor={moeda(resultado.valorPresenteTotal, moedaTitulo)}
          destaque
        />
        <Linha rotulo="Deságio" valor={moeda(resultado.desagioTotal, moedaTitulo)} />
      </div>

      <h3 className="mt-6 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
        Composição da taxa
      </h3>
      <div className="mt-2 divide-y divide-slate-100 dark:divide-slate-800">
        <Linha
          rotulo="Taxa base"
          valor={percentual(item.taxaBaseAplicada)}
          ajuda="custo do fundo"
        />
        <Linha
          rotulo="Spread"
          valor={percentual(item.spreadAplicado)}
          ajuda="prêmio de risco do produto"
        />
        <Linha rotulo="Taxa total" valor={percentual(item.taxaTotal)} />
        <Linha rotulo="Convenção" valor={item.convencaoAplicada.replace(/_/g, "/")} />
        <Linha
          rotulo="Expoente"
          valor={decimal(item.expoenteAplicado, 4)}
          ajuda="prazo normalizado"
        />
      </div>

      <h3 className="mt-6 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
        Liquidação
      </h3>
      <div className="mt-2 divide-y divide-slate-100 dark:divide-slate-800">
        {resultado.crossCurrency && resultado.cotacaoAplicada !== null && (
          <Linha
            rotulo={`Cotação ${moedaTitulo} → ${moedaLiquidacao}`}
            valor={cotacao(resultado.cotacaoAplicada)}
            ajuda="aplicada ao final"
          />
        )}
        <Linha
          rotulo="Valor a desembolsar"
          valor={moeda(resultado.valorLiquidacao, moedaLiquidacao)}
          destaque
        />
        <Linha rotulo="Data da operação" valor={data(resultado.dataOperacao)} />
      </div>
    </div>
  );
}
