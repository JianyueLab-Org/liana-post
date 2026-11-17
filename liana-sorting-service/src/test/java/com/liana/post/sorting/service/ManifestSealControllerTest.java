package com.liana.post.sorting.service;

import com.liana.post.common.dto.sorting.ManifestSealCommand;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.sorting.controller.ManifestSealController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManifestSealControllerTest {
    @Test
    void rejectsUnconfiguredMissingAndWrongCredentialsBeforeMutation() {
        var service = mock(ManifestSealService.class);
        var command = new ManifestSealCommand("S1", "M1", 1, "LN-OR-01", "clerk");
        assertEquals(401, assertThrows(BusinessException.class,
                () -> new ManifestSealController(service, "").seal(command, "")).getCode());
        var controller = new ManifestSealController(service, "shared-secret");
        assertThrows(BusinessException.class, () -> controller.seal(command, null));
        assertThrows(BusinessException.class, () -> controller.seal(command, "wrong"));
        verifyNoInteractions(service);
        controller.seal(command, "shared-secret");
        verify(service).seal(command);
    }
}
