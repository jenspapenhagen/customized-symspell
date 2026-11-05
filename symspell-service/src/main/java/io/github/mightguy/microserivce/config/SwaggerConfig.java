package io.github.mightguy.microserivce.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * Exposes the OpenAPI metadata for the spellcheck service.
 *
 * @author MighTguY
 * @since 6.7
 */
public class SwaggerConfig {

    @Bean
    /**
     * Builds the OpenAPI description of the service.
     *
     * @return the OpenAPI metadata
     */
    public OpenAPI spellcheckOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spellcheck Service")
                        .description("Spellcheck and edit distance API")
                        .version("6.7-SNAPSHOT")
                        .contact(new Contact().name("MighTguY")));
    }
}