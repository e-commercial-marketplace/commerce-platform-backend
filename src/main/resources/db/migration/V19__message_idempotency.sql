-- Phase 9 human chat: Idempotency-Key của người gửi, để gửi lại cùng một tin không tạo bản trùng.
-- Cột để trống được (tin cũ chưa có key); PostgreSQL coi các NULL là khác nhau nên không vướng ràng buộc.
ALTER TABLE messages ADD COLUMN client_message_id VARCHAR(100);
ALTER TABLE messages ADD CONSTRAINT uq_messages_sender_client_message UNIQUE (sender_id, client_message_id);
