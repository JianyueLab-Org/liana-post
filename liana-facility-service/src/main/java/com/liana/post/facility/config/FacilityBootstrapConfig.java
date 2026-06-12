package com.liana.post.facility.config;

import com.liana.post.facility.service.FacilityService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FacilityBootstrapConfig {
    @Bean
    @ConditionalOnProperty(prefix = "facility.bootstrap", name = "enabled", havingValue = "true")
    public CommandLineRunner facilitySeeder(FacilityService facilityService) {
        return args -> facilityService.bootstrapDefaults();
    }
}