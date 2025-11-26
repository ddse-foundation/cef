-- Test Schema for CEF Repository Tests
-- Simplified for R2DBC compatibility - no extensions, no schemas

-- Create nodes table
CREATE TABLE IF NOT EXISTS nodes (
    id UUID PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    properties JSONB,
    vectorizable_content TEXT,
    created TIMESTAMP NOT NULL,
    updated TIMESTAMP NOT NULL,
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
    created TIMESTAMP NOT NULL,
    FOREIGN KEY (source_node_id) REFERENCES nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (target_node_id) REFERENCES nodes(id) ON DELETE CASCADE
);

-- Create relation_type table (matches domain model: source_label, target_label, directed)
CREATE TABLE IF NOT EXISTS relation_type (
    name VARCHAR(255) PRIMARY KEY,
    source_label VARCHAR(255) NOT NULL,
    target_label VARCHAR(255) NOT NULL,
    semantics VARCHAR(50) NOT NULL,
    directed BOOLEAN NOT NULL DEFAULT TRUE
);

-- Create chunks table (simplified without vector extension)
CREATE TABLE IF NOT EXISTS chunks (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    embedding TEXT,
    linked_node_id UUID,
    metadata JSONB,
    created TIMESTAMP NOT NULL,
    FOREIGN KEY (linked_node_id) REFERENCES nodes(id) ON DELETE SET NULL
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_nodes_label ON nodes(label);
CREATE INDEX IF NOT EXISTS idx_edges_source ON edges(source_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_target ON edges(target_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_relation_type ON edges(relation_type);
CREATE INDEX IF NOT EXISTS idx_chunks_linked_node ON chunks(linked_node_id);

