package com.liana.post.oms.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.postage.PostageAcceptanceData;
import com.liana.post.common.model.JwtUserContext;
import com.liana.post.oms.acceptance.*;
import com.liana.post.oms.model.dto.*;
import com.liana.post.oms.model.entity.*;
import com.liana.post.oms.repository.OmsRepository;
import com.liana.post.oms.service.OmsService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OmsAcceptanceTest {
    final OmsRepository repository=mock(OmsRepository.class);
    final JwtUserContext clerk=new JwtUserContext();
    final ObjectMapper json=new ObjectMapper();
    AcceptanceRules rules;
    @BeforeEach void init(){
        clerk.setUsername("clerk");clerk.setRole("CLERK");clerk.setFacilityCode("B1");
        rules=new AcceptanceRules(repository,Validation.buildDefaultValidatorFactory().getValidator());
        when(repository.findMailTypeByCode(anyString())).thenReturn(Optional.of(new MailTypeEntity()));
        var service=new ServiceTypeEntity();service.setCode("AIR");service.setEnabled(1);
        when(repository.findServiceTypeByCode("AIR")).thenReturn(Optional.of(service));
    }
    MailCreateRequest mail(){var m=new MailCreateRequest();m.setMailTypeCode("U");m.setMailScope("DOMESTIC");m.setServiceType("AIR");m.setSenderFullName("Demo sender");m.setSenderPhone("123");m.setSenderAddress("Demo address");m.setSenderPostcode("1000");m.setRecipientFullName("Demo recipient");m.setRecipientPhone("456");m.setRecipientAddress("Demo address");m.setRecipientPostcode("2000");m.setWeightGrams(20);return m;}
    @Test void derivesFacilityAndNormalizesLegacyRegistration(){var m=mail();m.setMailTypeCode("r");m.setCurrentFacilityCode("SPOOF");m.setOriginFacilityCode("SPOOF");rules.validate(m,clerk);assertEquals("B1",m.getCurrentFacilityCode());assertEquals("B1",m.getOriginFacilityCode());assertEquals(List.of("REGISTRATION"),m.getAdditionalServices());}
    @Test void rejectsNonPositiveWeightAndUnknownServices(){var m=mail();m.setWeightGrams(0);assertThrows(ResponseStatusException.class,()->rules.validate(m,clerk));m.setWeightGrams(20);m.setAdditionalServices(List.of("INSURANCE"));assertThrows(ResponseStatusException.class,()->rules.validate(m,clerk));}
    @Test void rejectsMissingOrInvalidDomesticPostcodes(){var m=mail();m.setRecipientPostcode(null);assertThrows(ResponseStatusException.class,()->rules.validate(m,clerk));var invalid=mail();invalid.setSenderPostcode("10001");assertThrows(ResponseStatusException.class,()->rules.validate(invalid,clerk));var unknown=mail();unknown.setRecipientPostcode("9999");assertThrows(ResponseStatusException.class,()->rules.validate(unknown,clerk));}
    @Test void rejectsInternationalMailInDomesticFirstRelease(){var m=mail();m.setMailScope("INTERNATIONAL");m.setDestCountryCode("JP");assertThrows(ResponseStatusException.class,()->rules.validate(m,clerk));}
    @Test void createdLedgerReturnsOriginalWithoutCreatingAgain(){var jdbc=mock(JdbcTemplate.class);var service=mock(OmsService.class);when(jdbc.queryForMap(anyString(),any())).thenReturn(Map.of("status","CREATED","waybill_no","UU123456789LN"));var writer=new OmsAcceptanceWriter(jdbc,service,rules,json);var data=new PostageAcceptanceData(UUID.randomUUID().toString(),"PREPARED","clerk","B1",json.valueToTree(mail()),null);assertEquals("UU123456789LN",writer.complete(data,clerk).waybillNo());assertEquals("CREATED",writer.abort(data).status());verifyNoInteractions(service);}
    @Test void cancellationTombstoneRejectsDelayedCreate(){var jdbc=mock(JdbcTemplate.class);var service=mock(OmsService.class);when(jdbc.queryForMap(anyString(),any())).thenReturn(Map.of("status","CANCELLED"));var writer=new OmsAcceptanceWriter(jdbc,service,rules,json);var data=new PostageAcceptanceData(UUID.randomUUID().toString(),"PREPARED","clerk","B1",json.valueToTree(mail()),null);assertThrows(ResponseStatusException.class,()->writer.complete(data,clerk));verifyNoInteractions(service);}
    @Test void createsFromAuthoritativeSnapshotAndKeepsOriginalFacility(){var jdbc=mock(JdbcTemplate.class);var service=mock(OmsService.class);when(jdbc.queryForMap(anyString(),any())).thenReturn(Map.of("status","OPEN"));var response=new MailResponse();response.setWaybillNo("UU123456789LN");when(service.createMail(any())).thenReturn(response);var writer=new OmsAcceptanceWriter(jdbc,service,rules,json);var data=new PostageAcceptanceData(UUID.randomUUID().toString(),"PREPARED","clerk","ORIGINAL",json.valueToTree(mail()),null);assertEquals("CREATED",writer.complete(data,clerk).status());var captured=org.mockito.ArgumentCaptor.forClass(MailCreateRequest.class);verify(service).createMail(captured.capture());assertEquals("ORIGINAL",captured.getValue().getCurrentFacilityCode());}
    @Test void unsignedAndWrongRoleAreRejected(){var access=new AcceptanceAccess();assertEquals(401,assertThrows(ResponseStatusException.class,()->access.user(null)).getStatusCode().value());var token=com.liana.post.common.util.JwtTokenUtil.generateToken("sorter","SORTER","B1",Map.of("permissions",List.of("MAIL_CREATE")));assertEquals(403,assertThrows(ResponseStatusException.class,()->access.user("Bearer "+token)).getStatusCode().value());}
}
