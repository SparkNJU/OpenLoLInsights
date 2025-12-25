package com.example.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public class ChatHistoryResponse {

    public static class Item {
        private String role;
        private String content;
        private String ts;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String mode;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Map<String, Object> context;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getTs() { return ts; }
        public void setTs(String ts) { this.ts = ts; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Map<String, Object> getContext() { return context; }
        public void setContext(Map<String, Object> context) { this.context = context; }
    }

    private List<Item> items;
    private int page;
    private int pageSize;
    private long total;

    public ChatHistoryResponse() {}

    public ChatHistoryResponse(List<Item> items, int page, int pageSize, long total) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
    }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}

