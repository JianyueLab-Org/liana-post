
package com.liana.post.tracking.service;

import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.tracking.model.dto.TrackingEventResponse;
import com.liana.post.tracking.model.dto.TrackingQueryRequest;

import java.util.List;

public interface TrackingService {
    TrackingEventResponse recordEvent(TrackingEventCreateRequest request);
    TrackingEventResponse getByEventNo(String eventNo);
    List<TrackingEventResponse> listEvents();
    List<TrackingEventResponse> listByWaybillNo(String waybillNo);
    List<TrackingEventResponse> search(TrackingQueryRequest request);
    void initDefaults();
}
