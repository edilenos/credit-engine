"use client";

import { formatarDigitos, MAXIMO_DE_DIGITOS, somenteDigitos } from "@/lib/moeda-digitada";

/**
 * Campos de formulario — apresentacao pura.
 *
 * Nenhum deles busca dado ou conhece regra de negocio: recebem valor e
 * `onChange`, devolvem marcacao. O que decide quando simular e' o container em
 * `features/`.
 */

const rotuloBase = "block text-sm font-medium text-slate-700 dark:text-slate-300";
const controleBase =
  "mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm " +
  "shadow-sm outline-none focus:border-slate-500 focus:ring-1 focus:ring-slate-500 " +
  "dark:border-slate-700 dark:bg-slate-900 disabled:opacity-60";

function Erro({ mensagem }: { mensagem?: string }) {
  if (!mensagem) return null;
  return (
    <p role="alert" className="mt-1 text-xs text-red-600 dark:text-red-400">
      {mensagem}
    </p>
  );
}

interface CampoMonetarioProps {
  id: string;
  rotulo: string;
  /** Digitos de centavos, nao valor formatado. */
  digitos: string;
  prefixo: string;
  erro?: string;
  onChange: (digitos: string) => void;
}

export function CampoMonetario({
  id,
  rotulo,
  digitos,
  prefixo,
  erro,
  onChange,
}: CampoMonetarioProps) {
  return (
    <div>
      <label htmlFor={id} className={rotuloBase}>
        {rotulo}
      </label>
      <div className="relative">
        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm text-slate-500">
          {prefixo}
        </span>
        <input
          id={id}
          // inputMode numerico abre o teclado de digitos no celular; o type
          // continua "text" porque type="number" nao aceita mascara.
          inputMode="numeric"
          autoComplete="off"
          className={`${controleBase} tabular pl-12 text-right`}
          value={formatarDigitos(digitos)}
          onChange={(evento) =>
            onChange(somenteDigitos(evento.target.value).slice(0, MAXIMO_DE_DIGITOS))
          }
          placeholder="0,00"
          aria-invalid={erro ? true : undefined}
        />
      </div>
      <Erro mensagem={erro} />
    </div>
  );
}

interface CampoDataProps {
  id: string;
  rotulo: string;
  valor: string;
  minimo?: string;
  erro?: string;
  onChange: (valor: string) => void;
}

export function CampoData({ id, rotulo, valor, minimo, erro, onChange }: CampoDataProps) {
  return (
    <div>
      <label htmlFor={id} className={rotuloBase}>
        {rotulo}
      </label>
      <input
        id={id}
        type="date"
        className={controleBase}
        value={valor}
        min={minimo}
        onChange={(evento) => onChange(evento.target.value)}
        aria-invalid={erro ? true : undefined}
      />
      <Erro mensagem={erro} />
    </div>
  );
}

interface Opcao {
  valor: string;
  rotulo: string;
}

interface CampoSelecaoProps {
  id: string;
  rotulo: string;
  valor: string;
  opcoes: Opcao[];
  desabilitado?: boolean;
  erro?: string;
  onChange: (valor: string) => void;
}

export function CampoSelecao({
  id,
  rotulo,
  valor,
  opcoes,
  desabilitado,
  erro,
  onChange,
}: CampoSelecaoProps) {
  return (
    <div>
      <label htmlFor={id} className={rotuloBase}>
        {rotulo}
      </label>
      <select
        id={id}
        className={controleBase}
        value={valor}
        disabled={desabilitado}
        onChange={(evento) => onChange(evento.target.value)}
        aria-invalid={erro ? true : undefined}
      >
        <option value="">Selecione…</option>
        {opcoes.map((opcao) => (
          <option key={opcao.valor} value={opcao.valor}>
            {opcao.rotulo}
          </option>
        ))}
      </select>
      <Erro mensagem={erro} />
    </div>
  );
}
