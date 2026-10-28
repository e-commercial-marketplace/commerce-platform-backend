package com.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** Persisted human-chat message; sender membership is validated by the chat service. */
@Entity
@Table(name = "messages", uniqueConstraints = @UniqueConstraint(
    name = "uq_messages_sender_client_message", columnNames = {"sender_id", "client_message_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

  /** Idempotency-Key người gửi đặt cho tin này; gửi lại cùng key thì trả lại tin đã lưu (V19). */
  @Column(name = "client_message_id", length = 100)
  private String clientMessageId;

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
