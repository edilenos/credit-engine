import type { FiltroDoExtrato, PaginaDoExtrato } from "@/types/extrato";
import { get } from "./http-client";

/**
 * Extrato de liquidacao (PBI-35 na API).
 *
 * <p>A paginacao acontece no servidor. Este modulo nunca recebe a colecao
 * inteira, entao nao ha o que fatiar no cliente — trocar de pagina e' outra
 * requisicao, com outro `pagina`.
 */
export function consultarExtrato(
  filtro: FiltroDoExtrato,
  signal?: AbortSignal,
): Promise<PaginaDoExtrato> {
  const query = new URLSearchParams();

  // Campo vazio nao vira parametro: `?documentoCedente=` filtraria por string
  // vazia e devolveria zero linhas sem motivo aparente.
  const opcionais: Array<[string, string | undefined]> = [
    ["de", filtro.de],
    ["ate", filtro.ate],
    ["documentoCedente", filtro.documentoCedente],
    ["moedaLiquidacao", filtro.moedaLiquidacao],
    ["ordenarPor", filtro.ordenarPor],
    ["direcao", filtro.direcao],
  ];

  for (const [chave, valor] of opcionais) {
    if (valor) query.set(chave, valor);
  }

  query.set("pagina", String(filtro.pagina));
  query.set("tamanho", String(filtro.tamanho));

  return get<PaginaDoExtrato>(
    `/api/v1/relatorios/extrato-liquidacao?${query.toString()}`,
    { signal },
  );
}
