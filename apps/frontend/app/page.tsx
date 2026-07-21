/**
 * Pagina inicial.
 *
 * Deliberadamente enxuta: o PBI-25 estabelece a arquitetura, e a primeira tela
 * de verdade — o Painel do Operador — e' o PBI-26. Inventar interface aqui so
 * para a raiz nao ficar vazia geraria codigo que o proximo PBI joga fora.
 */
export default function Home() {
  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-semibold tracking-tight">
        Motor de cessao de credito
      </h1>
      <p className="mt-3 text-slate-600 dark:text-slate-400">
        O fundo adquire recebiveis com desagio, precifica pelo tipo de titulo e
        liquida na moeda contratada. As telas de operacao entram nas proximas
        etapas.
      </p>
    </div>
  );
}
