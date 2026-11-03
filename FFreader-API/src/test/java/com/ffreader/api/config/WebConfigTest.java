package com.ffreader.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    /** Exposes the registry's protected result. */
    private static final class InspectableRegistry extends CorsRegistry {
        CorsConfiguration apiConfiguration() {
            return getCorsConfigurations().get("/api/**");
        }
    }

    @Test
    void allowsTheConfiguredOriginsOnTheApi() {
        CorsConfiguration cors = corsFor(new FfreaderProperties.Cors("http://a.test,http://b.test"));

        assertThat(cors.getAllowedOrigins()).containsExactly("http://a.test", "http://b.test");
        assertThat(cors.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
    }

    @Test
    void defaultsToTheLocalUiWithoutCorsSettings() {
        assertThat(corsFor(null).getAllowedOrigins()).containsExactly("http://localhost:4200");
    }

    @Test
    void toleratesSpacesBetweenOrigins() {
        CorsConfiguration cors = corsFor(new FfreaderProperties.Cors("http://a.test, http://b.test"));

        assertThat(cors.getAllowedOrigins()).containsExactly("http://a.test", "http://b.test");
    }

    @Test
    void ignoresEmptyEntriesInTheOriginList() {
        CorsConfiguration cors = corsFor(new FfreaderProperties.Cors("http://a.test,, "));

        assertThat(cors.getAllowedOrigins()).containsExactly("http://a.test");
    }

    private static CorsConfiguration corsFor(FfreaderProperties.Cors cors) {
        InspectableRegistry registry = new InspectableRegistry();
        new WebConfig(new FfreaderProperties(null, cors, null)).addCorsMappings(registry);
        return registry.apiConfiguration();
    }
}
