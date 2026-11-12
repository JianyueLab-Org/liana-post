package com.liana.post.postage.tariff;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.time.Clock;
import java.time.Instant;

@Configuration
public class TariffConfiguration {
    @Bean
    public Clock tariffClock() { return Clock.systemUTC(); }

    @Bean
    public ApplicationRunner initialTariffImport(TariffService service, ObjectMapper json,
            @Value("${postage.seed-initial-tariff:false}") boolean enabled,
            @Value("${postage.initial-version-id}") String versionId,
            @Value("${postage.initial-effective-from}") String effectiveFrom) {
        return args -> {
            if (enabled) {
                try (var input = new ClassPathResource("initial-tariff.json").getInputStream()) {
                    service.seed(versionId, Instant.parse(effectiveFrom), json.readValue(input, TariffDefinition.class));
                }
            }
        };
    }
}
