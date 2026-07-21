"use client";

import { useEffect, useMemo, useState } from "react";

import { ResultadoDaSimulacao } from "@/components/simulacao/resultado-da-simulacao";
import { CampoData, CampoMonetario, CampoSelecao } from "@/components/ui/campos";
import { useCadastros } from "@/features/cadastros/use-cadastros";
import { useSimulacao } from "@/features/simulacao/use-simulacao";
import { digitosParaDecimal, ehPositivo } from "@/lib/moeda-digitada";
import { useDebounce } from "@/lib/use-debounce";
import type { CodigoMoeda } from "@/types/api";

/**
 * Painel do Operador (PBI-26).
 *
 * Container: guarda o estado do formulario, decide quando simular e passa dados
 * prontos para componentes de apresentacao. Nenhuma marcacao de resultado mora
 * aqui, e nenhum `fetch` tambem — quem fala com a API e' o `useSimulacao`.
 */

const ATRASO_DO_DEBOUNCE_MS = 400;

interface Formulario {
  digitosDoValor: string;
  dataVencimento: string;
  tipoRecebivel: string;
  moedaTitulo: string;
  moedaLiquidacao: string;
}

const INICIAL: Formulario = {
  digitosDoValor: "",
  dataVencimento: "",
  tipoRecebivel: "",
  moedaTitulo: "BRL",
  moedaLiquidacao: "BRL",
};

function hojeIso(): string {
  const agora = new Date();
  // Construir a partir das partes locais em vez de toISOString(): este ultimo
  // converte para UTC e devolveria o dia seguinte a partir das 21h no Brasil.
  const mes = String(agora.getMonth() + 1).padStart(2, "0");
  const dia = String(agora.getDate()).padStart(2, "0");
  return `${agora.getFullYear()}-${mes}-${dia}`;
}

export function PainelDoOperador() {
  const [formulario, setFormulario] = useState<Formulario>(INICIAL);
  const cadastros = useCadastros();
  const { resultado, carregando, erro, camposInvalidos, executar, limpar } = useSimulacao();

  const hoje = useMemo(() => hojeIso(), []);

  function alterar<C extends keyof Formulario>(campo: C, valor: Formulario[C]) {
    setFormulario((atual) => ({ ...atual, [campo]: valor }));
  }

  // Validacao local espelhando a do servidor. Ela existe para nao gastar viagem
  // com o que ja se sabe invalido — nao como fronteira de confianca: o cliente
  // nunca e uma, e a API valida tudo de novo.
  const problemas: Partial<Record<keyof Formulario, string>> = {};
  if (formulario.digitosDoValor !== "" && !ehPositivo(formulario.digitosDoValor)) {
    problemas.digitosDoValor = "Informe um valor maior que zero.";
  }
  if (formulario.dataVencimento !== "" && formulario.dataVencimento < hoje) {
    problemas.dataVencimento = "O vencimento nao pode ser anterior a hoje.";
  }

  const completo =
    ehPositivo(formulario.digitosDoValor) &&
    formulario.dataVencimento !== "" &&
    formulario.tipoRecebivel !== "" &&
    formulario.moedaTitulo !== "" &&
    formulario.moedaLiquidacao !== "";

  const valido = completo && Object.keys(problemas).length === 0;
  const formularioAtrasado = useDebounce(formulario, ATRASO_DO_DEBOUNCE_MS);

  useEffect(() => {
    const pronto =
      ehPositivo(formularioAtrasado.digitosDoValor) &&
      formularioAtrasado.dataVencimento !== "" &&
      formularioAtrasado.dataVencimento >= hoje &&
      formularioAtrasado.tipoRecebivel !== "" &&
      formularioAtrasado.moedaTitulo !== "" &&
      formularioAtrasado.moedaLiquidacao !== "";

    if (!pronto) {
      limpar();
      return;
    }

    executar({
      titulos: [
        {
          tipoRecebivel: formularioAtrasado.tipoRecebivel,
          valorFace: digitosParaDecimal(formularioAtrasado.digitosDoValor),
          dataVencimento: formularioAtrasado.dataVencimento,
        },
      ],
      moedaTitulo: formularioAtrasado.moedaTitulo as CodigoMoeda,
      moedaLiquidacao: formularioAtrasado.moedaLiquidacao as CodigoMoeda,
    });
  }, [formularioAtrasado, hoje, executar, limpar]);

  const opcoesDeMoeda = cadastros.moedas.map((m) => ({
    valor: m.codigo,
    rotulo: `${m.codigo} — ${m.nome}`,
  }));

  return (
    <div className="grid gap-8 lg:grid-cols-2">
      <section>
        <h1 className="text-xl font-semibold tracking-tight">Painel do Operador</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          A simulação atualiza sozinha a cada alteração válida. Nada é gravado.
        </p>

        {cadastros.erro && (
          <p
            role="alert"
            className="mt-4 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200"
          >
            {cadastros.erro}
          </p>
        )}

        <div className="mt-6 space-y-4">
          <CampoMonetario
            id="valor-de-face"
            rotulo="Valor de face"
            digitos={formulario.digitosDoValor}
            prefixo={formulario.moedaTitulo || "R$"}
            erro={problemas.digitosDoValor ?? camposInvalidos["titulos[0].valorFace"]}
            onChange={(digitos) => alterar("digitosDoValor", digitos)}
          />

          <CampoData
            id="data-de-vencimento"
            rotulo="Data de vencimento"
            valor={formulario.dataVencimento}
            minimo={hoje}
            erro={problemas.dataVencimento ?? camposInvalidos["titulos[0].dataVencimento"]}
            onChange={(valor) => alterar("dataVencimento", valor)}
          />

          <CampoSelecao
            id="tipo-de-recebivel"
            rotulo="Tipo de recebível"
            valor={formulario.tipoRecebivel}
            desabilitado={cadastros.carregando}
            opcoes={cadastros.tipos.map((tipo) => ({
              valor: tipo.codigo,
              rotulo: tipo.nome,
            }))}
            onChange={(valor) => alterar("tipoRecebivel", valor)}
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <CampoSelecao
              id="moeda-do-titulo"
              rotulo="Moeda do título"
              valor={formulario.moedaTitulo}
              desabilitado={cadastros.carregando}
              opcoes={opcoesDeMoeda}
              erro={camposInvalidos.moedaTitulo}
              onChange={(valor) => alterar("moedaTitulo", valor)}
            />
            <CampoSelecao
              id="moeda-de-liquidacao"
              rotulo="Moeda de liquidação"
              valor={formulario.moedaLiquidacao}
              desabilitado={cadastros.carregando}
              opcoes={opcoesDeMoeda}
              erro={camposInvalidos.moedaLiquidacao}
              onChange={(valor) => alterar("moedaLiquidacao", valor)}
            />
          </div>
        </div>
      </section>

      <section aria-live="polite" className="lg:pt-1">
        {erro && (
          <p
            role="alert"
            className="rounded-lg border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-900 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
          >
            {erro}
          </p>
        )}

        {!erro && resultado && (
          <ResultadoDaSimulacao resultado={resultado} desatualizado={carregando} />
        )}

        {!erro && !resultado && (
          <div className="rounded-lg border border-dashed border-slate-300 px-4 py-12 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
            {carregando
              ? "Simulando…"
              : valido
                ? "Simulando…"
                : "Preencha os campos para ver a precificação."}
          </div>
        )}

        {carregando && resultado && (
          <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">Atualizando…</p>
        )}
      </section>
    </div>
  );
}
