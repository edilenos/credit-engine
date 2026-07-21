"use client";

import { useEffect, useState } from "react";

/**
 * Atrasa a propagacao de um valor ate ele parar de mudar.
 *
 * Sem isto, o painel dispararia uma simulacao por tecla: digitar
 * `100000` sao seis requisicoes, cinco delas ja obsoletas quando chegam. O
 * cancelamento em `useSimulacao` protege a *correcao* do que e' exibido; o
 * debounce protege o servidor de trabalho jogado fora.
 */
export function useDebounce<T>(valor: T, atrasoMs: number): T {
  const [atrasado, setAtrasado] = useState(valor);

  useEffect(() => {
    const temporizador = setTimeout(() => setAtrasado(valor), atrasoMs);
    return () => clearTimeout(temporizador);
  }, [valor, atrasoMs]);

  return atrasado;
}
