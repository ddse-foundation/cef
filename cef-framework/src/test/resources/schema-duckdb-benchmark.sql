-- DuckDB Benchmark Schema
-- Installs extensions and creates tables for Graph + Vector benchmark

-- Install extensions (idempotent)
INSTALL vss;
LOAD vss;
INSTALL json;
LOAD json;

-- Create nodes table
CREATE TABLE IF NOT EXISTS nodes (
    id UUID PRIMARY KEY,
    label VARCHAR NOT NULL,
    properties JSON,
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
    properties JSON,
    weight DOUBLE,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_node_id) REFERENCES nodes(id),
    FOREIGN KEY (target_node_id) REFERENCES nodes(id)
);

-- Create relation_type table
CREATE TABLE IF NOT EXISTS relation_type (
    name VARCHAR PRIMARY KEY,
    source_label VARCHAR NOT NULL,
    target_label VARCHAR NOT NULL,
    semantics VARCHAR NOT NULL,
    directed BOOLEAN NOT NULL DEFAULT TRUE
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_nodes_label ON nodes(label);
CREATE INDEX IF NOT EXISTS idx_edges_source ON edges(source_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_target ON edges(target_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_relation_type ON edges(relation_type);
