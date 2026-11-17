package com.liana.post.common.dto.sorting;

import com.liana.post.common.exception.BusinessException;

/** Source-service command. Cancellation does not mean that a transport task has been cancelled. */
public record ManifestCancelCommand(String requestNo, String manifestNo, Integer versionNo,
                                    String sourceOrgCode, String actorId, String reason) {
    public ManifestCancelCommand normalized() {
        var identity = new ManifestSealCommand(requestNo, manifestNo, versionNo, sourceOrgCode, actorId).normalized();
        if (reason == null || reason.isBlank() || reason.trim().length() > 512) {
            throw new BusinessException(400, "cancellation reason is required and must fit 512 characters");
        }
        return new ManifestCancelCommand(identity.requestNo(), identity.manifestNo(), identity.versionNo(),
                identity.sourceOrgCode(), identity.actorId(), reason.trim());
    }
}
