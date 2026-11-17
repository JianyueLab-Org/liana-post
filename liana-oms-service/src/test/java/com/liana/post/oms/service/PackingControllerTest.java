package com.liana.post.oms.service;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.oms.controller.PackingController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackingControllerTest {
    @Test
    void missingInvalidAndUnconfiguredServiceCredentialsCannotWritePacking() {
        var service = mock(PackingConfirmationService.class);
        var controller = new PackingController(service, "test-token");
        assertEquals(401, assertThrows(BusinessException.class, () -> controller.confirm(null, null)).getCode());
        assertEquals(401, assertThrows(BusinessException.class, () -> controller.confirm(null, "wrong-token")).getCode());
        var disabled = new PackingController(service, "");
        assertEquals(401, assertThrows(BusinessException.class, () -> disabled.confirm(null, "")).getCode());
        verifyNoInteractions(service);
        when(service.accept(null)).thenReturn(true);
        assertTrue(controller.confirm(null, "test-token").getData());
    }
}
