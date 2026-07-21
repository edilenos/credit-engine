import type { SimulacaoRequest, SimulacaoResponse } from "@/types/simulacao";
import { post } from "./http-client";

/**
 * Acesso ao endpoint de simulacao (PBI-24).
 *
 * Camada fina de proposito: o mapeamento entre o contrato da API e o modelo da
 * tela mora aqui, e nao no componente. Quando o contrato mudar, muda este
 * arquivo — nenhuma tela precisa saber o formato do payload.
 */
export function simular(
  requisicao: SimulacaoRequest,
  signal?: AbortSignal,
): Promise<SimulacaoResponse> {
  return post<SimulacaoResponse>("/api/v1/simulacoes", requisicao, { signal });
}
