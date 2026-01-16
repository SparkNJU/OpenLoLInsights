package com.example.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public class PlayerSearchRequest extends PageRequest {

    @NotBlank
    private String q;

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
}
