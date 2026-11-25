ALTER TABLE rag_documents DROP CONSTRAINT rag_documents_document_key_version_key;
CREATE UNIQUE INDEX rag_documents_current_edition_idx ON rag_documents(document_key, version)
    WHERE status <> 'FAILED';

ALTER TABLE chat_messages ADD COLUMN trace_id UUID
    CHECK (trace_id IS NULL OR role = 'ASSISTANT');
CREATE UNIQUE INDEX chat_messages_trace_idx ON chat_messages(trace_id) WHERE trace_id IS NOT NULL;
