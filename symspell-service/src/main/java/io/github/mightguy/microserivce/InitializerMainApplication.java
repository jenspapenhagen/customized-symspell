package io.github.mightguy.microserivce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the spellcheck microservice.
 *
 * @author MighTguY
 * @since 6.7
 */
@SpringBootApplication
public class InitializerMainApplication {

    /**
     * Creates the application entry point. Spring Boot instantiates and configures the context.
     */
    public InitializerMainApplication() {
        // configured by Spring
    }

    /**
     * Starts the Spring Boot application.
     *
     * @param args command line arguments passed to Spring Boot
     */
    public static void main(final String[] args) {
        SpringApplication.run(InitializerMainApplication.class, args);
    }
}
