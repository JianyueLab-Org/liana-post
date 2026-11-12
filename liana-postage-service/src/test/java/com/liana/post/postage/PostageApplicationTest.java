package com.liana.post.postage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("standalone")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostageApplicationTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void exposesHealthAndReadinessWithoutExternalInfrastructure() {
        ResponseEntity<String> health = http.getForEntity("/actuator/health", String.class);
        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertTrue(health.getBody().contains("\"status\":\"UP\""));
        assertFalse(health.getBody().contains("components"));

        ResponseEntity<String> readiness = http.getForEntity("/actuator/health/readiness", String.class);
        assertEquals(HttpStatus.OK, readiness.getStatusCode());
        assertTrue(readiness.getBody().contains("\"status\":\"UP\""));
    }

    @Test
    void doesNotExposeEnvironmentEndpoint() {
        ResponseEntity<String> environment = http.getForEntity("/actuator/env", String.class);
        assertEquals(HttpStatus.NOT_FOUND, environment.getStatusCode());
    }
}
