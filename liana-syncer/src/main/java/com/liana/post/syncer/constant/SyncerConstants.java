package com.liana.post.syncer.constant;

/**
 * Syncer 全局常量。
 */
public interface SyncerConstants {

    String BIZ_TYPE_OUTBOX = "OUTBOX";
    String BIZ_TYPE_SYNC_TASK = "SYNC_TASK";
    String BIZ_TYPE_RETRY = "RETRY";

    String STATUS_NEW = "NEW";
    String STATUS_PENDING = "PENDING";
    String STATUS_PROCESSING = "PROCESSING";
    String STATUS_SUCCESS = "SUCCESS";
    String STATUS_FAILED = "FAILED";
    String STATUS_CONFLICT = "CONFLICT";

    String CHANNEL_DB = "DB";
    String CHANNEL_MESSAGE = "MESSAGE";
    String CHANNEL_REDIS = "REDIS";

    String DEFAULT_SOURCE_SERVICE = "UNKNOWN";
    String DEFAULT_TARGET_SERVICE = "UNKNOWN";
}
