import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /**
   * Saida enxuta para container: o Next copia apenas o necessario, incluindo o
   * recorte de `node_modules` que a aplicacao de fato usa, e gera um
   * `server.js` proprio. A imagem final nao roda `pnpm install` nem carrega a
   * arvore de dependencias inteira.
   */
  output: "standalone",
};

export default nextConfig;
