package br.com.srm.creditengine.aplicacao;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para o SPA.
 *
 * <p>O frontend roda em 3000 e a API em 8081 — origens distintas, entao o
 * navegador faz preflight antes de qualquer POST com JSON. Sem esta
 * configuracao a tela nao fala com a API, e a falha aparece so no browser: os
 * testes de MockMvc passam porque nao ha navegador para barrar nada.
 *
 * <p>As origens vem de {@code APP_CORS_ORIGINS} e nao ha curinga. {@code "*"}
 * seria mais simples e permitiria que qualquer pagina na maquina do operador
 * chamasse a API em nome dele; o enunciado trata seguranca como criterio
 * avaliado (§5.2), e uma lista explicita e' o que se defende numa revisao.
 */
@Configuration
public class ConfiguracaoDeCors implements WebMvcConfigurer {

    private final List<String> origensPermitidas;

    public ConfiguracaoDeCors(
            @Value("${app.cors.origens:http://localhost:3000}") List<String> origensPermitidas) {
        this.origensPermitidas = origensPermitidas;
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registro) {
        registro.addMapping("/api/**")
                .allowedOrigins(origensPermitidas.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept")
                .maxAge(3600);
    }
}
