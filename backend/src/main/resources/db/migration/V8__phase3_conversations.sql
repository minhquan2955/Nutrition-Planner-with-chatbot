CREATE TABLE chat_conversations (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 title VARCHAR(255) NOT NULL CHECK (length(trim(title)) > 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 turn_count INTEGER NOT NULL DEFAULT 0 CHECK (turn_count >= 0),
 pending_id UUID,
 pending_until TIMESTAMPTZ,
 CHECK ((pending_id IS NULL) = (pending_until IS NULL))
);
CREATE INDEX chat_conversations_owner_idx ON chat_conversations (user_id, created_at DESC, id);

CREATE TABLE chat_messages (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
 turn_index INTEGER NOT NULL CHECK (turn_index > 0),
 role VARCHAR(32) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
 content TEXT NOT NULL CHECK (length(trim(content)) > 0),
 status VARCHAR(32) NOT NULL DEFAULT 'COMPLETE' CHECK (status IN ('COMPLETE','INSUFFICIENT_EVIDENCE')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 model_name VARCHAR(100),
 prompt_version VARCHAR(50),
 response_time_ms BIGINT CHECK (response_time_ms >= 0),
 input_tokens INTEGER CHECK (input_tokens >= 0),
 output_tokens INTEGER CHECK (output_tokens >= 0),
 UNIQUE (conversation_id, turn_index, role)
);

CREATE TABLE chat_sources (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 chunk_id UUID NOT NULL REFERENCES rag_chunks(id),
 message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
 citation_index INTEGER NOT NULL CHECK (citation_index BETWEEN 1 AND 5),
 relevance_score NUMERIC(12,3) NOT NULL,
 quoted_text_snapshot TEXT NOT NULL,
 UNIQUE (message_id, citation_index),
 UNIQUE (message_id, chunk_id)
);
