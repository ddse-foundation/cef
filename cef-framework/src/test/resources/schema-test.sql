-- Test Schema for PostgreSQL (Testcontainers)
-- No pgvector extension - uses BYTEA for embeddings

-- Create nodes table
CREATE TABLE IF NOT EXISTS nodes (
    id UUID PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    properties JSONB,
    vectorizable_content TEXT,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Create edges table
CREATE TABLE IF NOT EXISTS edges (
    id UUID PRIMARY KEY,
    relation_type VARCHAR(255) NOT NULL,
    source_node_id UUID NOT NULL,
    target_node_id UUID NOT NULL,
    properties JSONB,
    weight DOUBLE PRECISION,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_node_id) REFERENCES nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (target_node_id) REFERENCES nodes(id) ON DELETE CASCADE
);

-- Create relation_types table
CREATE TABLE IF NOT EXISTS relation_types (
    name VARCHAR(255) PRIMARY KEY,
    source_label VARCHAR(255) NOT NULL,
    target_label VARCHAR(255) NOT NULL,
    semantics VARCHAR(50) NOT NULL,
    directed BOOLEAN NOT NULL DEFAULT TRUE
);

-- Create chunks table (for vector search)
-- Use BYTEA instead of vector for test compatibility
CREATE TABLE IF NOT EXISTS chunks (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    embedding BYTEA,  -- BYTEA instead of vector(1536) for tests
    linked_node_id UUID,
    metadata JSONB,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (linked_node_id) REFERENCES nodes(id) ON DELETE SET NULL
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_nodes_label ON nodes(label);
CREATE INDEX IF NOT EXISTS idx_nodes_properties ON nodes USING GIN (properties);
CREATE INDEX IF NOT EXISTS idx_edges_source ON edges(source_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_target ON edges(target_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_relation_type ON edges(relation_type);
CREATE INDEX IF NOT EXISTS idx_chunks_linked_node ON chunks(linked_node_id);
CREATE INDEX IF NOT EXISTS idx_chunks_metadata ON chunks USING GIN (metadata);

-- Create framework mandatory relation types
INSERT INTO relation_types (name, source_label, target_label, semantics, directed) VALUES
    ('IS_A', 'Instance', 'Class', 'CLASSIFICATION', TRUE),
    ('CONTAINS', 'Whole', 'Part', 'CONTAINMENT', TRUE),
    ('CHILD_OF', 'Child', 'Parent', 'HIERARCHY', TRUE)
ON CONFLICT (name) DO NOTHING;
