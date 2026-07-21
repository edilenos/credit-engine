"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useMemo } from "react";

import {
  ORDENACOES,
  TAMANHO_MAXIMO,
  TAMANHO_PADRAO,
  type Direcao,
  type FiltroDoExtrato,
  type Ordenacao,
} from "@/types/extrato";

/**
 * O filtro do grid mora na URL, nao em estado local.
 *
 * <p>E' requisito do PBI-37, e a razao e' pratica: filtro em `useState` morre no
 * reload e nao pode ser colado num chat. Na URL, a mesma busca vira link — que
 * e' como uma pessoa da mesa manda "olha essa operacao" para outra.
 *
 * <p>Foi tambem o argumento que dispensou biblioteca de estado global: o estado
 * do grid nao e' client state, e' endereco.
 *
 * <h2>replace, nao push</h2>
 *
 * Trocar de pagina ou de filtro usa {@code router.replace}. Com {@code push},
 * cada tecla digitada num campo empilharia uma entrada no historico, e o botao
 * Voltar levaria o usuario letra por letra de volta em vez de sair da tela.
 */
export interface EstadoDoFiltro {
  filtro: FiltroDoExtrato;
  /** Aplica mudancas e volta para a primeira pagina. */
  aplicar: (mudancas: Partial<FiltroDoExtrato>) => void;
  /** Muda so a pagina, preservando os filtros. */
  irParaPagina: (pagina: number) => void;
  limpar: () => void;
  temFiltroAtivo: boolean;
}

function comoOrdenacao(valor: string | null): Ordenacao | undefined {
  return ORDENACOES.includes(valor as Ordenacao) ? (valor as Ordenacao) : undefined;
}

function comoDirecao(valor: string | null): Direcao | undefined {
  return valor === "ASC" || valor === "DESC" ? valor : undefined;
}

function comoInteiro(valor: string | null, padrao: number, minimo: number, maximo: number): number {
  const numero = Number(valor);
  if (!Number.isInteger(numero)) return padrao;
  return Math.min(Math.max(numero, minimo), maximo);
}

export function useFiltroNaUrl(): EstadoDoFiltro {
  const parametros = useSearchParams();
  const router = useRouter();
  const caminho = usePathname();

  const filtro = useMemo<FiltroDoExtrato>(() => {
    // Tudo que vem da URL e' entrada do usuario: qualquer pessoa edita a barra
    // de enderecos. Valores fora do esperado caem no padrao em vez de irem
    // para a API produzir um 422 que a tela nao sabe explicar.
    const texto = (chave: string) => parametros.get(chave)?.trim() || undefined;

    return {
      de: texto("de"),
      ate: texto("ate"),
      documentoCedente: texto("documentoCedente"),
      moedaLiquidacao: texto("moedaLiquidacao"),
      ordenarPor: comoOrdenacao(parametros.get("ordenarPor")),
      direcao: comoDirecao(parametros.get("direcao")),
      pagina: comoInteiro(parametros.get("pagina"), 0, 0, Number.MAX_SAFE_INTEGER),
      tamanho: comoInteiro(parametros.get("tamanho"), TAMANHO_PADRAO, 1, TAMANHO_MAXIMO),
    };
  }, [parametros]);

  const navegar = useCallback(
    (mudancas: Partial<FiltroDoExtrato>) => {
      const proximos = new URLSearchParams(parametros.toString());

      for (const [chave, valor] of Object.entries(mudancas)) {
        if (valor === undefined || valor === "" || valor === null) {
          proximos.delete(chave);
        } else {
          proximos.set(chave, String(valor));
        }
      }

      // `pagina=0` e `tamanho` padrao ficam implicitos: URL curta e' mais facil
      // de compartilhar, e o parser acima ja assume esses valores.
      if (proximos.get("pagina") === "0") proximos.delete("pagina");
      if (proximos.get("tamanho") === String(TAMANHO_PADRAO)) proximos.delete("tamanho");

      const consulta = proximos.toString();
      router.replace(consulta ? `${caminho}?${consulta}` : caminho, { scroll: false });
    },
    [parametros, router, caminho],
  );

  const aplicar = useCallback(
    (mudancas: Partial<FiltroDoExtrato>) => {
      // Voltar para a primeira pagina nao e' cosmetico: filtrar estando na
      // pagina 12 de um resultado que agora tem 3 mostraria uma tabela vazia,
      // e o usuario leria isso como "nao ha nada" em vez de "mudei o filtro".
      navegar({ ...mudancas, pagina: 0 });
    },
    [navegar],
  );

  const irParaPagina = useCallback(
    (pagina: number) => navegar({ pagina: Math.max(pagina, 0) }),
    [navegar],
  );

  const limpar = useCallback(() => router.replace(caminho, { scroll: false }), [router, caminho]);

  const temFiltroAtivo = Boolean(
    filtro.de || filtro.ate || filtro.documentoCedente || filtro.moedaLiquidacao,
  );

  return { filtro, aplicar, irParaPagina, limpar, temFiltroAtivo };
}
