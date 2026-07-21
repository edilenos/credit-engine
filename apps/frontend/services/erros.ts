import type { ProblemDetail } from "@/types/api";

/**
 * Erro da API, ja traduzido para algo que se pode mostrar a um operador.
 *
 * O criterio de aceite pede mensagem em portugues, acionavel e sem stacktrace
 * nem codigo de status cru. Por isso a mensagem e' construida aqui, uma vez, e
 * nao em cada tela: repetir a traducao por componente garante que uma delas vai
 * vazar "Error 500" para a mesa.
 *
 * `problema` fica exposto para quem precisar do detalhe — o painel do operador
 * usa `camposInvalidos` para marcar o campo errado no formulario.
 */
export class ErroDaApi extends Error {
  constructor(
    readonly status: number,
    readonly problema: ProblemDetail | null,
    mensagem: string,
  ) {
    super(mensagem);
    this.name = "ErroDaApi";
  }

  /** Erros por campo, quando a API os detalhou (400). */
  get camposInvalidos(): Record<string, string> {
    return this.problema?.campos ?? {};
  }

  /** Violacao de regra de negocio — o pedido faz sentido mas o dominio recusou. */
  get isRegraDeNegocio(): boolean {
    return this.status === 422;
  }
}

/** A requisicao nao chegou a ter resposta: rede caida, CORS, timeout. */
export class ErroDeConexao extends Error {
  constructor(mensagem: string, readonly causa?: unknown) {
    super(mensagem);
    this.name = "ErroDeConexao";
  }
}

/**
 * Constroi a mensagem exibivel a partir da resposta.
 *
 * A API ja devolve `detail` em portugues para 404 e 422 — sao mensagens de
 * dominio escritas para serem lidas. Para 400 a mensagem util esta nos campos,
 * e para 5xx nada do corpo serve: detalhe de falha interna nao ajuda o operador
 * e pode vazar estrutura.
 */
export function mensagemDe(status: number, problema: ProblemDetail | null): string {
  if (status >= 500) {
    return "O servico esta indisponivel no momento. Tente novamente em instantes.";
  }

  if (status === 400) {
    const campos = Object.entries(problema?.campos ?? {});
    if (campos.length > 0) {
      return campos.map(([campo, erro]) => `${campo}: ${erro}`).join("; ");
    }
    return problema?.detail ?? "Requisicao invalida.";
  }

  return problema?.detail ?? problema?.title ?? "Nao foi possivel completar a operacao.";
}
