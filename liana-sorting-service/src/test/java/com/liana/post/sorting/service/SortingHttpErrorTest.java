package com.liana.post.sorting.service;

import com.liana.post.sorting.controller.ManifestClosureController;
import com.liana.post.sorting.handler.SortingExceptionHandler;
import com.liana.post.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SortingHttpErrorTest {
    @Test
    void businessRejectionKeepsHttpAndResultCodesInsteadOfBecoming500() throws Exception {
        var service = mock(ManifestClosureService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ManifestClosureController(service))
                .setControllerAdvice(new SortingExceptionHandler()).build();
        for (int code : new int[]{401, 403, 409}) {
            when(service.finishCheck(eq("M1"), any())).thenThrow(new BusinessException(code, "rejected"));
            mvc.perform(post("/api/v1/sorting/manifests/M1/finish-check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"requestNo\":\"C1\",\"facilityCode\":\"LN-OR-RC\"}"))
                    .andExpect(status().is(code)).andExpect(jsonPath("$.code").value(code));
        }
    }

    @Test
    void malformedJsonIs400BeforeMutation() throws Exception {
        var service = mock(ManifestClosureService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ManifestClosureController(service))
                .setControllerAdvice(new SortingExceptionHandler()).build();
        mvc.perform(post("/api/v1/sorting/manifests/M1/finish-check")
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }
}
