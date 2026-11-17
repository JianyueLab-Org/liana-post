package com.liana.post.dispatch.service;

import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.dispatch.controller.ManifestAssignmentController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManifestAssignmentControllerTest {
    @Test
    void readAndWriteRequireConfiguredServiceCredential() {
        var service = mock(DispatchManifestAssignmentService.class);
        var controller = new ManifestAssignmentController(service, "secret");
        for (String supplied : new String[] { null, "", "bad" }) {
            assertEquals(401, assertThrows(BusinessException.class, () -> controller.snapshot("M1", supplied)).getCode());
            assertEquals(401, assertThrows(BusinessException.class, () -> controller.apply(null, supplied)).getCode());
            assertEquals(401, assertThrows(BusinessException.class, () -> controller.receive(null, supplied)).getCode());
        }
        assertThrows(BusinessException.class, () -> new ManifestAssignmentController(service, "").snapshot("M1", ""));
        verifyNoInteractions(service);
        controller.snapshot("M1", "secret"); verify(service).snapshot("M1");
        var command = new ManifestAllocation.Command("R1", "A1", "M1", 1, "a".repeat(64), "T1", "ASSIGN", "clerk", "");
        controller.apply(command, "secret"); verify(service).apply(command);
    }
}
