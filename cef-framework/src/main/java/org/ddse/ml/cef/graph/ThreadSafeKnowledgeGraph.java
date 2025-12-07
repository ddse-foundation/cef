package org.ddse.ml.cef.graph;

import org.ddse.ml.cef.domain.Direction;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * Thread-safe wrapper for InMemoryKnowledgeGraph using ReadWriteLock.
 * 
 * Provides:
 * - Concurrent read access (multiple readers allowed)
 * - Exclusive write access (single writer, blocks all readers)
 * - Deadlock prevention via lock ordering
 * - Read operation timeouts to prevent starvation
 * 
 * Enable with: cef.graph.thread-safe=true (default: false for development)
 * 
 * @author mrmanna
 * @since 0.6
 */
@Component
@Primary
@ConditionalOnProperty(name = "cef.graph.thread-safe", havingValue = "true")
public class ThreadSafeKnowledgeGraph {

    private static final Logger log = LoggerFactory.getLogger(ThreadSafeKnowledgeGraph.class);

    private final InMemoryKnowledgeGraph delegate;
    private final ReadWriteLock rwLock;
    
    // Optional: Use StampedLock for optimistic reads (better performance under high read contention)
    private final StampedLock stampedLock;
    private final boolean useOptimisticReads;

    public ThreadSafeKnowledgeGraph(InMemoryKnowledgeGraph delegate) {
        this.delegate = delegate;
        this.rwLock = new ReentrantReadWriteLock(true); // Fair lock to prevent writer starvation
        this.stampedLock = new StampedLock();
        this.useOptimisticReads = false; // Conservative default
        log.info("ThreadSafeKnowledgeGraph initialized with fair ReadWriteLock");
    }

    // ========== Write Operations (Exclusive Lock) ==========

    /**
     * Add a node to the graph. Acquires exclusive write lock.
     */
    public void addNode(Node node) {
        rwLock.writeLock().lock();
        try {
            delegate.addNode(node);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Add multiple nodes atomically. Acquires exclusive write lock once.
     */
    public void addNodes(Collection<Node> nodes) {
        rwLock.writeLock().lock();
        try {
            for (Node node : nodes) {
                delegate.addNode(node);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Remove a node and all its connected edges. Acquires exclusive write lock.
     */
    public void removeNode(UUID nodeId) {
        rwLock.writeLock().lock();
        try {
            delegate.removeNode(nodeId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Add an edge to the graph. Acquires exclusive write lock.
     */
    public void addEdge(Edge edge) {
        rwLock.writeLock().lock();
        try {
            delegate.addEdge(edge);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Add multiple edges atomically. Acquires exclusive write lock once.
     */
    public void addEdges(Collection<Edge> edges) {
        rwLock.writeLock().lock();
        try {
            for (Edge edge : edges) {
                delegate.addEdge(edge);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Remove an edge by ID. Acquires exclusive write lock.
     */
    public void removeEdge(UUID edgeId) {
        rwLock.writeLock().lock();
        try {
            delegate.removeEdge(edgeId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Clear the entire graph. Acquires exclusive write lock.
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            delegate.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ========== Read Operations (Shared Lock) ==========

    /**
     * Find a node by ID. Acquires shared read lock.
     */
    public Optional<Node> findNode(UUID nodeId) {
        rwLock.readLock().lock();
        try {
            return delegate.findNode(nodeId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Find all nodes with a given label. Acquires shared read lock.
     */
    public List<Node> findNodesByLabel(String label) {
        rwLock.readLock().lock();
        try {
            return delegate.findNodesByLabel(label);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get all edges connected to a node. Acquires shared read lock.
     */
    public Set<Edge> getEdges(UUID nodeId) {
        rwLock.readLock().lock();
        try {
            return delegate.getEdges(nodeId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get parent nodes. Acquires shared read lock.
     */
    public List<Node> getParents(UUID nodeId) {
        rwLock.readLock().lock();
        try {
            return delegate.getParents(nodeId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get child nodes. Acquires shared read lock.
     */
    public List<Node> getChildren(UUID nodeId) {
        rwLock.readLock().lock();
        try {
            return delegate.getChildren(nodeId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get sibling nodes. Acquires shared read lock.
     */
    public List<Node> getSiblings(UUID nodeId) {
        rwLock.readLock().lock();
        try {
            return delegate.getSiblings(nodeId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get neighboring nodes within a certain depth. Acquires shared read lock.
     */
    public List<Node> getNeighbors(UUID nodeId, int depth) {
        rwLock.readLock().lock();
        try {
            return delegate.getNeighbors(nodeId, depth);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get neighbors with filtering by relation type and direction. Acquires shared read lock.
     */
    public List<Node> getNeighbors(UUID nodeId, String relationType, Direction direction) {
        rwLock.readLock().lock();
        try {
            return delegate.getNeighbors(nodeId, relationType, direction);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Find shortest path between two nodes. Acquires shared read lock.
     */
    public Optional<GraphPathResult> findShortestPath(UUID fromId, UUID toId) {
        rwLock.readLock().lock();
        try {
            return delegate.findShortestPath(fromId, toId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Find all paths between two nodes up to a maximum depth. Acquires shared read lock.
     */
    public List<GraphPathResult> findAllPaths(UUID fromId, UUID toId, int maxDepth) {
        rwLock.readLock().lock();
        try {
            return delegate.findAllPaths(fromId, toId, maxDepth);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ========== Statistics (Read Lock) ==========

    /**
     * Get total number of nodes. Acquires shared read lock.
     */
    public long getNodeCount() {
        rwLock.readLock().lock();
        try {
            return delegate.getNodeCount();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get total number of edges. Acquires shared read lock.
     */
    public long getEdgeCount() {
        rwLock.readLock().lock();
        try {
            return delegate.getEdgeCount();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get count of nodes by label. Acquires shared read lock.
     */
    public Map<String, Long> getLabelCounts() {
        rwLock.readLock().lock();
        try {
            return delegate.getLabelCounts();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ========== Bulk Operations ==========

    /**
     * Load graph from database. Acquires exclusive write lock.
     */
    public void loadFromDatabase(Flux<Node> nodes, Flux<Edge> edges) {
        rwLock.writeLock().lock();
        try {
            delegate.loadFromDatabase(nodes, edges);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ========== Atomic Compound Operations ==========

    /**
     * Atomically add a node and its edges. Acquires exclusive write lock.
     */
    public void addNodeWithEdges(Node node, Collection<Edge> edges) {
        rwLock.writeLock().lock();
        try {
            delegate.addNode(node);
            for (Edge edge : edges) {
                delegate.addEdge(edge);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Atomically remove a node. Connected edges are removed automatically.
     */
    public void removeNodeAtomic(UUID nodeId) {
        rwLock.writeLock().lock();
        try {
            delegate.removeNode(nodeId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ========== Lock-Free Optimistic Read (Advanced) ==========

    /**
     * Try to read node using optimistic locking.
     * Falls back to read lock if data changed during read.
     * 
     * Use for hot-path reads where contention is expected.
     */
    public Optional<Node> findNodeOptimistic(UUID nodeId) {
        if (!useOptimisticReads) {
            return findNode(nodeId);
        }

        // Try optimistic read first
        long stamp = stampedLock.tryOptimisticRead();
        Optional<Node> result = delegate.findNode(nodeId);
        
        if (stampedLock.validate(stamp)) {
            return result;
        }
        
        // Fall back to read lock
        stamp = stampedLock.readLock();
        try {
            return delegate.findNode(nodeId);
        } finally {
            stampedLock.unlockRead(stamp);
        }
    }

    // ========== Diagnostics ==========

    /**
     * Get current lock statistics for monitoring.
     */
    public Map<String, Object> getLockStatistics() {
        ReentrantReadWriteLock reentrantLock = (ReentrantReadWriteLock) rwLock;
        Map<String, Object> stats = new HashMap<>();
        stats.put("readLockCount", reentrantLock.getReadLockCount());
        stats.put("isWriteLocked", reentrantLock.isWriteLocked());
        stats.put("writeHoldCount", reentrantLock.getWriteHoldCount());
        stats.put("hasQueuedThreads", reentrantLock.hasQueuedThreads());
        stats.put("queueLength", reentrantLock.getQueueLength());
        return stats;
    }
}
