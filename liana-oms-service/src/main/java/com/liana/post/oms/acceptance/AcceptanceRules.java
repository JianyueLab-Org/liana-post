package com.liana.post.oms.acceptance;

import com.liana.post.common.model.JwtUserContext;
import com.liana.post.oms.model.dto.MailCreateRequest;
import com.liana.post.oms.repository.OmsRepository;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AcceptanceRules {
    private final OmsRepository repository;
    private final Validator validator;
    public AcceptanceRules(OmsRepository repository,Validator validator) { this.repository=repository; this.validator=validator; }
    public MailCreateRequest validate(MailCreateRequest r, JwtUserContext actor) {
        if (r==null || !validator.validate(r).isEmpty()) invalid("请补全收寄必填信息，重量须为正整数克");
        String product=upper(r.getMailTypeCode()), scope=r.getMailScope()==null?"DOMESTIC":upper(r.getMailScope());
        String mode=upper(r.getServiceType());
        if (!Set.of("U","R","C","E").contains(product) || repository.findMailTypeByCode(product).isEmpty()) invalid("邮件产品不支持收寄");
        if (!"DOMESTIC".equals(scope)) invalid("首期只支持国内邮件");
        if (!r.getSenderPostcode().matches("\\d{4}") || !r.getRecipientPostcode().matches("\\d{4}")) {
            invalid("寄件人和收件人邮编必须是四位数字");
        }
        if (!Set.of("10", "20", "30", "40").contains(r.getSenderPostcode().substring(0, 2))
                || !Set.of("10", "20", "30", "40").contains(r.getRecipientPostcode().substring(0, 2))) {
            invalid("邮编不属于首期开放的国内邮区");
        }
        var service=repository.findServiceTypeByCode(mode).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"寄递服务不存在"));
        if (!Integer.valueOf(1).equals(service.getEnabled())) invalid("寄递服务已停用");
        var additions=r.getAdditionalServices()==null?List.<String>of():r.getAdditionalServices();
        if (new java.util.HashSet<>(additions).size()!=additions.size() || additions.stream().anyMatch(a->!"REGISTRATION".equals(a))
                || (!additions.isEmpty() && !Set.of("U","R").contains(product))) invalid("附加服务无效");
        r.setAdditionalServices("R".equals(product)?List.of("REGISTRATION"):List.copyOf(additions));
        r.setDestCountryCode(null);
        if (actor.getFacilityCode()==null || actor.getFacilityCode().isBlank()) invalid("工作人员必须绑定网点");
        r.setMailTypeCode(product); r.setMailScope(scope); r.setServiceType(mode);
        r.setOriginFacilityCode(actor.getFacilityCode()); r.setCurrentFacilityCode(actor.getFacilityCode());
        return r;
    }
    private String upper(String value) { if (value==null || value.isBlank()) invalid("邮件产品、服务和国家不能为空"); return value.trim().toUpperCase(Locale.ROOT); }
    private static void invalid(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
