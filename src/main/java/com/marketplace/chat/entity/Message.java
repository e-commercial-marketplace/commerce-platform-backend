package com.marketplace.chat.entity;

import com.marketplace.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** Persisted human-chat message; sender membership is validated by the chat service. */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "image_url", length = 2048)
  private String imageUrl;

  @Column(name = "is_read", nullable = false)
  private boolean read;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sender_id", nullable = false)
  private User sender;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }
}
