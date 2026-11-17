package com.liana.post.common.dto.sorting;

import com.liana.post.common.exception.BusinessException;

import java.util.Locale;

/** A fixed command for one immutable prealert version. Actor comes from the sending service's login. */
public record ManifestSealCommand(String requestNo, String manifestNo, Integer versionNo,
                                  String sourceOrgCode, String actorId) {
    public ManifestSealCommand normalized() {
        if (versionNo == null || versionNo < 1) throw new BusinessException(400, "positive versionNo is required");
        if (actorId == null || actorId.isBlank() || actorId.length() > 64) {
            throw new BusinessException(400, "actorId is required and must fit 64 characters");
        }
        return new ManifestSealCommand(code(requestNo), code(manifestNo), versionNo, code(sourceOrgCode), actorId.trim());
    }

    private static String code(String value) {
        if (value == null || !value.trim().matches("[A-Za-z0-9_-]{1,64}")) {
            throw new BusinessException(400, "invalid seal command identifier");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
