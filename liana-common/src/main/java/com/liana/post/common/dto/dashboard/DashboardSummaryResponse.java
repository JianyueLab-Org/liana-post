package com.liana.post.common.dto.dashboard;

import java.util.ArrayList;
import java.util.List;

public class DashboardSummaryResponse {
    private String title;
    private String scope;
    private List<MetricItem> metrics = new ArrayList<>();
    private List<BreakdownGroup> breakdowns = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public List<MetricItem> getMetrics() { return metrics; }
    public void setMetrics(List<MetricItem> metrics) { this.metrics = metrics; }
    public List<BreakdownGroup> getBreakdowns() { return breakdowns; }
    public void setBreakdowns(List<BreakdownGroup> breakdowns) { this.breakdowns = breakdowns; }

    public DashboardSummaryResponse addMetric(String label, Object value, String hint, String tone) {
        this.metrics.add(new MetricItem(label, value == null ? "0" : String.valueOf(value), hint, tone));
        return this;
    }

    public DashboardSummaryResponse addBreakdown(String title, List<BreakdownItem> items) {
        this.breakdowns.add(new BreakdownGroup(title, items == null ? List.of() : items));
        return this;
    }

    public static BreakdownItem item(String label, Object value, String status) {
        return new BreakdownItem(label, value == null ? "0" : String.valueOf(value), status);
    }

    public static class MetricItem {
        private String label;
        private String value;
        private String hint;
        private String tone;

        public MetricItem() {
        }

        public MetricItem(String label, String value, String hint, String tone) {
            this.label = label;
            this.value = value;
            this.hint = hint;
            this.tone = tone;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getHint() { return hint; }
        public void setHint(String hint) { this.hint = hint; }
        public String getTone() { return tone; }
        public void setTone(String tone) { this.tone = tone; }
    }

    public static class BreakdownGroup {
        private String title;
        private List<BreakdownItem> items = new ArrayList<>();

        public BreakdownGroup() {
        }

        public BreakdownGroup(String title, List<BreakdownItem> items) {
            this.title = title;
            this.items = items;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public List<BreakdownItem> getItems() { return items; }
        public void setItems(List<BreakdownItem> items) { this.items = items; }
    }

    public static class BreakdownItem {
        private String label;
        private String value;
        private String status;

        public BreakdownItem() {
        }

        public BreakdownItem(String label, String value, String status) {
            this.label = label;
            this.value = value;
            this.status = status;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
