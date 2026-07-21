import type { ConvencaoContagem } from "./api";

/** Espelha `TipoRecebivelResponse` da API. */
export interface TipoRecebivel {
  codigo: string;
  nome: string;
  /**
   * Spread cadastrado. Serve para o painel mostrar a taxa antes de simular —
   * o valor que vale e' o `spreadAplicado` que volta da simulacao, porque a
   * Strategy do produto pode ajusta-lo pelo contexto.
   */
  spread: number;
  periodicidade: "DIARIA" | "MENSAL" | "ANUAL";
  convencaoContagem: ConvencaoContagem;
}

/** Espelha `MoedaResponse` da API. */
export interface Moeda {
  codigo: string;
  nome: string;
  escalaPadrao: number;
}
