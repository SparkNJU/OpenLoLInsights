package com.example.backend.repository;

import com.example.backend.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRepository extends JpaRepository<Game, Integer> {
    List<Game> findByMatchIdOrderByGameNumberAsc(Integer matchId);
    long countByMatchId(Integer matchId);
}
