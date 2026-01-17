package com.example.backend.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseEvent {
    private String event;
    private Object data;

    public SseEvent() {}

    public SseEvent(String event, Object data) {
        this.event = event;
        this.data = data;
    }

    public static SseEvent of(String event, Object data) {
        return new SseEvent(event, data);
    }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
