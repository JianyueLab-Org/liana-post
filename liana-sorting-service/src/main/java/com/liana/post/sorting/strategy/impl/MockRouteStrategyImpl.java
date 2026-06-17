package com.liana.post.sorting.strategy.impl;

import com.liana.post.common.constant.LogisticsConstants;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.sorting.constant.SortingConstants;
import com.liana.post.sorting.model.dto.RouteResult;
import com.liana.post.sorting.model.entity.DiscrepancyVerificationEntity;
import com.liana.post.sorting.repository.SortingRepository;
import com.liana.post.sorting.strategy.RoutingStrategy;
import com.liana.post.sorting.client.OmsClient;
import com.liana.post.oms.model.dto.MailResponse;
import com.liana.post.common.model.Result;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Primary
public class MockRouteStrategyImpl implements RoutingStrategy {

    private final SortingRepository sortingRepository;
    private final OmsClient omsClient;

    public MockRouteStrategyImpl(SortingRepository sortingRepository, OmsClient omsClient) {
        this.sortingRepository = sortingRepository;
        this.omsClient = omsClient;
    }

    @Override
    public RouteResult doRoute(String mailId, String targetFacility) {
        String normalizedMailId = normalize(mailId);
        if ("RD588151316CN".equalsIgnoreCase(normalizedMailId)) {
            return securityBlock(normalizedMailId, targetFacility);
        }
        MailResponse mail = fetchMail(normalizedMailId);
        if (mail != null && "INTERNATIONAL".equalsIgnoreCase(mail.getMailScope())) {
            return international(normalizedMailId, mail);
        }
        return domestic(normalizedMailId);
    }

    private RouteResult domestic(String mailId) {
        RouteResult result = new RouteResult();
        result.setSlotCode("B2");
        result.setTargetNodeName("Domestic_Post_Office_B2");
        result.setNextHopCode("B2");
        result.setSecurityStatus(SortingConstants.SECURITY_STATUS_PASS);
        result.setMessage("[ROUTE] Domestic flow stub");
        return result;
    }

    private RouteResult international(String mailId, MailResponse mail) {
        RouteResult result = new RouteResult();
        result.setSlotCode("A2");
        result.setTargetNodeName("A2-International_Exchange_Bureau");
        result.setNextHopCode("A2");
        result.setSecurityStatus(SortingConstants.SECURITY_STATUS_PASS);
        result.setMessage("[ROUTE] International exchange flow stub");
        return result;
    }

    private RouteResult securityBlock(String mailId, String targetFacility) {
        DiscrepancyVerificationEntity entity = new DiscrepancyVerificationEntity();
        entity.setVerificationNo(IdGeneratorUtil.generateBizNo("DV"));
        entity.setItemNo(mailId);
        entity.setPackageNo(StringUtils.hasText(targetFacility) ? targetFacility.trim().toUpperCase() : "99");
        entity.setDiscrepancyType(SortingConstants.DISCREPANCY_SECURITY_FAILED);
        entity.setDiscrepancySource(SortingConstants.SOURCE_SECURITY);
        entity.setExpectedQty(1);
        entity.setActualQty(0);
        entity.setExceptionLevel("DANGER");
        entity.setStatus("OPEN");
        entity.setEvidence("{\"mailId\":\"" + mailId + "\",\"targetFacility\":\"" + (targetFacility == null ? "" : targetFacility.trim().toUpperCase()) + "\"}");
        sortingRepository.saveDiscrepancy(entity);

        RouteResult result = new RouteResult();
        result.setSlotCode("99");
        result.setTargetNodeName("Security_Exception_Bin");
        result.setNextHopCode("RETURN_TO_ORIGIN");
        result.setSecurityStatus(SortingConstants.SECURITY_STATUS_DANGER);
        result.setMessage("[ROUTE] Security block flow stub");
        return result;
    }

    private MailResponse fetchMail(String mailId) {
        if (omsClient == null) {
            return null;
        }
        Result<MailResponse> result = omsClient.getMail(mailId);
        return result == null ? null : result.getData();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("mailId cannot be blank");
        }
        return value.trim().toUpperCase();
    }
}
