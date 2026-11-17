package com.liana.post.sorting.service;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.sorting.model.entity.SortingManifestEntity;
import com.liana.post.sorting.model.entity.SortingManifestPackageEntity;
import com.liana.post.sorting.repository.SortingRepository;
import com.liana.post.sorting.service.impl.SortingServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SortingManifestPreviewTest {
    private final SortingRepository repository = mock(SortingRepository.class);
    private final SortingServiceImpl service = new SortingServiceImpl(repository, null, null, null, null, null, null, null, null, null, null);

    @Test
    void explicitManifestWithNoMailNeverFallsBackToAnotherHandoffOrOms() {
        when(repository.findManifestByNo("M1")).thenReturn(Optional.of(new SortingManifestEntity()));
        when(repository.findManifestPackage("M1", "P1")).thenReturn(Optional.of(new SortingManifestPackageEntity()));
        when(repository.findManifestItems("M1")).thenReturn(List.of());

        var response = service.previewUnpackItems("P1", "M1");

        assertEquals("M1", response.getManifestNo());
        assertEquals("MANIFEST", response.getSource());
        assertTrue(response.getItemNos().isEmpty());
        verify(repository, never()).findManifestByExpectedPackageNo(anyString());
    }

    @Test
    void explicitManifestRejectsPackageFromAnotherHandoff() {
        when(repository.findManifestByNo("M1")).thenReturn(Optional.of(new SortingManifestEntity()));
        when(repository.findManifestPackage("M1", "P2")).thenReturn(Optional.empty());

        assertEquals(409, assertThrows(BusinessException.class,
                () -> service.previewUnpackItems("P2", "M1")).getCode());
        verify(repository, never()).findManifestByExpectedPackageNo(anyString());
    }

    @Test
    void receiveUsesTheDurableReceivingBoundaryWithoutLegacyWrites() {
        var receiving = mock(ManifestReceivingService.class);
        var sorting = new SortingServiceImpl(repository, null, null, null, null, null, null, null, receiving, null, null);
        var request = new com.liana.post.sorting.model.dto.SortingReceiveRequest();
        request.setManifestNo("M1"); request.setPackageNos(List.of("P1"));
        doThrow(new BusinessException(409, "not sealed")).when(receiving).receive(request);
        assertEquals(409, assertThrows(BusinessException.class, () -> sorting.receive(request)).getCode());
        verify(receiving).receive(request);
        verify(repository, never()).saveTotalPackage(any());
        verify(repository, never()).saveManifestPackage(any());
    }

    @Test
    void cancelledManifestCannotBePreviewedAndOpeningUsesDurableBoundary() {
        var manifest = new SortingManifestEntity(); manifest.setManifestNo("M1"); manifest.setManifestStatus("CANCELLED");
        when(repository.findManifestByNo("M1")).thenReturn(Optional.of(manifest));
        when(repository.lockManifestByNo("M1")).thenReturn(Optional.of(manifest));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.previewUnpackItems("P1", "M1")).getCode());
        var bag = new com.liana.post.sorting.model.entity.SortingTotalPackageEntity(); bag.setManifestNo("M1");
        when(repository.findTotalPackageByNo("P1")).thenReturn(Optional.of(bag));
        var request = new com.liana.post.sorting.model.dto.SortingUnpackItemRequest(); request.setPackageNo("P1");
        var unpacking = mock(UnpackingService.class);
        var sorting = new SortingServiceImpl(repository, null, null, null, null, null, null, null, null, unpacking, null);
        doThrow(new BusinessException(409, "cancelled")).when(unpacking).unpack(request);
        assertEquals(409, assertThrows(BusinessException.class, () -> sorting.unpackItem(request)).getCode());
        verify(unpacking).unpack(request);
        verify(repository, never()).updateTotalPackage(any());
        verify(repository, never()).savePackageItemLine(any());
    }

    @Test
    void automaticPackageLookupCannotTurnCancelledExpectedPackageIntoBlindReceipt() {
        var manifests = mock(com.liana.post.sorting.mapper.SortingManifestMapper.class);
        var packages = mock(com.liana.post.sorting.mapper.SortingManifestPackageMapper.class);
        var items = mock(com.liana.post.sorting.mapper.SortingManifestItemMapper.class);
        var expected = new SortingManifestPackageEntity(); expected.setManifestNo("M1"); expected.setPackageNo("P1");
        when(packages.selectList(any())).thenReturn(List.of(expected));
        when(items.selectList(any())).thenReturn(List.of());
        var cancelled = new SortingManifestEntity(); cancelled.setManifestNo("M1"); cancelled.setManifestStatus("CANCELLED");
        when(manifests.selectOne(any())).thenReturn(cancelled);
        var actualRepository = new com.liana.post.sorting.repository.MyBatisSortingRepository(null, manifests, items, packages, null, null);
        assertEquals(409, assertThrows(BusinessException.class, () -> actualRepository.findManifestByExpectedPackageNo("P1")).getCode());
    }
}
