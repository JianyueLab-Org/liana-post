package com.liana.post.syncer.util;

import com.liana.post.syncer.model.dto.SyncPlanDto;
import java.util.List;

/**
 * 同步计划构建辅助类。
 */
public final class SyncerStubDataBuilder {

    private SyncerStubDataBuilder() {
    }

    public static SyncPlanDto emptyPlan() {
        SyncPlanDto dto = new SyncPlanDto();
        dto.setBizIds(List.of());
        return dto;
    }
}