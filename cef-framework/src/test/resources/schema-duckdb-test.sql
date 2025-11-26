-- DuckDB Test Schema for CEF Framework
-- Simplified for R2DBC testing (no extensions needed)

-- Create nodes table
CREATE TABLE IF NOT EXISTS nodes (
    id UUID PRIMARY KEY,
    label VARCHAR NOT NULL,
    properties VARCHAR,  -- Use VARCHAR for JSON in tests
    vectorizable_content TEXT,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Create edges table
CREATE TABLE IF NOT EXISTS edges (
    id UUID PRIMARY KEY,
    relation_type VARCHAR NOT NULL,
    source_node_id UUID NOT NULL,
    target_node_id UUID NOT NULL,
    properties VARCHAR,  -- Use VARCHAR for JSON in tests
    weight DOUBLE,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_node_id) REFERENCES nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (target_node_id) REFERENCES nodes(id) ON DELETE CASCADE
);

-- Create relation_type table (singular as per domain model @Table annotation)
CREATE TABLE IF NOT EXISTS relation_type (
    name VARCHAR PRIMARY KEY,
    source_label VARCHAR NOT NULL,
    target_label VARCHAR NOT NULL,
    semantics VARCHAR NOT NULL,
    directed BOOLEAN NOT NULL DEFAULT TRUE
);

-- Create chunks table (for vector search)
CREATE TABLE IF NOT EXISTS chunks (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    embedding TEXT,  -- Simplified for tests
    linked_node_id UUID,
    metadata VARCHAR,  -- Use VARCHAR for JSON in tests
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (linked_node_id) REFERENCES nodes(id) ON DELETE SET NULL
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_nodes_label ON nodes(label);
CREATE INDEX IF NOT EXISTS idx_edges_source ON edges(source_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_target ON edges(target_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_relation_type ON edges(relation_type);
CREATE INDEX IF NOT EXISTS idx_chunks_linked_node ON chunks(linked_node_id);
