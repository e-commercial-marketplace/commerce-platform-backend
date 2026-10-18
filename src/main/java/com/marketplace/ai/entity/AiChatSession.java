package com.marketplace.ai.entity;

import com.marketplace.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** Persisted AI chat belongs to an authenticated customer; guest AI requests remain stateless. */
@Entity
@Table(name = "ai_chat_sessions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiChatSession {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @OneToMany(mappedBy = "session")
  private List<AiMessage> messages = new ArrayList<>();

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }
}
