package com.liana.post.transport.service;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.transport.controller.ManifestArrivalController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManifestArrivalControllerTest {
    @Test
    void arrivalAndReceiptsRequireTheConfiguredInternalCredential() {
        var service = mock(ManifestArrivalService.class);
        var controller = new ManifestArrivalController(service, "secret");
        for (String token : new String[] { null, "", "bad" }) {
            assertEquals(401, assertThrows(BusinessException.class, () -> controller.arrival("M1", token)).getCode());
            assertEquals(401, assertThrows(BusinessException.class, () -> controller.receive(null, token)).getCode());
        }
        assertThrows(BusinessException.class, () -> new ManifestArrivalController(service, "").receive(null, ""));
        verifyNoInteractions(service);
        controller.arrival("m1", "secret"); verify(service).arrival("M1");
    }
}
