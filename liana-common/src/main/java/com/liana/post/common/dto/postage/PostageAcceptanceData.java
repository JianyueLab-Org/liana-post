package com.liana.post.common.dto.postage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/** Authoritative prepared request read by OMS; never supplied directly by a browser. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostageAcceptanceData(String acceptanceId, String status, String createdBy,
                                    String facilityCode, JsonNode mail, String waybillNo) { }
