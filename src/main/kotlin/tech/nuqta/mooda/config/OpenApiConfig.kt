package tech.nuqta.mooda.config

import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.OpenAPI
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
        .externalDocs(
            ExternalDocumentation()
                .description("Project")
                .url("https://github.com/nuqta-tech/mooda")
        )
}