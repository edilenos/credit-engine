import { PainelDoOperador } from "@/features/simulacao/painel-do-operador";

/**
 * Rota raiz — o Painel do Operador.
 *
 * A pagina em si e' Server Component e nao faz nada alem de montar o painel; a
 * interatividade toda mora no container, marcado com `"use client"`. O grid de
 * transacoes (PBI-37) entra em rota propria.
 */
export default function Home() {
  return <PainelDoOperador />;
}
