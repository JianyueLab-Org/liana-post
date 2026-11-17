package com.liana.post.sorting.service;

import com.liana.post.common.dto.sorting.ManifestCancelCommand;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.sorting.controller.ManifestCancelController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManifestCancelControllerTest {
    @Test
    void cancellationRejectsMissingWrongOrUnconfiguredServiceCredentials() {
        var service = mock(ManifestCancelService.class);
        var command = new ManifestCancelCommand("C1", "M1", 1, "LN-OR-01", "clerk", "cancel");
        assertEquals(401, assertThrows(BusinessException.class,
                () -> new ManifestCancelController(service, "").cancel(command, "")).getCode());
        var controller = new ManifestCancelController(service, "shared-secret");
        assertThrows(BusinessException.class, () -> controller.cancel(command, null));
        assertThrows(BusinessException.class, () -> controller.cancel(command, "wrong"));
        verifyNoInteractions(service);
        controller.cancel(command, "shared-secret");
        verify(service).cancel(command);
    }
}
