package com.tfg.kanban.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos de la documentacion OpenAPI y declaracion del esquema de seguridad Bearer/JWT. */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI kanbanOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de PlanifIA")
                        .description("""
                                API de la herramienta de gestion colaborativa de proyectos universitarios.
                                Tableros Kanban, equipos y planificacion automatica de tareas con IA.
                                """)
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact().name("TFG - Gestion de proyectos universitarios"))
                        .license(new License().name("Uso academico")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT obtenido en el endpoint de login")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
