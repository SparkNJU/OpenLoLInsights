package com.example.backend.repository;

import com.example.backend.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    Page<ChatSession> findByUserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    Page<ChatSession> findByUserIdAndStatusOrderByUpdatedAtDesc(String userId, String status, Pageable pageable);

    Page<ChatSession> findByUserIdAndUpdatedAtBetweenOrderByUpdatedAtDesc(String userId, Instant from, Instant to, Pageable pageable);

    Page<ChatSession> findByUserIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtDesc(String userId, String status, Instant from, Instant to, Pageable pageable);
}

