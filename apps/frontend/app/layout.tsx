import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";

import { Cabecalho } from "@/components/layout/cabecalho";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "SRM Credit Engine",
  description:
    "Plataforma de cessao de credito multi-moeda para FIDC: precificacao, cambio e liquidacao.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    // lang="pt-BR" nao e' cosmetico: define a pronuncia em leitor de tela e a
    // heuristica de traducao automatica do navegador.
    <html
      lang="pt-BR"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
        <Cabecalho />
        <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">{children}</main>
      </body>
    </html>
  );
}
