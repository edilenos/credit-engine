/**
 * Configuracao de ambiente.
 *
 * A URL da API nao tem valor padrao de propostio. Um fallback para
 * `http://localhost:8081` faria a aplicacao "funcionar" na maquina de quem
 * escreveu e falhar em qualquer outro lugar, com o sintoma aparecendo longe da
 * causa. Sem padrao, a configuracao ausente se anuncia na primeira requisicao.
 *
 * A leitura e' feita na chamada, nao no import: `NEXT_PUBLIC_*` e' substituida
 * em tempo de build, e falhar no topo do modulo quebraria `next build` em vez
 * de quebrar a requisicao.
 */

export class ConfiguracaoAusenteError extends Error {
  constructor(variavel: string) {
    super(
      `Variavel de ambiente ${variavel} nao configurada. ` +
        `Copie .env.example para .env.local e ajuste o valor.`,
    );
    this.name = "ConfiguracaoAusenteError";
  }
}

export function urlDaApi(): string {
  const url = process.env.NEXT_PUBLIC_API_URL;

  if (!url) {
    throw new ConfiguracaoAusenteError("NEXT_PUBLIC_API_URL");
  }
  return url.replace(/\/+$/, "");
}

/**
 * Teto de espera de uma requisicao.
 *
 * Precificar um lote de 500 titulos faz 500 potenciacoes decimais em precisao
 * arbitraria; 10 segundos da folga confortavel para isso e ainda assim evita
 * que a interface fique presa indefinidamente quando a API nao responde.
 */
export const TIMEOUT_MS = 10_000;
