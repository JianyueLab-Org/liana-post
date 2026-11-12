package com.liana.post.sorting.model.dto;

public class SortingDetectedLabelResponse {
    private String label;
    private Double confidence;
    private Double x;
    private Double y;
    private Double width;
    private Double height;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }
    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }
    public Double getWidth() { return width; }
    public void setWidth(Double width) { this.width = width; }
    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }
}
