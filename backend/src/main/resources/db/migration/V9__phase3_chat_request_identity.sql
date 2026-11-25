ALTER TABLE chat_messages ADD COLUMN request_id UUID
    CHECK (request_id IS NULL OR role = 'USER');
CREATE UNIQUE INDEX chat_messages_request_idx ON chat_messages (conversation_id, request_id)
    WHERE request_id IS NOT NULL;
