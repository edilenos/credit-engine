"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { ErroDaApi, ErroDeConexao } from "@/services/erros";
import { simular } from "@/services/simulacao-service";
import type { SimulacaoRequest, SimulacaoResponse } from "@/types/simulacao";

/**
 * Estado da simulacao para as telas.
 *
 * Nenhum componente de apresentacao chama `fetch`: consome este hook, que sabe
 * pedir, cancelar e classificar o erro. O criterio de aceite do PBI-25 exige
 * essa separacao, e o PBI-26 monta o formulario em cima dela.
 */

export interface EstadoDaSimulacao {
  resultado: SimulacaoResponse | null;
  carregando: boolean;
  erro: string | null;
  /** Erros por campo quando a API detalhou (400), para marcar o formulario. */
  camposInvalidos: Record<string, string>;
  executar: (requisicao: SimulacaoRequest) => void;
  limpar: () => void;
}

export function useSimulacao(): EstadoDaSimulacao {
  const [resultado, setResultado] = useState<SimulacaoResponse | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [camposInvalidos, setCamposInvalidos] = useState<Record<string, string>>({});

  const emVoo = useRef<AbortController | null>(null);

  // Desmontar com requisicao aberta deixaria o setState cair em componente
  // morto — sintoma classico de vazamento em tela que remonta rapido.
  useEffect(() => () => emVoo.current?.abort(), []);

  const executar = useCallback((requisicao: SimulacaoRequest) => {
    // A resposta obsoleta nao pode sobrescrever a mais recente: o operador
    // digita depressa, e sem isto o valor exibido seria o da penultima
    // requisicao sempre que a rede entregasse fora de ordem. Cancelar a
    // anterior resolve na origem, em vez de comparar carimbos na chegada.
    emVoo.current?.abort();

    const controlador = new AbortController();
    emVoo.current = controlador;

    setCarregando(true);
    setErro(null);
    setCamposInvalidos({});

    simular(requisicao, controlador.signal)
      .then((resposta) => {
        if (controlador.signal.aborted) return;
        setResultado(resposta);
      })
      .catch((causa: unknown) => {
        if (controlador.signal.aborted) return;

        if (causa instanceof ErroDaApi) {
          setErro(causa.message);
          setCamposInvalidos(causa.camposInvalidos);
          // Resultado antigo ao lado de erro novo faria o operador ler um numero
          // que nao corresponde mais ao formulario na tela.
          setResultado(null);
          return;
        }
        if (causa instanceof ErroDeConexao) {
          setErro(causa.message);
          setResultado(null);
          return;
        }
        setErro("Ocorreu um erro inesperado. Tente novamente.");
        setResultado(null);
      })
      .finally(() => {
        if (controlador.signal.aborted) return;
        setCarregando(false);
      });
  }, []);

  const limpar = useCallback(() => {
    emVoo.current?.abort();
    setResultado(null);
    setErro(null);
    setCamposInvalidos({});
    setCarregando(false);
  }, []);

  return { resultado, carregando, erro, camposInvalidos, executar, limpar };
}
