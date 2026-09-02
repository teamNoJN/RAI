-- RAI (Regulatory AI) 초기 DDL
-- SQLAlchemy 엔티티(backend/parser-service/app/model/entities.py)와 동일하게 유지할 것.

CREATE EXTENSION IF NOT EXISTS vector;

-- 규제 문서 Metadata (PRD 3. 데이터 요구사항)
CREATE TABLE IF NOT EXISTS regulation_documents (
    document_id      VARCHAR(64)   NOT NULL,
    country          VARCHAR(8)    NOT NULL,
    authority        VARCHAR(255)  NOT NULL,
    title            VARCHAR(500)  NOT NULL,
    document_version VARCHAR(64),
    published_date   DATE,
    effective_date   DATE,
    section          VARCHAR(64),
    source_url       TEXT,
    status           VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (document_id)
);
CREATE INDEX IF NOT EXISTS ix_regulation_documents_country ON regulation_documents (country);

-- 파싱된 규제 문서 청크 + 임베딩 (RAG Retrieval 대상)
-- embedding 차원은 parser-service 의 EMBEDDING_DIMENSION 과 일치해야 함
CREATE TABLE IF NOT EXISTS regulation_chunks (
    chunk_id     BIGSERIAL     NOT NULL,
    document_id  VARCHAR(64)   NOT NULL REFERENCES regulation_documents (document_id) ON DELETE CASCADE,
    section      VARCHAR(64),
    chunk_index  INTEGER       NOT NULL,
    content      TEXT          NOT NULL,
    embedding    VECTOR(1536),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (chunk_id)
);
CREATE INDEX IF NOT EXISTS ix_regulation_chunks_document_id ON regulation_chunks (document_id);
