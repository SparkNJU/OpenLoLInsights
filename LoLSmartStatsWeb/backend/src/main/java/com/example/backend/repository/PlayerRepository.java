package com.example.backend.repository;

import com.example.backend.entity.Player;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Integer> {
    Page<Player> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
