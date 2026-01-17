package com.example.backend.repository;

import com.example.backend.entity.PlayerGameStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerGameStatRepository extends JpaRepository<PlayerGameStat, Integer> {
    List<PlayerGameStat> findByGameId(Integer gameId);
}
