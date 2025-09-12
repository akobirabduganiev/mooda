package tech.nuqta.mooda.config

import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun moodaOpenAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Mooda API")
                .version("v1")
                .description("Reactive backend for mood journaling & stats (MVP)")
        )
        // Define JWT Bearer authentication for Swagger UI
        .components(
            io.swagger.v3.oas.models.Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            )
        )
        // Apply bearerAuth globally (public endpoints will still be accessible without token at runtime)
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
        .externalDocs(
            ExternalDocumentation()
                .description("Project")
                .url("https://github.com/nuqta-tech/mooda")
        )
}