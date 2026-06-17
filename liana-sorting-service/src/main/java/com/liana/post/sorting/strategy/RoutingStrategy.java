package com.liana.post.sorting.strategy;

import com.liana.post.sorting.model.dto.RouteResult;

public interface RoutingStrategy {
    RouteResult doRoute(String mailId, String targetFacility);
}
