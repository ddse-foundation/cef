# Makefile for CEF Project
# Author: mrmanna

.PHONY: help build clean test run docker-up docker-down docker-logs install

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

install: ## Install dependencies
	mvn clean install -DskipTests

build: ## Build all modules
	mvn clean package

clean: ## Clean build artifacts
	mvn clean
	rm -rf data/*.duckdb*

test: ## Run tests
	mvn test

run: ## Run example application
	cd cef-example && mvn spring-boot:run

# Docker targets
docker-up: ## Start all services (Ollama only)
	docker-compose up -d

docker-up-postgres: ## Start all services with PostgreSQL
	docker-compose --profile postgres up -d

docker-up-minio: ## Start all services with MinIO
	docker-compose --profile minio up -d

docker-up-all: ## Start all services (PostgreSQL + MinIO)
	docker-compose --profile postgres --profile minio up -d

docker-down: ## Stop all services
	docker-compose down

docker-logs: ## Show docker logs
	docker-compose logs -f

docker-clean: ## Remove all containers and volumes
	docker-compose down -v
	docker volume rm cef_postgres_data cef_minio_data cef_ollama_data 2>/dev/null || true

# Development targets
dev: docker-up install run ## Full dev setup (start docker + build + run)

dev-postgres: docker-up-postgres install run ## Dev with PostgreSQL

dev-all: docker-up-all install run ## Dev with all services

# Quick start
quick: ## Quick start (assumes docker is already running)
	mvn clean install -DskipTests && cd cef-example && mvn spring-boot:run

# Check services
check: ## Check if services are running
	@echo "Checking services..."
	@curl -s http://localhost:11434/api/tags > /dev/null && echo "✓ Ollama is running" || echo "✗ Ollama is NOT running"
	@docker ps | grep -q cef-postgres && echo "✓ PostgreSQL is running" || echo "✗ PostgreSQL is NOT running (optional)"
	@docker ps | grep -q cef-minio && echo "✓ MinIO is running" || echo "✗ MinIO is NOT running (optional)"

# Database targets
db-duckdb: ## Use DuckDB (default)
	@echo "Using DuckDB (embedded)"

db-postgres: ## Switch to PostgreSQL
	@echo "Switching to PostgreSQL..."
	@echo "Update cef-example/src/main/resources/application.yml:"
	@echo "  cef.database.type: postgresql"

# Ollama model management
ollama-pull-llama: ## Pull llama3.2 model
	docker exec -it cef-ollama ollama pull llama3.2:3b

ollama-pull-nomic: ## Pull nomic-embed-text model
	docker exec -it cef-ollama ollama pull nomic-embed-text

ollama-list: ## List installed ollama models
	docker exec -it cef-ollama ollama list

# Info
info: ## Show project info
	@echo "CEF - Context Engineering Framework"
	@echo "Author: mrmanna"
	@echo "Version: 0.1.0-SNAPSHOT"
	@echo ""
	@echo "Modules:"
	@echo "  - cef-framework (core library)"
	@echo "  - cef-example (medical domain example)"
	@echo ""
	@echo "Default ports:"
	@echo "  - Example App: http://localhost:8080"
	@echo "  - Ollama: http://localhost:11434"
	@echo "  - PostgreSQL: localhost:5432 (optional)"
	@echo "  - MinIO: http://localhost:9000 (optional)"
	@echo "  - MinIO Console: http://localhost:9001 (optional)"

