"use client";

import { useEffect, useState } from "react";

import { listarMoedas, listarTiposDeRecebivel } from "@/services/cadastros-service";
import type { Moeda, TipoRecebivel } from "@/types/cadastros";

/**
 * Dados de referencia dos seletores.
 *
 * Sao os dados de servidor que a analise de estado global identificou: mudam
 * raramente, ninguem os edita pela tela, e por isso um `useState` no topo
 * resolve. Foi por casos assim que nao entrou biblioteca de estado global —
 * ver o README.
 */
export interface EstadoDosCadastros {
  tipos: TipoRecebivel[];
  moedas: Moeda[];
  carregando: boolean;
  erro: string | null;
}

export function useCadastros(): EstadoDosCadastros {
  const [tipos, setTipos] = useState<TipoRecebivel[]>([]);
  const [moedas, setMoedas] = useState<Moeda[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    const controlador = new AbortController();

    Promise.all([
      listarTiposDeRecebivel(controlador.signal),
      listarMoedas(controlador.signal),
    ])
      .then(([tiposCarregados, moedasCarregadas]) => {
        if (controlador.signal.aborted) return;
        setTipos(tiposCarregados);
        setMoedas(moedasCarregadas);
      })
      .catch((causa: unknown) => {
        if (controlador.signal.aborted) return;
        setErro(
          causa instanceof Error
            ? causa.message
            : "Nao foi possivel carregar os dados de cadastro.",
        );
      })
      .finally(() => {
        if (controlador.signal.aborted) return;
        setCarregando(false);
      });

    return () => controlador.abort();
  }, []);

  return { tipos, moedas, carregando, erro };
}
