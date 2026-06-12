package com.liana.post.dispatch.cache;

public interface RedisKeyConstants {
    String OMS_MAIL_BY_WAYBILL_PREFIX = "cache:oms:mail:";
    String OMS_MAIL_LIST_BY_STATUS_PREFIX = "cache:oms:mail:status:";
    String OMS_DISPATCH_CANDIDATES_PREFIX = "cache:oms:dispatch:candidates:";
    String DISPATCH_BAG_PREFIX = "cache:dispatch:bag:";
    String TRACKING_EVENT_BY_WAYBILL_PREFIX = "cache:tracking:event:waybill:";
    String TRACKING_EVENT_BY_NO_PREFIX = "cache:tracking:event:no:";
}
