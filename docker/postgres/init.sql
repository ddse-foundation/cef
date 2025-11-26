-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create schema for graph data
CREATE SCHEMA IF NOT EXISTS graph;

-- Set default schema
SET search_path TO graph, public;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA graph TO cef_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA graph TO cef_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA graph TO cef_user;

-- Create vector index function for future use
CREATE OR REPLACE FUNCTION create_vector_index()
RETURNS void AS $$
BEGIN
    -- Indexes will be created by the application
    RAISE NOTICE 'Database initialized successfully';
END;
$$ LANGUAGE plpgsql;

