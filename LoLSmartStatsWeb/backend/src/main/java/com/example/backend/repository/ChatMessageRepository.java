package com.example.backend.repository;

import com.example.backend.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId, Pageable pageable);

    Optional<ChatMessage> findFirstBySessionIdAndTurnIdAndRole(String sessionId, String turnId, String role);

    Optional<ChatMessage> findFirstBySessionIdAndReportFileIdIsNotNullAndReportFileId(String sessionId, String reportFileId);

    Optional<ChatMessage> findFirstByReportFileId(String reportFileId);
}

