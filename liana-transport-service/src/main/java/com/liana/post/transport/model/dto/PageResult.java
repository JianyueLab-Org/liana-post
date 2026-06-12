package com.liana.post.transport.model.dto;

import java.util.List;

public class PageResult<T> {
    private long page;
    private long pageSize;
    private long total;
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long page, long pageSize, long total, List<T> list) {
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
        this.list = list;
    }

    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public long getPageSize() { return pageSize; }
    public void setPageSize(long pageSize) { this.pageSize = pageSize; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }
}
