/**
 * Campo monetario em pt-BR, sem ponto flutuante em nenhum passo.
 *
 * O estado do campo e' a sequencia de **digitos de centavos** que o operador
 * digitou: teclar `10000000` produz `100.000,00`. E' o comportamento de campo
 * monetario a que a mesa esta acostumada, e resolve de graca a ambiguidade de
 * separador — nao ha como digitar ponto ou virgula no lugar errado.
 *
 * Todas as transformacoes sao textuais. Passar por `Number` reintroduziria na
 * borda do formulario exatamente o erro que o backend evita usando
 * `BigDecimal`: `Number("0.1") + Number("0.2")` ja e' 0,30000000000000004, e
 * valores de face nesta aplicacao chegam a treze digitos.
 */

interface Partes {
  inteiro: string;
  centavos: string;
}

function partes(digitos: string): Partes {
  const preenchido = somenteDigitos(digitos).padStart(3, "0");
  return {
    // Remove zeros a esquerda mas preserva o unico zero de "0,05".
    inteiro: preenchido.slice(0, -2).replace(/^0+(?=\d)/, ""),
    centavos: preenchido.slice(-2),
  };
}

export function somenteDigitos(entrada: string): string {
  return entrada.replace(/\D/g, "");
}

/** `10000000` vira `100.000,00`. String vazia continua vazia. */
export function formatarDigitos(digitos: string): string {
  const limpo = somenteDigitos(digitos);
  if (limpo === "") return "";

  const { inteiro, centavos } = partes(limpo);
  // Agrupamento por regex, nao por toLocaleString: o segundo exige converter
  // para Number, e treze digitos ja passam do inteiro seguro.
  const agrupado = inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${agrupado},${centavos}`;
}

/** `10000000` vira `100000.00`, que e' o que a API espera. */
export function digitosParaDecimal(digitos: string): string {
  const limpo = somenteDigitos(digitos);
  if (limpo === "") return "";

  const { inteiro, centavos } = partes(limpo);
  return `${inteiro}.${centavos}`;
}

/** Ha valor e ele e' maior que zero. */
export function ehPositivo(digitos: string): boolean {
  const limpo = somenteDigitos(digitos);
  return limpo !== "" && /[1-9]/.test(limpo);
}

/**
 * Teto de digitos aceitos.
 *
 * A coluna e' `NUMERIC(19,2)`: dezessete inteiros mais dois centavos. Barrar na
 * digitacao evita a viagem que voltaria 400.
 */
export const MAXIMO_DE_DIGITOS = 19;
