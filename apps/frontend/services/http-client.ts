import type { ProblemDetail } from "@/types/api";
import { TIMEOUT_MS, urlDaApi } from "./config";
import { ErroDaApi, ErroDeConexao, mensagemDe } from "./erros";

/**
 * Cliente HTTP unico da aplicacao.
 *
 * Todo acesso a API passa por aqui. E' o que permite que timeout, tratamento de
 * erro e cabecalhos existam em um lugar so — o criterio de aceite pede
 * exatamente isso ("erro da API e' tratado em um lugar so e exposto de forma
 * tipada"), e a alternativa e' cada tela reinventar o `catch`.
 *
 * Componentes de apresentacao nao chamam este modulo diretamente: quem chama
 * sao os servicos por dominio, e as telas consomem hooks de `features/`.
 */

interface OpcoesDeRequisicao {
  /**
   * Sinal do chamador, para cancelar quando o componente desmonta ou quando uma
   * requisicao mais nova torna esta obsoleta.
   */
  signal?: AbortSignal;
}

async function requisitar<T>(
  caminho: string,
  init: RequestInit,
  opcoes: OpcoesDeRequisicao = {},
): Promise<T> {
  const sinais = [AbortSignal.timeout(TIMEOUT_MS)];
  if (opcoes.signal) {
    sinais.push(opcoes.signal);
  }

  let resposta: Response;
  try {
    resposta = await fetch(`${urlDaApi()}${caminho}`, {
      ...init,
      headers: { "Content-Type": "application/json", ...init.headers },
      signal: AbortSignal.any(sinais),
    });
  } catch (causa) {
    // Cancelamento pedido pelo chamador nao e' falha: quem cancelou sabe o que
    // fez, e transformar isso em erro na tela mostraria alarme a cada tecla.
    if (opcoes.signal?.aborted) {
      throw causa;
    }
    if (causa instanceof DOMException && causa.name === "TimeoutError") {
      throw new ErroDeConexao(
        "O servidor demorou demais para responder. Verifique a conexao e tente novamente.",
        causa,
      );
    }
    throw new ErroDeConexao(
      "Nao foi possivel falar com o servidor. Verifique se a API esta no ar.",
      causa,
    );
  }

  if (!resposta.ok) {
    const problema = await lerProblema(resposta);
    throw new ErroDaApi(resposta.status, problema, mensagemDe(resposta.status, problema));
  }

  if (resposta.status === 204) {
    return undefined as T;
  }
  return (await resposta.json()) as T;
}

/**
 * Le o corpo de erro sem deixar a leitura virar o erro reportado.
 *
 * Um 502 de proxy devolve HTML, nao JSON. Se o `json()` estourasse aqui, a tela
 * mostraria "Unexpected token < in JSON" no lugar do problema real.
 */
async function lerProblema(resposta: Response): Promise<ProblemDetail | null> {
  try {
    return (await resposta.json()) as ProblemDetail;
  } catch {
    return null;
  }
}

export function get<T>(caminho: string, opcoes?: OpcoesDeRequisicao): Promise<T> {
  return requisitar<T>(caminho, { method: "GET" }, opcoes);
}

export function post<T>(
  caminho: string,
  corpo: unknown,
  opcoes?: OpcoesDeRequisicao,
): Promise<T> {
  return requisitar<T>(caminho, { method: "POST", body: JSON.stringify(corpo) }, opcoes);
}
