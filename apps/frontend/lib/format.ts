import type { CodigoMoeda } from "@/types/api";

/**
 * Formatacao pt-BR, centralizada.
 *
 * Repetir `toLocaleString` por componente e' como a interface acaba com tres
 * jeitos de escrever o mesmo valor. Aqui tambem fica a garantia de que moeda
 * nunca aparece sem simbolo: numero solto numa tela que mistura BRL e USD e'
 * ambiguo justamente onde a ambiguidade custa caro.
 */

const formatadores = new Map<string, Intl.NumberFormat>();

function formatador(chave: string, opcoes: Intl.NumberFormatOptions): Intl.NumberFormat {
  // Construir Intl.NumberFormat e' caro, e o grid do PBI-37 formata milhares de
  // celulas por pagina.
  let existente = formatadores.get(chave);
  if (!existente) {
    existente = new Intl.NumberFormat("pt-BR", opcoes);
    formatadores.set(chave, existente);
  }
  return existente;
}

/** `1234.5` em BRL vira `R$ 1.234,50`. */
export function moeda(valor: number, codigo: CodigoMoeda): string {
  return formatador(`moeda-${codigo}`, {
    style: "currency",
    currency: codigo,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(valor);
}

/**
 * Taxa decimal vira percentual: `0.015` em `1,5000%`.
 *
 * Quatro casas porque spread e taxa base chegam com escala 6 e a diferenca
 * entre 1,5% e 1,5025% e' dinheiro — arredondar para 1,5% esconderia o
 * parametro que o operador esta conferindo.
 */
export function percentual(taxa: number): string {
  return formatador("percentual", {
    style: "percent",
    minimumFractionDigits: 4,
    maximumFractionDigits: 4,
  }).format(taxa);
}

/** Cotacao cambial, com as seis casas em que a API a mantem. */
export function cotacao(valor: number): string {
  return formatador("cotacao", {
    minimumFractionDigits: 6,
    maximumFractionDigits: 6,
  }).format(valor);
}

/**
 * `2026-09-04` vira `04/09/2026`.
 *
 * A data chega como `LocalDate` da API — sem hora e sem fuso. Passar por
 * `new Date("2026-09-04")` a interpretaria como UTC meia-noite e exibiria o dia
 * anterior em qualquer fuso a oeste de Greenwich, que inclui o Brasil inteiro.
 * Por isso a conversao e' textual.
 */
export function data(isoLocalDate: string): string {
  const [ano, mes, dia] = isoLocalDate.split("-");
  return `${dia}/${mes}/${ano}`;
}

/** Numero puro com duas casas, para expoentes e quantidades. */
export function decimal(valor: number, casas = 2): string {
  return formatador(`decimal-${casas}`, {
    minimumFractionDigits: casas,
    maximumFractionDigits: casas,
  }).format(valor);
}
