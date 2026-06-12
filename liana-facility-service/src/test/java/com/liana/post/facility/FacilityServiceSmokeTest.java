package com.liana.post.facility;

import com.liana.post.facility.service.FacilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
public class FacilityServiceSmokeTest {
    @Autowired
    private FacilityService facilityService;

    @Test
    void shouldBootstrapDefaultFacilities() {
        assertFalse(facilityService.listFacilities().isEmpty());
    }
}