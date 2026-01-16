package com.example.backend.dto.request;

import jakarta.validation.Valid;

import java.util.List;

public class MatchSearchRequest extends PageRequest {

    @Valid
    private Filter filter;

    @Valid
    private Sort sort;

    public Filter getFilter() { return filter; }
    public void setFilter(Filter filter) { this.filter = filter; }

    public Sort getSort() { return sort; }
    public void setSort(Sort sort) { this.sort = sort; }

    public static class Filter {
        private String tournamentName;
        private String stage;
        private List<Integer> teamIds;
        private DataOptionsRequest.DateRange dateRange;

        public String getTournamentName() { return tournamentName; }
        public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }

        public String getStage() { return stage; }
        public void setStage(String stage) { this.stage = stage; }

        public List<Integer> getTeamIds() { return teamIds; }
        public void setTeamIds(List<Integer> teamIds) { this.teamIds = teamIds; }

        public DataOptionsRequest.DateRange getDateRange() { return dateRange; }
        public void setDateRange(DataOptionsRequest.DateRange dateRange) { this.dateRange = dateRange; }
    }

    public static class Sort {
        private String field; // matchDate / id
        private String order; // asc / desc

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getOrder() { return order; }
        public void setOrder(String order) { this.order = order; }
    }
}

