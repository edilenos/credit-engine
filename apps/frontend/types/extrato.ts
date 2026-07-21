import type { CodigoMoeda } from "./api";

/**
 * Contratos de `GET /api/v1/relatorios/extrato-liquidacao`.
 *
 * Espelham `LinhaDoExtrato` e `PaginaDoExtrato` da API. Vale aqui a mesma
 * observacao dos tipos de simulacao: valores monetarios chegam como numero
 * JSON, e o cliente **nao faz aritmetica com eles** — apenas formata.
 */

/** Colunas aceitas pela API. Lista branca, espelhando o enum do servidor. */
export const ORDENACOES = [
  "LIQUIDADO_EM",
  "VALOR_LIQUIDADO",
  "CEDENTE",
  "OPERACAO",
] as const;

export type Ordenacao = (typeof ORDENACOES)[number];
export type Direcao = "ASC" | "DESC";

export interface LinhaDoExtrato {
  liquidacaoId: number;
  operacaoId: number;
  liquidadoEm: string;
  liquidadoPor: string;
  documentoCedente: string;
  razaoSocialCedente: string;
  moedaTitulo: CodigoMoeda;
  moedaLiquidacao: CodigoMoeda;
  valorFaceTotal: number;
  valorPresenteTotal: number;
  desagioTotal: number;
  valorLiquidado: number;
  /** `null` em operacao de moeda unica. */
  cotacaoAplicada: number | null;
  operacaoCriadaEm: string;
}

export interface PaginaDoExtrato {
  conteudo: LinhaDoExtrato[];
  /** Total para o filtro, nao o tamanho da pagina. */
  totalDeItens: number;
  pagina: number;
  tamanho: number;
  totalDePaginas: number;
  temProxima: boolean;
}

/** Filtros aceitos. Todos opcionais e combinaveis. */
export interface FiltroDoExtrato {
  de?: string;
  ate?: string;
  documentoCedente?: string;
  moedaLiquidacao?: string;
  ordenarPor?: Ordenacao;
  direcao?: Direcao;
  pagina: number;
  tamanho: number;
}

/** Mesmo teto da API. Repetido para o cliente nao pedir o que sera cortado. */
export const TAMANHO_MAXIMO = 100;
export const TAMANHO_PADRAO = 20;
