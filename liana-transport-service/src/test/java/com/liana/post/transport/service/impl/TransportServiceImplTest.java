package com.liana.post.transport.service.impl;

import com.liana.post.transport.model.dto.TransportTaskRequest;
import com.liana.post.transport.repository.TransportRepository;
import com.liana.post.transport.service.ManifestAssignmentPublisher;
import com.liana.post.transport.service.ManifestAssignmentService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class TransportServiceImplTest {
    @Test
    void legacyApiDelegatesWholeManifestAndNeverWritesPerBagLinks() {
        var repository = mock(TransportRepository.class);
        var allocations = mock(ManifestAssignmentService.class);
        var publisher = mock(ManifestAssignmentPublisher.class);
        var service = new TransportServiceImpl(repository, null, null, null, allocations, publisher);
        service.createTaskFromDispatchBag(7L);
        verify(allocations).create(argThat(request -> request.getDispatchBagId().equals(7L)));
        service.createTask(new TransportTaskRequest());
        service.retryUnlinkedTaskBags();
        verify(publisher).publishDue();
        verifyNoInteractions(repository);
    }
}
