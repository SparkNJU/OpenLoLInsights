package com.example.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "Teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 255)
    private String name;

    @Column(name = "short_name", length = 255)
    private String shortName;

    @Column(length = 255)
    private String region;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}

