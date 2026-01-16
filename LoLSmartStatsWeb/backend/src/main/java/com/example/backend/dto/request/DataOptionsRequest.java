package com.example.backend.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class DataOptionsRequest {

    private Scope scope;

    @NotEmpty
    private List<String> need;

    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }

    public List<String> getNeed() { return need; }
    public void setNeed(List<String> need) { this.need = need; }

    public static class Scope {
        private String tournamentName;
        private String stage;
        private DateRange dateRange;

        public String getTournamentName() { return tournamentName; }
        public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }

        public String getStage() { return stage; }
        public void setStage(String stage) { this.stage = stage; }

        public DateRange getDateRange() { return dateRange; }
        public void setDateRange(DateRange dateRange) { this.dateRange = dateRange; }
    }

    public static class DateRange {
        private String from;
        private String to;

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
    }
}

