/**
 * Formato de erro da API — RFC 7807 (ProblemDetail).
 *
 * A API responde erro sempre neste formato, via `@RestControllerAdvice`.
 * O campo `campos` e' extensao nossa: aparece em 400 e lista todos os campos
 * invalidos de uma vez, nao so o primeiro.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  campos?: Record<string, string>;
  /**
   * Id que liga esta resposta a linha de log do servidor. Vem em toda resposta
   * de erro e tambem no cabecalho `X-Correlation-Id`.
   */
  correlationId?: string;
}

/** Nomes de moeda aceitos pelo sistema. Espelha a tabela `moeda` do seed. */
export type CodigoMoeda = "BRL" | "USD";

/** Convencoes de contagem de dias. Espelha o enum `ConvencaoContagem` da API. */
export type ConvencaoContagem =
  | "ACT_30"
  | "COMERCIAL_30_360"
  | "ACT_360"
  | "ACT_365"
  | "TAXA_DIARIA"
  | "BUS_252";
