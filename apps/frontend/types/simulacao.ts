import type { CodigoMoeda, ConvencaoContagem } from "./api";

/**
 * Contratos de `POST /api/v1/simulacoes`.
 *
 * Espelham os records `SimularRequest` e `SimulacaoResponse` da API.
 *
 * ## Sobre `number` nos campos monetarios
 *
 * A API produz `BigDecimal` e o Jackson serializa como **numero JSON**
 * (conferido no corpo cru: `"valorPresente":96284.58`). `JSON.parse` transforma
 * isso em `double`, entao a precisao arbitraria do backend termina na borda.
 *
 * Para os valores desta aplicacao isso e' exato na exibicao: `double` tem ~15
 * digitos significativos e a coluna e' `NUMERIC(19,2)`, entao ate a casa de
 * 1e13 a formatacao com duas decimais devolve o numero original. Acima disso
 * comeca a divergir.
 *
 * Tipar como `number` e' honesto quanto ao que chega. O que **nao** se faz e'
 * aritmetica aqui: totais e desagios vem calculados da API, e o cliente so
 * formata. Somar no navegador reintroduziria o erro que o backend evitou.
 */

export interface TituloParaSimular {
  tipoRecebivel: string;
  /** Enviado como string para nao perder a escala no caminho de ida. */
  valorFace: string;
  dataVencimento: string;
}

export interface SimulacaoRequest {
  titulos: TituloParaSimular[];
  moedaTitulo: CodigoMoeda;
  moedaLiquidacao: CodigoMoeda;
  /** Ausente significa hoje, decidido pela API. */
  dataOperacao?: string;
}

export interface ItemSimulado {
  indice: number;
  valorFace: number;
  valorPresente: number;
  desagio: number;
  taxaBaseAplicada: number;
  spreadAplicado: number;
  taxaTotal: number;
  convencaoAplicada: ConvencaoContagem;
  expoenteAplicado: number;
}

export interface SimulacaoResponse {
  itens: ItemSimulado[];
  valorFaceTotal: number;
  valorPresenteTotal: number;
  desagioTotal: number;
  moedaTitulo: CodigoMoeda;
  moedaLiquidacao: CodigoMoeda;
  valorLiquidacao: number;
  /**
   * `null` em operacao de moeda unica — a API emite a chave com valor nulo, nao
   * a omite. Tipar como opcional deixaria `cotacaoAplicada!` compilar e quebrar
   * em runtime.
   */
  cotacaoAplicada: number | null;
  crossCurrency: boolean;
  dataOperacao: string;
}

/** Teto de lote da API. Repetido aqui para avisar antes de gastar a viagem. */
export const LOTE_MAXIMO = 500;
