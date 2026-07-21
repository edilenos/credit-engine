import { data, moeda } from "@/lib/format";
import type { LinhaDoExtrato } from "@/types/extrato";

/**
 * Tabela do extrato — apresentacao pura.
 *
 * <p>Sem `fetch`, sem estado, sem regra: recebe linhas e devolve marcacao.
 * Quem busca e' o `useExtrato`; quem decide o filtro e' a URL.
 */
interface Props {
  linhas: LinhaDoExtrato[];
  /** Deixa o corpo opaco enquanto uma busca mais nova esta em voo. */
  desatualizado: boolean;
}

function Cabecalho({ children, numerico }: { children: React.ReactNode; numerico?: boolean }) {
  return (
    <th
      scope="col"
      className={`whitespace-nowrap px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400 ${
        numerico ? "text-right" : "text-left"
      }`}
    >
      {children}
    </th>
  );
}

function Celula({
  children,
  numerico,
  suave,
}: {
  children: React.ReactNode;
  numerico?: boolean;
  suave?: boolean;
}) {
  return (
    <td
      className={`whitespace-nowrap px-3 py-2 text-sm ${numerico ? "tabular text-right" : ""} ${
        suave ? "text-slate-500 dark:text-slate-400" : "text-slate-800 dark:text-slate-200"
      }`}
    >
      {children}
    </td>
  );
}

/** `2026-07-21T14:07:59.189Z` vira `21/07/2026`. */
function dataDoInstante(iso: string): string {
  return data(iso.slice(0, 10));
}

export function TabelaDeTransacoes({ linhas, desatualizado }: Props) {
  return (
    <div
      aria-busy={desatualizado}
      className={`overflow-x-auto rounded-lg border border-slate-200 bg-white transition-opacity dark:border-slate-800 dark:bg-slate-950 ${
        desatualizado ? "opacity-50" : "opacity-100"
      }`}
    >
      <table className="w-full border-collapse">
        <thead className="border-b border-slate-200 dark:border-slate-800">
          <tr>
            <Cabecalho>Liquidação</Cabecalho>
            <Cabecalho>Operação</Cabecalho>
            <Cabecalho>Cedente</Cabecalho>
            <Cabecalho numerico>Valor de face</Cabecalho>
            <Cabecalho numerico>Deságio</Cabecalho>
            <Cabecalho numerico>Liquidado</Cabecalho>
            <Cabecalho>Câmbio</Cabecalho>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
          {linhas.map((linha) => (
            <tr key={linha.liquidacaoId} className="hover:bg-slate-50 dark:hover:bg-slate-900">
              <Celula suave>{dataDoInstante(linha.liquidadoEm)}</Celula>
              <Celula suave>#{linha.operacaoId}</Celula>
              <Celula>
                <span className="block max-w-56 truncate" title={linha.razaoSocialCedente}>
                  {linha.razaoSocialCedente}
                </span>
              </Celula>
              <Celula numerico>{moeda(linha.valorFaceTotal, linha.moedaTitulo)}</Celula>
              <Celula numerico suave>
                {moeda(linha.desagioTotal, linha.moedaTitulo)}
              </Celula>
              <Celula numerico>{moeda(linha.valorLiquidado, linha.moedaLiquidacao)}</Celula>
              <Celula suave>
                {/* Moeda sempre indicada: a coluna mistura BRL e USD, e numero
                    solto aqui seria ambiguo justamente onde custa caro. */}
                {linha.cotacaoAplicada === null
                  ? "—"
                  : `${linha.moedaTitulo} → ${linha.moedaLiquidacao}`}
              </Celula>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
