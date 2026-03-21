package com.pesaloop.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI configuration.
 *
 * Access the interactive docs at:
 *   http://localhost:8080/swagger-ui.html
 *   http://localhost:8080/v3/api-docs        (raw JSON)
 *
 * All endpoints require a Bearer JWT. Use POST /api/v1/auth/login to obtain one,
 * then click "Authorize" and paste the token.
 *
 * Multi-tenant note: every request must include the X-Group-Slug header.
 * The group slug identifies which chamaa you are acting on behalf of.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI pesaLoopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PesaLoop API")
                        .description("""
                                Multi-tenant SaaS for Kenyan savings groups (Chamaas / SACCOs).
                                
                                **Authentication**: All endpoints (except /auth/login and /auth/register) \
                                require a Bearer JWT obtained from POST /api/v1/auth/login.
                                
                                **Multi-tenancy**: Every request must include the **X-Group-Slug** header \
                                identifying the group (e.g. `watiri-investment-club`). \
                                The system enforces data isolation — a token for group A \
                                cannot access group B data.
                                
                                **Roles**: ADMIN · TREASURER · SECRETARY · MEMBER · AUDITOR
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PesaLoop Support")
                                .email("dev@pesaloop.app")
                                .url("https://pesaloop.app"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://pesaloop.app/terms")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development"),
                        new Server()
                                .url("https://api.pesaloop.app")
                                .description("Production")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token from POST /api/v1/auth/login")));
    }
}
