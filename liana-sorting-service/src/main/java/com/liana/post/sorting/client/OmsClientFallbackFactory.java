package com.liana.post.sorting.client;

import com.liana.post.common.model.Result;
import com.liana.post.oms.model.dto.CountryResponse;
import com.liana.post.oms.model.dto.MailBagAssignRequest;
import com.liana.post.oms.model.dto.MailResponse;
import com.liana.post.oms.model.dto.MailRouteAssignRequest;
import com.liana.post.oms.model.dto.MailSlotSealRequest;
import com.liana.post.oms.model.dto.MailSlotSealResponse;
import com.liana.post.oms.model.dto.MailSlotSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OmsClientFallbackFactory implements FallbackFactory<OmsClient> {

    private static final Logger log = LoggerFactory.getLogger(OmsClientFallbackFactory.class);
    private static final String MESSAGE = "OMS 服务暂不可用，已触发降级";

    @Override
    public OmsClient create(Throwable cause) {
        return new OmsClient() {
            @Override
            public Result<MailResponse> getMail(String waybillNo) {
                warn("getMail", cause);
                return Result.fail(MESSAGE);
            }

            @Override
            public Result<List<MailResponse>> listMails() {
                warn("listMails", cause);
                return Result.fail(MESSAGE);
            }

            @Override
            public Result<List<CountryResponse>> listCountries() {
                warn("listCountries", cause);
                return Result.fail(MESSAGE);
            }

            @Override
            public Result<MailResponse> updateMailBag(String waybillNo, MailBagAssignRequest request) {
                warn("updateMailBag", cause);
                return Result.fail(MESSAGE);
            }

            @Override
            public Result<MailResponse> updateMailRoute(String waybillNo, MailRouteAssignRequest request) {
                warn("updateMailRoute", cause);
                return Result.fail(MESSAGE);
            }

            @Override
            public Result<List<MailSlotSummaryResponse>> listActiveSlots() {
                warn("listActiveSlots", cause);
                return Result.fail(MESSAGE);
            }

            @Override
            public Result<MailSlotSealResponse> sealMailSlot(String slotCode, MailSlotSealRequest request) {
                warn("sealMailSlot", cause);
                return Result.fail(MESSAGE);
            }

            @Override
            public Result<MailSlotSealResponse> rebagMailSlot(String slotCode, MailSlotSealRequest request) {
                warn("rebagMailSlot", cause);
                return Result.fail(MESSAGE);
            }

            @Override
            public Result<MailResponse> deliverMail(String waybillNo, String facilityCode) {
                warn("deliverMail", cause);
                return Result.fail(MESSAGE);
            }

            @Override
            public Result<MailResponse> departExchangeMail(String waybillNo, String facilityCode) {
                warn("departExchangeMail", cause);
                return Result.fail(MESSAGE);
            }
        };
    }

    private void warn(String operation, Throwable cause) {
        log.warn("Feign fallback triggered: client=OmsClient operation={} reason={}", operation, cause == null ? "unknown" : cause.toString());
    }
}
