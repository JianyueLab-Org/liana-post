package com.liana.post.oms.service.impl;

import com.liana.post.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OmsServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void packageReceptionUsesDestinationAndReturnOrigin() {
        var repository = mock(com.liana.post.oms.repository.OmsRepository.class);
        var dispatch = mock(org.springframework.beans.factory.ObjectProvider.class);
        var tracking = mock(org.springframework.beans.factory.ObjectProvider.class);
        var service = new OmsServiceImpl(repository, dispatch, tracking, null);

        var oro = packageSummary("ORO-1", "B1", "LN-OR-01");
        var b2 = packageSummary("B2-1", "B1", "B2");
        var returned = packageSummary("RETURN-1", "B1", "RETURN_ORIGIN");
        when(repository.findPackageSummaries()).thenReturn(java.util.List.of(oro, b2, returned));
        when(repository.findMailsByPackageId("ORO-1")).thenReturn(java.util.List.of(packageMail("B1", "LN-OR-01")));
        when(repository.findMailsByPackageId("RETURN-1")).thenReturn(java.util.List.of(packageMail("B1", "RETURN_ORIGIN")));

        assertEquals(java.util.List.of("ORO-1"), service.listPackages("LN-OR-01").stream()
                .map(com.liana.post.oms.model.dto.MailPackageSummaryResponse::getPackageId).toList());
        assertEquals(java.util.List.of("RETURN-1"), service.listPackages("B1").stream()
                .map(com.liana.post.oms.model.dto.MailPackageSummaryResponse::getPackageId).toList());
        assertThrows(BusinessException.class, () -> service.receiveAndOpenMailPackage("RETURN-1", "LN-OR-01"));
    }

    private com.liana.post.oms.model.dto.MailPackageSummaryResponse packageSummary(String packageId, String origin, String destination) {
        var summary = new com.liana.post.oms.model.dto.MailPackageSummaryResponse();
        summary.setPackageId(packageId);
        summary.setOriginFacilityCode(origin);
        summary.setDestinationNode(destination);
        return summary;
    }

    private com.liana.post.oms.model.entity.MailEntity packageMail(String origin, String destination) {
        var mail = new com.liana.post.oms.model.entity.MailEntity();
        mail.setOriginFacilityCode(origin);
        mail.setDestinationNode(destination);
        return mail;
    }

    @Test
    @SuppressWarnings("unchecked")
    void createMailPersistsServicesAndGeneratesMatchingValidBarcode() {
        var repository = org.mockito.Mockito.mock(com.liana.post.oms.repository.OmsRepository.class);
        var tracking = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        var dispatch = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(repository.findMailTypeByCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.Optional.of(new com.liana.post.oms.model.entity.MailTypeEntity()));
        org.mockito.Mockito.when(repository.findServiceTypeByCode("AIR")).thenReturn(java.util.Optional.of(new com.liana.post.oms.model.entity.ServiceTypeEntity()));
        org.mockito.Mockito.when(repository.saveSender(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.when(repository.saveRecipient(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.when(repository.saveMail(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        var service = new OmsServiceImpl(repository, dispatch, tracking, null);
        for (String product : java.util.List.of("U", "R")) {
            for (boolean registration : java.util.List.of(false, true)) {
                var request = new com.liana.post.oms.model.dto.MailCreateRequest();
                request.setMailTypeCode(product);
                request.setServiceType("AIR");
                request.setAdditionalServices(registration ? java.util.List.of("REGISTRATION") : java.util.List.of());
                request.setSenderFullName("Demo sender");
                request.setSenderPhone("123");
                request.setSenderAddress("Demo address");
                request.setSenderPostcode("1000");
                request.setRecipientFullName("Demo recipient");
                request.setRecipientPhone("456");
                request.setRecipientAddress("Demo address");
                request.setRecipientPostcode("2000");
                var response = service.createMail(request);
                boolean registered = registration || "R".equals(product);
                org.junit.jupiter.api.Assertions.assertTrue(response.getWaybillNo().startsWith(registered ? "RR" : "UU"));
                org.junit.jupiter.api.Assertions.assertTrue(com.liana.post.common.util.UpuBarcodeUtil.validate(response.getWaybillNo()));
                assertEquals(registered ? java.util.List.of("REGISTRATION") : java.util.List.of(), response.getAdditionalServices());
            }
        }
        var captured = org.mockito.ArgumentCaptor.forClass(com.liana.post.oms.model.entity.MailEntity.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(4)).saveMail(captured.capture());
        assertEquals("", captured.getAllValues().get(0).getAdditionalServices());
        assertEquals("REGISTRATION", captured.getAllValues().get(1).getAdditionalServices());
        assertEquals("U", captured.getAllValues().get(1).getMailTypeCode());
    }

    @Test
    void serviceIndicatorMatchesMailType() {
        assertEquals("CP", OmsServiceImpl.serviceIndicatorForMailType("C"));
        assertEquals("UU", OmsServiceImpl.serviceIndicatorForMailType("U"));
        assertEquals("RR", OmsServiceImpl.serviceIndicatorForMailType("R"));
        assertEquals("EE", OmsServiceImpl.serviceIndicatorForMailType("E"));
    }

    @Test
    void serviceIndicatorNormalizesMailTypeCode() {
        assertEquals("RR", OmsServiceImpl.serviceIndicatorForMailType(" r "));
    }

    @Test
    void serviceIndicatorRejectsUnsupportedMailType() {
        assertThrows(BusinessException.class, () -> OmsServiceImpl.serviceIndicatorForMailType("X"));
    }

    @Test
    void registrationIsIndependentAndLegacyRRemainsRegistered() {
        assertEquals(java.util.List.of(), OmsServiceImpl.normalizeAdditionalServices("U", null));
        assertEquals(java.util.List.of("REGISTRATION"), OmsServiceImpl.normalizeAdditionalServices("U", java.util.List.of("REGISTRATION")));
        assertEquals(java.util.List.of("REGISTRATION"), OmsServiceImpl.normalizeAdditionalServices("R", null));
        assertThrows(BusinessException.class, () -> OmsServiceImpl.normalizeAdditionalServices("C", java.util.List.of("REGISTRATION")));
        assertThrows(BusinessException.class, () -> OmsServiceImpl.normalizeAdditionalServices("U", java.util.List.of("INSURANCE")));
        assertThrows(BusinessException.class, () -> OmsServiceImpl.normalizeAdditionalServices("U", java.util.List.of("REGISTRATION", "REGISTRATION")));
    }
}
