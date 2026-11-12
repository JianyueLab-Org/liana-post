package com.liana.post.sorting.strategy.impl;

import com.liana.post.common.constant.LogisticsConstants;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.sorting.constant.SortingConstants;
import com.liana.post.sorting.model.dto.RouteResult;
import com.liana.post.sorting.model.entity.DiscrepancyVerificationEntity;
import com.liana.post.sorting.repository.SortingRepository;
import com.liana.post.sorting.strategy.RoutingStrategy;
import com.liana.post.sorting.client.OmsClient;
import com.liana.post.sorting.client.FacilityClient;
import com.liana.post.sorting.model.dto.FacilityRouteResponse;
import com.liana.post.oms.model.dto.MailResponse;
import com.liana.post.common.model.Result;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.*;

@Service
@Primary
public class MockRouteStrategyImpl implements RoutingStrategy {

    private final SortingRepository sortingRepository;
    private final OmsClient omsClient;
    private final FacilityClient facilityClient;

    public MockRouteStrategyImpl(SortingRepository sortingRepository, OmsClient omsClient, FacilityClient facilityClient) {
        this.sortingRepository = sortingRepository;
        this.omsClient = omsClient;
        this.facilityClient = facilityClient;
    }

    @Override
    public RouteResult doRoute(String mailId, String targetFacility) {
        String normalizedMailId = normalize(mailId);
        if ("RD588151316CN".equalsIgnoreCase(normalizedMailId)) {
            return securityBlock(normalizedMailId, targetFacility);
        }
        MailResponse mail = fetchMail(normalizedMailId);
        if (mail != null && "INTERNATIONAL".equalsIgnoreCase(mail.getMailScope())) {
            return manual(normalizedMailId, "首期只支持国内邮件");
        }
        return domestic(normalizedMailId, mail, targetFacility);
    }

    @Override
    public RouteResult doManualRoute(String mailId, String currentFacility, String nextHopCode) {
        String current = normalizeOptional(currentFacility);
        String next = normalizeOptional(nextHopCode);
        if (!StringUtils.hasText(current) || !StringUtils.hasText(next)) return manual(mailId, "缺少当前机构或下一跳");
        MailResponse mail = fetchMail(normalize(mailId));
        if (mail == null || !"DOMESTIC".equalsIgnoreCase(mail.getMailScope())) return manual(mailId, "仅国内邮件可人工指定下一跳");
        try {
            Result<List<FacilityRouteResponse>> response = facilityClient.listRoutes();
            FacilityRouteResponse route = response == null || response.getData() == null ? null : response.getData().stream()
                    .filter(item -> item != null && Integer.valueOf(1).equals(item.getStatus()))
                    .filter(item -> current.equals(normalizeOptional(item.getOriginFacilityCode())) && next.equals(normalizeOptional(item.getDestinationFacilityCode())))
                    .min(Comparator.comparing(item -> item.getPriorityLevel() == null ? 0 : item.getPriorityLevel())).orElse(null);
            if (route == null) return manual(mailId, "当前机构到指定下一跳没有启用的封发关系");
            RouteResult result = new RouteResult();
            result.setSlotCode(next + "-SLOT");
            result.setTargetNodeName(next);
            result.setNextHopCode(next);
            result.setRouteCode(route.getRouteCode());
            result.setTransportMode(route.getTransportMode());
            result.setSecurityStatus(SortingConstants.SECURITY_STATUS_PASS);
            result.setMessage("[ROUTE] 管理员指定下一跳 " + next + "，邮路 " + route.getRouteCode());
            return result;
        } catch (Exception ex) {
            return manual(mailId, "设施邮路关系暂不可用，请管理员处理");
        }
    }

    private RouteResult domestic(String mailId, MailResponse mail, String fallbackCurrentFacility) {
        RouteResult result = new RouteResult();
        String current = normalizeOptional(fallbackCurrentFacility);
        if (!StringUtils.hasText(current)) current = normalizeOptional(mail == null ? null : mail.getCurrentFacilityCode());
        if (!StringUtils.hasText(current)) return manual(mailId, "缺少当前设施");
        String postcode = mail == null ? null : mail.getRecipientPostcode();
        if (!StringUtils.hasText(postcode) || !postcode.matches("\\d{4}")) return manual(mailId, "收件邮编缺失或格式无效");
        String target = switch (postcode.substring(0, 2)) {
            case "10" -> "LN-NAM-HQ";
            case "20" -> "LN-OR-RC";
            case "30" -> "LN-MI-RC";
            case "40" -> "LN-SE-RC";
            default -> null;
        };
        if (target == null) return manual(mailId, "收件邮编未覆盖");
        if (current.equals(target)) return manual(mailId, "已到达目的区域中心，请管理员处理末端投递");
        try {
            Result<List<FacilityRouteResponse>> response = facilityClient.listRoutes();
            List<FacilityRouteResponse> routes = response == null || response.getData() == null ? List.of() : response.getData();
            List<FacilityRouteResponse> path = findPath(routes, current, target);
            if (path.isEmpty()) return manual(mailId, "当前设施到目的区域中心没有有效邮路");
            FacilityRouteResponse first = path.get(0);
            result.setSlotCode(first.getDestinationFacilityCode() + "-SLOT");
            result.setTargetNodeName(first.getDestinationFacilityCode());
            result.setNextHopCode(first.getDestinationFacilityCode());
            result.setRouteCode(first.getRouteCode());
            result.setTransportMode(first.getTransportMode());
            result.setSecurityStatus(SortingConstants.SECURITY_STATUS_PASS);
            result.setMessage("[ROUTE] " + first.getRouteCode() + " / " + first.getTransportMode() + "，下一跳 " + first.getDestinationFacilityCode());
            return result;
        } catch (Exception ex) {
            return manual(mailId, "设施邮路关系暂不可用，请管理员处理");
        }
    }

    private List<FacilityRouteResponse> findPath(List<FacilityRouteResponse> routes, String source, String destination) {
        Map<String, List<FacilityRouteResponse>> graph = new HashMap<>();
        for (FacilityRouteResponse route : routes) {
            if (route == null || !Integer.valueOf(1).equals(route.getStatus()) || !StringUtils.hasText(route.getOriginFacilityCode()) || !StringUtils.hasText(route.getDestinationFacilityCode())) continue;
            graph.computeIfAbsent(route.getOriginFacilityCode().trim().toUpperCase(), ignored -> new ArrayList<>()).add(route);
        }
        record State(String node, List<FacilityRouteResponse> path) {}
        Queue<State> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new State(source.toUpperCase(), List.of()));
        visited.add(source.toUpperCase());
        while (!queue.isEmpty()) {
            State state = queue.remove();
            List<FacilityRouteResponse> nextRoutes = new ArrayList<>(graph.getOrDefault(state.node(), List.of()));
            nextRoutes.sort(Comparator.comparing((FacilityRouteResponse r) -> r.getPriorityLevel() == null ? 0 : r.getPriorityLevel()).thenComparing(r -> r.getEstimatedHours() == null ? java.math.BigDecimal.ZERO : r.getEstimatedHours()));
            for (FacilityRouteResponse route : nextRoutes) {
                String next = route.getDestinationFacilityCode().trim().toUpperCase();
                List<FacilityRouteResponse> nextPath = new ArrayList<>(state.path());
                nextPath.add(route);
                if (next.equals(destination.toUpperCase())) return nextPath;
                if (visited.add(next)) queue.add(new State(next, nextPath));
            }
        }
        return List.of();
    }

    private RouteResult manual(String mailId, String message) {
        RouteResult result = new RouteResult();
        result.setSlotCode("MANUAL_REVIEW");
        result.setTargetNodeName("MANUAL_REVIEW");
        result.setNextHopCode(null);
        result.setSecurityStatus("MANUAL_REVIEW");
        result.setMessage("[ROUTE] " + (StringUtils.hasText(message) ? message : "未匹配封发路由关系，请管理员处理"));
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

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
