CREATE TABLE rag_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_key UUID NOT NULL DEFAULT gen_random_uuid(),
    title VARCHAR(250) NOT NULL,
    publisher VARCHAR(200) NOT NULL,
    publication_year INTEGER CHECK (publication_year BETWEEN 1 AND 9999),
    source_url VARCHAR(2000),
    version VARCHAR(80) NOT NULL,
    checksum VARCHAR(64) NOT NULL CHECK (checksum ~ '^[0-9a-f]{64}$'),
    file_path TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('QUEUED','PROCESSING','READY','ACTIVE','FAILED','REVOKED')),
    selected_pages TEXT NOT NULL DEFAULT '',
    verified_transcriptions JSONB NOT NULL DEFAULT '{}'::jsonb,
    uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    error_code TEXT,
    processing_token UUID,
    processing_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    UNIQUE (document_key, version),
    CHECK ((error_code IS NOT NULL) = (status = 'FAILED')),
    CHECK ((processing_token IS NOT NULL) = (status = 'PROCESSING')),
    CHECK ((processing_until IS NOT NULL) = (status = 'PROCESSING'))
);

CREATE INDEX rag_documents_queue ON rag_documents(created_at, id)
    WHERE status IN ('QUEUED','PROCESSING');

CREATE TABLE rag_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES rag_documents(id) ON DELETE RESTRICT,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content TEXT NOT NULL CHECK (btrim(content) <> ''),
    page_start INTEGER NOT NULL CHECK (page_start >= 1),
    page_end INTEGER NOT NULL CHECK (page_end >= page_start),
    embedding public.vector(1536) NOT NULL,
    embedding_model TEXT NOT NULL,
    embedding_dimensions INTEGER NOT NULL DEFAULT 1536 CHECK (embedding_dimensions = 1536),
    extraction_method TEXT NOT NULL DEFAULT 'PDF_TEXT'
        CHECK (extraction_method IN ('PDF_TEXT','VERIFIED_TRANSCRIPTION')),
    UNIQUE (document_id, chunk_index)
);
