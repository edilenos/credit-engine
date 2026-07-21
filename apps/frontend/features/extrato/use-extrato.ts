"use client";

import { useEffect, useRef, useState } from "react";

import { ErroDaApi, ErroDeConexao } from "@/services/erros";
import { consultarExtrato } from "@/services/extrato-service";
import type { FiltroDoExtrato, PaginaDoExtrato } from "@/types/extrato";

/**
 * Busca a pagina do extrato correspondente ao filtro.
 *
 * <p>Nenhum componente de apresentacao chama `fetch`: a tabela recebe dados
 * prontos por prop, e quem sabe pedir, cancelar e classificar o erro e' este
 * hook — mesma divisao do `useSimulacao`.
 *
 * <h2>`carregando` e derivado, nao armazenado</h2>
 *
 * O resultado guardado carrega a <b>chave</b> do filtro que o produziu, e
 * "carregando" e' simplesmente `chave pedida !== chave carregada`. Alem de
 * dispensar o `setState` sincrono no corpo do efeito — que o lint recusa,
 * porque encadeia renders —, isso resolve de graca o problema de fundo: uma
 * resposta so e' exibida se corresponder ao filtro atual.
 *
 * <p>Trocar de pagina depressa dispara varias buscas, e a rede nao garante
 * ordem de chegada. Sem essa correspondencia, a resposta da pagina 2 poderia
 * pousar depois da pagina 3 e a tabela mostraria conteudo que nao bate com o
 * numero exibido — sem erro nenhum, que e' o pior tipo de bug de grid.
 */
export interface EstadoDoExtrato {
  pagina: PaginaDoExtrato | null;
  carregando: boolean;
  erro: string | null;
}

interface Resultado {
  chave: string;
  pagina: PaginaDoExtrato | null;
  erro: string | null;
}

/** Identidade do filtro. Duas buscas com a mesma chave pedem a mesma coisa. */
function chaveDe(filtro: FiltroDoExtrato): string {
  return JSON.stringify([
    filtro.de ?? "",
    filtro.ate ?? "",
    filtro.documentoCedente ?? "",
    filtro.moedaLiquidacao ?? "",
    filtro.ordenarPor ?? "",
    filtro.direcao ?? "",
    filtro.pagina,
    filtro.tamanho,
  ]);
}

export function useExtrato(filtro: FiltroDoExtrato): EstadoDoExtrato {
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const emVoo = useRef<AbortController | null>(null);

  const chave = chaveDe(filtro);

  useEffect(() => {
    emVoo.current?.abort();

    const controlador = new AbortController();
    emVoo.current = controlador;

    consultarExtrato(filtro, controlador.signal)
      .then((resposta) => {
        if (controlador.signal.aborted) return;
        setResultado({ chave, pagina: resposta, erro: null });
      })
      .catch((causa: unknown) => {
        if (controlador.signal.aborted) return;

        const mensagem =
          causa instanceof ErroDaApi || causa instanceof ErroDeConexao
            ? causa.message
            : "Ocorreu um erro inesperado ao consultar o extrato.";

        // Resultado antigo ao lado de erro novo faria o operador ler linhas que
        // nao correspondem mais ao filtro na tela.
        setResultado({ chave, pagina: null, erro: mensagem });
      });

    return () => controlador.abort();
    // `chave` cobre todos os campos do filtro; `filtro` e' recriado a cada
    // render pelo hook de URL, e depender dele buscaria uma vez por render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chave]);

  const atual = resultado?.chave === chave ? resultado : null;

  // Enquanto a busca nova nao chega, a tabela anterior continua na tela,
  // esmaecida pelo `desatualizado`. Esvaziar e reencher a cada troca de pagina
  // faria o conteudo piscar e o layout saltar a cada clique.
  //
  // O erro anterior NAO e' carregado adiante: ele pertencia ao filtro antigo, e
  // mante-lo faria a tela acusar falha de uma busca que ja foi substituida.
  const paginaExibida = atual?.pagina ?? resultado?.pagina ?? null;

  return {
    pagina: atual?.erro ? null : paginaExibida,
    carregando: atual === null,
    erro: atual?.erro ?? null,
  };
}
