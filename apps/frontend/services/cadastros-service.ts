import type { Moeda, TipoRecebivel } from "@/types/cadastros";
import { get } from "./http-client";

/** Produtos disponiveis para nova operacao. Desativados nao vem. */
export function listarTiposDeRecebivel(signal?: AbortSignal): Promise<TipoRecebivel[]> {
  return get<TipoRecebivel[]>("/api/v1/cadastros/tipos-recebivel", { signal });
}

export function listarMoedas(signal?: AbortSignal): Promise<Moeda[]> {
  return get<Moeda[]>("/api/v1/cadastros/moedas", { signal });
}
