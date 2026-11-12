package com.liana.post.postage.acceptance;

import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.model.Result;
import com.liana.post.postage.client.OmsAcceptanceClient;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static com.liana.post.postage.acceptance.AcceptanceModels.*;

/** Durable local transitions surround HTTP calls; no database lock spans an HTTP call. */
@Service
public class AcceptanceCoordinator {
    private final AcceptanceStore store;
    private final OmsAcceptanceClient oms;
    public AcceptanceCoordinator(AcceptanceStore store, OmsAcceptanceClient oms) { this.store=store; this.oms=oms; }
    public View accept(Request body, JwtUserContext actor, String auth) {
        var request=store.normalize(body);
        var prepared=store.previous(request,actor);
        if (prepared==null) {
            var mail=remote(()->oms.validate(request.mail(),auth));
            prepared=store.prepare(request,mail,actor);
        }
        return resume(prepared.acceptanceId(),actor,auth);
    }
    public View resume(String id, JwtUserContext actor, String auth) {
        var prepared=store.get(id,actor);
        if (!"PREPARED".equals(prepared.status())) return prepared;
        var outcome=remote(()->oms.complete(id,auth));
        if (!"CREATED".equals(outcome.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"OMS 未确认邮件创建，请查询后重试或取消");
        return store.finish(id,outcome.waybillNo(),actor);
    }
    public View cancel(String id, JwtUserContext actor, String auth) {
        var prepared=store.get(id,actor);
        if (!"PREPARED".equals(prepared.status())) return prepared;
        var outcome=remote(()->oms.abort(id,auth));
        // A committed mail wins a racing cancel; vouchers must stay consumed.
        if ("CREATED".equals(outcome.status())) return store.finish(id,outcome.waybillNo(),actor);
        if (!"CANCELLED".equals(outcome.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"OMS 未确认取消，保留邮资预占");
        return store.release(id,actor);
    }
    private <T> T remote(java.util.function.Supplier<Result<T>> action) {
        try {
            var result=action.get();
            if (result==null || result.getCode()==null || result.getCode()!=200 || result.getData()==null) {
                int code=result!=null && result.getCode()!=null && result.getCode()>=400 && result.getCode()<500?result.getCode():503;
                throw new ResponseStatusException(HttpStatus.valueOf(code),result!=null?result.getMessage():"OMS 返回无效结果");
            }
            return result.getData();
        } catch (FeignException e) {
            int code=e.status()>=400 && e.status()<500?e.status():503;
            throw new ResponseStatusException(HttpStatus.valueOf(code),"OMS 收寄操作未确认，请恢复原请求或查询结算记录；不要重复收款");
        }
    }
}
