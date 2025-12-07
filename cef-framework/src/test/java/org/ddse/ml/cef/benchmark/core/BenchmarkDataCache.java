package org.ddse.ml.cef.benchmark.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.benchmark.dataset.MedicalDataset;
import org.ddse.ml.cef.benchmark.dataset.SapDataset;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton cache for benchmark datasets.
 * Loads data ONCE per JVM and reuses across all benchmark runs.
 * 
 * <p>This eliminates redundant JSON parsing and object creation
 * when running benchmarks against multiple backends.</p>
 *
 * @author mrmanna
 * @since v0.6
 */
public class BenchmarkDataCache {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkDataCache.class);
    
    private static final BenchmarkDataCache INSTANCE = new BenchmarkDataCache();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Cached datasets
    private volatile MedicalDataset medicalDataset;
    private volatile SapDataset sapDataset;
    
    // Thread-safe loading flags
    private final Map<String, Boolean> loadingFlags = new ConcurrentHashMap<>();

    private BenchmarkDataCache() {
        // Private constructor for singleton
    }

    public static BenchmarkDataCache getInstance() {
        return INSTANCE;
    }

    /**
     * Get the medical dataset, loading it if not already cached.
     */
    public synchronized MedicalDataset getMedicalDataset() {
        if (medicalDataset == null) {
            loadMedicalDataset();
        }
        return medicalDataset;
    }

    /**
     * Get the SAP dataset, loading it if not already cached.
     */
    public synchronized SapDataset getSapDataset() {
        if (sapDataset == null) {
            loadSapDataset();
        }
        return sapDataset;
    }

    /**
     * Load medical benchmark data from JSON.
     */
    private void loadMedicalDataset() {
        logger.info("Loading medical dataset into cache (one-time operation)...");
        long startTime = System.currentTimeMillis();

        try {
            JsonNode root = objectMapper.readTree(
                    new ClassPathResource("medical_benchmark_data.json").getInputStream());

            List<Node> nodes = new ArrayList<>();
            List<Edge> edges = new ArrayList<>();
            List<Chunk> chunks = new ArrayList<>();
            Set<String> labels = new HashSet<>();

            // Parse nodes
            for (JsonNode nodeJson : root.get("nodes")) {
                UUID id = UUID.fromString(nodeJson.get("id").asText());
                String label = nodeJson.get("label").asText();
                Map<String, Object> props = objectMapper.convertValue(nodeJson.get("properties"), Map.class);
                
                Node node = new Node(id, label, props, null);
                node.setNew(false);
                nodes.add(node);
                labels.add(label);
            }

            // Parse edges
            for (JsonNode edgeJson : root.get("edges")) {
                UUID id = UUID.fromString(edgeJson.get("id").asText());
                UUID sourceId = UUID.fromString(edgeJson.get("sourceNodeId").asText());
                UUID targetId = UUID.fromString(edgeJson.get("targetNodeId").asText());
                String type = edgeJson.get("relationType").asText();
                Map<String, Object> props = objectMapper.convertValue(edgeJson.get("properties"), Map.class);
                
                Edge edge = new Edge(id, type, sourceId, targetId, props, 1.0);
                edge.setNew(false);
                edges.add(edge);
            }

            // Parse chunks
            if (root.has("chunks")) {
                for (JsonNode chunkJson : root.get("chunks")) {
                    Chunk chunk = new Chunk();
                    chunk.setId(UUID.fromString(chunkJson.get("id").asText()));
                    chunk.setContent(chunkJson.get("content").asText());
                    chunk.setNew(false);

                    if (chunkJson.has("linkedNodeId")) {
                        chunk.setLinkedNodeId(UUID.fromString(chunkJson.get("linkedNodeId").asText()));
                    }
                    if (chunkJson.has("metadata")) {
                        chunk.setMetadata(objectMapper.convertValue(chunkJson.get("metadata"), Map.class));
                    }
                    chunks.add(chunk);
                }
            }

            medicalDataset = new MedicalDataset(nodes, edges, chunks, new ArrayList<>(labels));

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Medical dataset loaded: {} nodes, {} edges, {} chunks in {}ms",
                    nodes.size(), edges.size(), chunks.size(), duration);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load medical benchmark data", e);
        }
    }

    /**
     * Load SAP benchmark data from CSV files.
     * Parses CSV files from sap_data/ directory and builds nodes, edges, chunks.
     */
    private void loadSapDataset() {
        logger.info("Loading SAP dataset into cache (one-time operation)...");
        long startTime = System.currentTimeMillis();

        try {
            List<Node> nodes = new ArrayList<>();
            List<Edge> edges = new ArrayList<>();
            List<Chunk> chunks = new ArrayList<>();
            Set<String> labels = new HashSet<>();

            // Track lookups for building relationships
            Map<String, String> vendorNames = new HashMap<>();
            Map<String, String> costCenterNames = new HashMap<>();
            Map<String, String> costCenterDepts = new HashMap<>();
            Map<String, String> materialDescriptions = new HashMap<>();
            Map<String, String> bomToMaterial = new HashMap<>();
            Map<String, List<String>> materialComponents = new HashMap<>();

            // 1. Load Departments
            loadDepartments(nodes, edges, chunks, labels);
            
            // 2. Load Vendors
            loadVendors(nodes, chunks, labels, vendorNames);
            
            // 3. Load Cost Centers
            loadCostCenters(nodes, edges, chunks, labels, costCenterNames, costCenterDepts);
            
            // 4. Load Projects
            loadProjects(nodes, edges, chunks, labels);
            
            // 5. Load Materials
            loadMaterials(nodes, edges, chunks, labels, materialDescriptions);
            
            // 6. Load Products
            loadProducts(nodes, edges, chunks, labels);
            
            // 7. Load BOM Structure
            loadBomStructure(edges, bomToMaterial, materialComponents);
            
            // 8. Load Vendor Supplies
            loadVendorSupplies(edges);
            
            // 9. Load Customer Orders
            loadCustomerOrders(nodes, edges, chunks, labels);
            
            // 10. Load Financial Transactions (simplified)
            loadFinancials(nodes, edges, chunks, labels, vendorNames, costCenterNames, costCenterDepts);
            
            // 11. Add External Events (Typhoon scenario)
            addExternalEvents(nodes, edges, chunks, labels);

            sapDataset = new SapDataset(nodes, edges, chunks, new ArrayList<>(labels));

            long duration = System.currentTimeMillis() - startTime;
            logger.info("SAP dataset loaded: {} nodes, {} edges, {} chunks in {}ms",
                    nodes.size(), edges.size(), chunks.size(), duration);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load SAP benchmark data", e);
        }
    }
    
    private void loadDepartments(List<Node> nodes, List<Edge> edges, List<Chunk> chunks, Set<String> labels) throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/DEPARTMENTS.csv");
        labels.add("Department");
        labels.add("FinancialStatus");
        
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("DEPT_ID");
            boolean hasOverrun = "Y".equals(row.get("HAS_OVERRUN"));
            
            Map<String, Object> props = new HashMap<>();
            props.put("name", row.get("NAME"));
            props.put("has_overrun", hasOverrun);
            
            Node node = new Node(UUID.nameUUIDFromBytes(("DEPT-" + id).getBytes()), "Department", props, null);
            node.setNew(false);
            nodes.add(node);
            
            // Create chunk
            String content = String.format("**DEPARTMENT**\nDepartment ID: %s\nName: %s\nCost Overrun Status: %s",
                    id, row.get("NAME"), hasOverrun ? "Has overruns" : "On budget");
            Chunk chunk = createChunk("CHUNK-DEPT-" + id, node.getId(), content,
                    Map.of("type", "department", "dept_id", id, "has_overrun", hasOverrun));
            chunks.add(chunk);
            
            // If has overrun, create FinancialStatus node and edge
            if (hasOverrun) {
                UUID overrunId = UUID.nameUUIDFromBytes(("OVERRUN-" + id).getBytes());
                Node overrunNode = new Node(overrunId, "FinancialStatus",
                        Map.of("type", "overrun", "severity", "high"), null);
                overrunNode.setNew(false);
                nodes.add(overrunNode);
                
                Edge edge = new Edge(UUID.randomUUID(), "HAS_OVERRUN", node.getId(), overrunId, Map.of(), 1.0);
                edge.setNew(false);
                edges.add(edge);
            }
        }
    }
    
    private void loadVendors(List<Node> nodes, List<Chunk> chunks, Set<String> labels,
                            Map<String, String> vendorNames) throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/LFA1.csv");
        labels.add("Vendor");
        
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("LIFNR");
            String name = (String) row.get("NAME1");
            vendorNames.put(id, name);
            
            Map<String, Object> props = new HashMap<>();
            props.put("name", name);
            props.put("city", row.get("ORT01"));
            props.put("country", row.get("LAND1"));
            
            Node node = new Node(UUID.nameUUIDFromBytes(("VENDOR-" + id).getBytes()), "Vendor", props, null);
            node.setNew(false);
            nodes.add(node);
            
            String content = String.format("**VENDOR MASTER DATA**\nVendor ID: %s\nName: %s\nCity: %s\nCountry: %s",
                    id, name, row.get("ORT01"), row.get("LAND1"));
            Chunk chunk = createChunk("CHUNK-VENDOR-" + id, node.getId(), content,
                    Map.of("type", "vendor_profile", "vendor_id", id));
            chunks.add(chunk);
        }
    }
    
    private void loadCostCenters(List<Node> nodes, List<Edge> edges, List<Chunk> chunks, Set<String> labels,
                                Map<String, String> costCenterNames, Map<String, String> costCenterDepts) throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/CSKS.csv");
        labels.add("CostCenter");
        
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("KOSTL");
            String name = (String) row.get("KTEXT");
            String deptId = (String) row.get("ABTEI");
            
            costCenterNames.put(id, name);
            costCenterDepts.put(id, deptId);
            
            Map<String, Object> props = new HashMap<>();
            props.put("name", name);
            props.put("department", deptId);
            
            Node node = new Node(UUID.nameUUIDFromBytes(("CC-" + id).getBytes()), "CostCenter", props, null);
            node.setNew(false);
            nodes.add(node);
            
            String content = String.format("**COST CENTER PROFILE**\nCost Center: %s\nName: %s\nDepartment: %s",
                    id, name, deptId);
            Chunk chunk = createChunk("CHUNK-CC-" + id, node.getId(), content,
                    Map.of("type", "cost_center_profile", "cost_center_id", id, "department", deptId));
            chunks.add(chunk);
            
            // Link to department
            UUID deptUuid = UUID.nameUUIDFromBytes(("DEPT-" + deptId).getBytes());
            Edge belongsEdge = new Edge(UUID.randomUUID(), "BELONGS_TO", node.getId(), deptUuid, Map.of(), 1.0);
            belongsEdge.setNew(false);
            edges.add(belongsEdge);
            
            Edge hasEdge = new Edge(UUID.randomUUID(), "HAS_COST_CENTER", deptUuid, node.getId(), Map.of(), 1.0);
            hasEdge.setNew(false);
            edges.add(hasEdge);
        }
    }
    
    private void loadProjects(List<Node> nodes, List<Edge> edges, List<Chunk> chunks, Set<String> labels) throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/PROJECTS.csv");
        labels.add("Project");
        
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("PROJECT_ID");
            String deptId = (String) row.get("DEPT_ID");
            String status = (String) row.get("STATUS");
            
            Map<String, Object> props = new HashMap<>();
            props.put("name", row.get("NAME"));
            props.put("status", status);
            
            Node node = new Node(UUID.nameUUIDFromBytes(("PROJ-" + id).getBytes()), "Project", props, null);
            node.setNew(false);
            nodes.add(node);
            
            String content = String.format("**PROJECT**\nProject ID: %s\nName: %s\nDepartment: %s\nStatus: %s",
                    id, row.get("NAME"), deptId, status);
            Chunk chunk = createChunk("CHUNK-PROJ-" + id, node.getId(), content,
                    Map.of("type", "project", "project_id", id, "dept_id", deptId, "status", status));
            chunks.add(chunk);
            
            // Link to department
            UUID deptUuid = UUID.nameUUIDFromBytes(("DEPT-" + deptId).getBytes());
            Edge edge = new Edge(UUID.randomUUID(), "FUNDED_BY", node.getId(), deptUuid, Map.of(), 1.0);
            edge.setNew(false);
            edges.add(edge);
        }
    }
    
    private void loadMaterials(List<Node> nodes, List<Edge> edges, List<Chunk> chunks, Set<String> labels,
                              Map<String, String> materialDescriptions) throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/MARA.csv");
        labels.add("Material");
        
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("MATNR");
            String desc = (String) row.get("MAKTX");
            String projectId = (String) row.get("PROJECT_ID");
            
            materialDescriptions.put(id, desc);
            
            Map<String, Object> props = new HashMap<>();
            props.put("description", desc);
            props.put("type", row.get("MTART"));
            
            Node node = new Node(UUID.nameUUIDFromBytes(("MAT-" + id).getBytes()), "Material", props, null);
            node.setNew(false);
            nodes.add(node);
            
            String content = String.format("**MATERIAL MASTER DATA**\nMaterial ID: %s\nDescription: %s\nType: %s",
                    id, desc, row.get("MTART"));
            Chunk chunk = createChunk("CHUNK-MAT-" + id, node.getId(), content,
                    Map.of("type", "material_profile", "material_id", id, "material_type", row.get("MTART")));
            chunks.add(chunk);
            
            // Link to project if specified
            if (projectId != null && !projectId.isEmpty()) {
                UUID projectUuid = UUID.nameUUIDFromBytes(("PROJ-" + projectId).getBytes());
                Edge edge = new Edge(UUID.randomUUID(), "USED_IN", node.getId(), projectUuid, Map.of(), 1.0);
                edge.setNew(false);
                edges.add(edge);
            }
        }
    }
    
    private void loadProducts(List<Node> nodes, List<Edge> edges, List<Chunk> chunks, Set<String> labels) throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/PRODUCTS.csv");
        labels.add("Product");
        
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("PRODUCT_ID");
            
            Map<String, Object> props = new HashMap<>();
            props.put("name", row.get("NAME"));
            props.put("category", row.get("CATEGORY"));
            
            Node node = new Node(UUID.nameUUIDFromBytes(("PRODUCT-" + id).getBytes()), "Product", props, null);
            node.setNew(false);
            nodes.add(node);
            
            String content = String.format("**PRODUCT**\nProduct ID: %s\nName: %s\nCategory: %s",
                    id, row.get("NAME"), row.get("CATEGORY"));
            Chunk chunk = createChunk("CHUNK-PRODUCT-" + id, node.getId(), content,
                    Map.of("type", "product", "product_id", id));
            chunks.add(chunk);
            
            // Link Product to Material
            UUID materialUuid = UUID.nameUUIDFromBytes(("MAT-" + id).getBytes());
            Edge matEdge = new Edge(UUID.randomUUID(), "MATERIALIZED_AS", node.getId(), materialUuid, Map.of(), 1.0);
            matEdge.setNew(false);
            edges.add(matEdge);
        }
    }
    
    private void loadBomStructure(List<Edge> edges, Map<String, String> bomToMaterial,
                                 Map<String, List<String>> materialComponents) throws IOException {
        // First load MAST to get BOM to Material mapping
        List<Map<String, Object>> mastRows = readCsv("sap_data/MAST.csv");
        for (Map<String, Object> row : mastRows) {
            bomToMaterial.put((String) row.get("STLNR"), (String) row.get("MATNR"));
        }
        
        // Then load STPO for BOM components
        List<Map<String, Object>> stpoRows = readCsv("sap_data/STPO.csv");
        for (Map<String, Object> row : stpoRows) {
            String bomId = (String) row.get("STLNR");
            String componentId = (String) row.get("IDNRK");
            String parentId = bomToMaterial.get(bomId);
            String qty = (String) row.get("MENGE");
            
            if (parentId != null) {
                UUID parentUuid = UUID.nameUUIDFromBytes(("MAT-" + parentId).getBytes());
                UUID childUuid = UUID.nameUUIDFromBytes(("MAT-" + componentId).getBytes());
                
                Edge composedEdge = new Edge(UUID.randomUUID(), "COMPOSED_OF", parentUuid, childUuid,
                        Map.of("quantity", Double.parseDouble(qty)), 1.0);
                composedEdge.setNew(false);
                edges.add(composedEdge);
                
                Edge containsEdge = new Edge(UUID.randomUUID(), "CONTAINS", parentUuid, childUuid,
                        Map.of("quantity", Double.parseDouble(qty)), 1.0);
                containsEdge.setNew(false);
                edges.add(containsEdge);
                
                materialComponents.computeIfAbsent(parentId, k -> new ArrayList<>()).add(componentId);
            }
        }
    }
    
    private void loadVendorSupplies(List<Edge> edges) throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/VENDOR_SUPPLIES.csv");
        
        for (Map<String, Object> row : rows) {
            String vendorId = (String) row.get("VENDOR_ID");
            String componentId = (String) row.get("COMPONENT_ID");
            
            UUID vendorUuid = UUID.nameUUIDFromBytes(("VENDOR-" + vendorId).getBytes());
            UUID componentUuid = UUID.nameUUIDFromBytes(("MAT-" + componentId).getBytes());
            
            Edge suppliesEdge = new Edge(UUID.randomUUID(), "SUPPLIES", vendorUuid, componentUuid, Map.of(), 1.0);
            suppliesEdge.setNew(false);
            edges.add(suppliesEdge);
            
            Edge suppliedByEdge = new Edge(UUID.randomUUID(), "SUPPLIED_BY", componentUuid, vendorUuid, Map.of(), 1.0);
            suppliedByEdge.setNew(false);
            edges.add(suppliedByEdge);
        }
    }
    
    private void loadCustomerOrders(List<Node> nodes, List<Edge> edges, List<Chunk> chunks, Set<String> labels) throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/CUSTOMER_ORDERS.csv");
        labels.add("CustomerOrder");
        
        for (Map<String, Object> row : rows) {
            String orderId = (String) row.get("ORDER_ID");
            String productId = (String) row.get("PRODUCT_ID");
            String status = (String) row.get("STATUS");
            
            Map<String, Object> props = new HashMap<>();
            props.put("customer", row.get("CUSTOMER"));
            props.put("quantity", Integer.parseInt((String) row.get("QUANTITY")));
            props.put("order_date", row.get("ORDER_DATE"));
            props.put("status", status);
            
            Node node = new Node(UUID.nameUUIDFromBytes(("ORDER-" + orderId).getBytes()), "CustomerOrder", props, null);
            node.setNew(false);
            nodes.add(node);
            
            String content = String.format(
                    "**CUSTOMER ORDER**\nOrder ID: %s\nProduct: %s\nCustomer: %s\nQuantity: %s\nStatus: %s",
                    orderId, productId, row.get("CUSTOMER"), row.get("QUANTITY"), status);
            Chunk chunk = createChunk("CHUNK-ORDER-" + orderId, node.getId(), content,
                    Map.of("type", "customer_order", "order_id", orderId, "product_id", productId, "status", status));
            chunks.add(chunk);
            
            // Link to Product
            UUID productUuid = UUID.nameUUIDFromBytes(("PRODUCT-" + productId).getBytes());
            Edge ordersEdge = new Edge(UUID.randomUUID(), "ORDERS", node.getId(), productUuid,
                    Map.of("quantity", Integer.parseInt((String) row.get("QUANTITY"))), 1.0);
            ordersEdge.setNew(false);
            edges.add(ordersEdge);
        }
    }
    
    private void loadFinancials(List<Node> nodes, List<Edge> edges, List<Chunk> chunks, Set<String> labels,
                               Map<String, String> vendorNames, Map<String, String> costCenterNames,
                               Map<String, String> costCenterDepts) throws IOException {
        labels.add("Invoice");
        
        // Load document headers for dates
        Map<String, String> docDates = new HashMap<>();
        List<Map<String, Object>> headers = readCsv("sap_data/BKPF.csv");
        for (Map<String, Object> row : headers) {
            docDates.put((String) row.get("BELNR"), (String) row.get("BLDAT"));
        }
        
        // Load line items
        List<Map<String, Object>> segments = readCsv("sap_data/BSEG.csv");
        for (Map<String, Object> row : segments) {
            String docId = (String) row.get("BELNR");
            String vendorId = (String) row.get("LIFNR");
            String costCenterId = (String) row.get("KOSTL");
            String amount = (String) row.get("DMBTR");
            String date = docDates.get(docId);
            
            UUID invoiceUuid = UUID.nameUUIDFromBytes(("INVOICE-" + docId + "-" + row.get("BUZEI")).getBytes());
            
            Map<String, Object> invoiceProps = new HashMap<>();
            invoiceProps.put("doc_id", docId);
            invoiceProps.put("date", date);
            invoiceProps.put("amount", Double.parseDouble(amount));
            
            Node invoiceNode = new Node(invoiceUuid, "Invoice", invoiceProps, null);
            invoiceNode.setNew(false);
            nodes.add(invoiceNode);
            
            // Create financial chunk
            String vendorName = vendorNames.getOrDefault(vendorId, vendorId != null ? vendorId : "Unknown");
            String ccName = costCenterNames.getOrDefault(costCenterId, costCenterId != null ? costCenterId : "N/A");
            String dept = costCenterDepts.getOrDefault(costCenterId, "Unknown");
            
            String content = String.format(
                    "Invoice %s posted on %s for %.2f USD against cost center %s (%s department). Vendor: %s.",
                    docId, date, Double.parseDouble(amount), ccName, dept, vendorName);
            Chunk chunk = createChunk("CHUNK-TRANS-" + docId + "-" + costCenterId, invoiceUuid, content,
                    Map.of("type", "transaction", "docId", docId, "amount", Double.parseDouble(amount)));
            chunks.add(chunk);
            
            // Link to vendor
            if (vendorId != null && !vendorId.isEmpty()) {
                UUID vendorUuid = UUID.nameUUIDFromBytes(("VENDOR-" + vendorId).getBytes());
                Edge paidEdge = new Edge(UUID.randomUUID(), "PAID_TO", invoiceUuid, vendorUuid,
                        Map.of("amount", Double.parseDouble(amount)), 1.0);
                paidEdge.setNew(false);
                edges.add(paidEdge);
            }
            
            // Link to cost center
            if (costCenterId != null && !costCenterId.isEmpty()) {
                UUID ccUuid = UUID.nameUUIDFromBytes(("CC-" + costCenterId).getBytes());
                Edge incurredEdge = new Edge(UUID.randomUUID(), "INCURRED_BY", invoiceUuid, ccUuid, Map.of(), 1.0);
                incurredEdge.setNew(false);
                edges.add(incurredEdge);
                
                // Also link cost center to vendor (PAYS relationship)
                if (vendorId != null && !vendorId.isEmpty()) {
                    UUID vendorUuid = UUID.nameUUIDFromBytes(("VENDOR-" + vendorId).getBytes());
                    Edge paysEdge = new Edge(UUID.randomUUID(), "PAYS", ccUuid, vendorUuid,
                            Map.of("amount", Double.parseDouble(amount)), 1.0);
                    paysEdge.setNew(false);
                    edges.add(paysEdge);
                }
            }
        }
    }
    
    private void addExternalEvents(List<Node> nodes, List<Edge> edges, List<Chunk> chunks, Set<String> labels) {
        labels.add("Event");
        labels.add("Location");
        
        // Typhoon event
        UUID eventUuid = UUID.nameUUIDFromBytes("EVENT-Typhoon".getBytes());
        Node eventNode = new Node(eventUuid, "Event",
                Map.of("name", "Super Typhoon Kong-rey", "type", "Natural Disaster", "severity", "High"), null);
        eventNode.setNew(false);
        nodes.add(eventNode);
        
        // Taiwan location
        UUID locUuid = UUID.nameUUIDFromBytes("LOC-TW".getBytes());
        Node locNode = new Node(locUuid, "Location", Map.of("name", "Taiwan", "code", "TW"), null);
        locNode.setNew(false);
        nodes.add(locNode);
        
        // Event affects location
        Edge affectsEdge = new Edge(UUID.randomUUID(), "AFFECTS_LOCATION", eventUuid, locUuid, Map.of(), 1.0);
        affectsEdge.setNew(false);
        edges.add(affectsEdge);
        
        // Vendor V-2001 is in Taiwan
        UUID vendorUuid = UUID.nameUUIDFromBytes("VENDOR-V-2001".getBytes());
        Edge locatedEdge = new Edge(UUID.randomUUID(), "LOCATED_IN", vendorUuid, locUuid, Map.of(), 1.0);
        locatedEdge.setNew(false);
        edges.add(locatedEdge);
        
        // Event chunk
        String eventContent = "Super Typhoon Kong-rey stalled shipments in Taiwan and directly impacted Taiwan Supplier. " +
                "Holiday Laptop Pro components (CPU Ryzen 9 and GPU RTX 5000) depend on this supplier, " +
                "driving schedule risks for December deliveries.";
        Chunk eventChunk = createChunk("CHUNK-TYPHOON-IMPACT", eventUuid, eventContent,
                Map.of("event", "Typhoon Kong-rey", "location", "Taiwan", "type", "supply_chain_disruption"));
        chunks.add(eventChunk);
        
        // Supply chain impact chunk linked to Holiday Laptop
        UUID laptopUuid = UUID.nameUUIDFromBytes("MAT-M-9000".getBytes());
        String supplyContent = "Taiwan Supplier in Hsinchu has 14 day lead time. Typhoon disruptions extend Holiday Laptop delivery queue. " +
                "Holiday Laptop Pro requires components: OLED Screen 15in, Li-Ion Battery 99Wh, Motherboard Main.";
        Chunk supplyChunk = createChunk("CHUNK-HOLIDAY-LAPTOP-SUPPLY", laptopUuid, supplyContent,
                Map.of("material", "Holiday Laptop Pro", "criticalSupplier", "Taiwan Supplier", "leadTimeDays", 14));
        chunks.add(supplyChunk);
    }
    
    private Chunk createChunk(String chunkKey, UUID linkedNodeId, String content, Map<String, Object> metadata) {
        Chunk chunk = new Chunk();
        chunk.setId(UUID.nameUUIDFromBytes(chunkKey.getBytes()));
        chunk.setLinkedNodeId(linkedNodeId);
        chunk.setContent(content);
        chunk.setMetadata(metadata);
        chunk.setNew(false);
        return chunk;
    }
    
    private List<Map<String, Object>> readCsv(String path) throws IOException {
        List<Map<String, Object>> records = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            
            String headerLine = reader.readLine();
            if (headerLine == null) return records;
            
            String[] headers = headerLine.split(",");
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1); // -1 to keep empty trailing fields
                Map<String, Object> record = new HashMap<>();
                for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                    record.put(headers[i].trim(), values[i].trim());
                }
                records.add(record);
            }
        }
        
        return records;
    }

    /**
     * Clear all cached data (useful for testing).
     */
    public synchronized void clearCache() {
        medicalDataset = null;
        sapDataset = null;
        logger.info("Benchmark data cache cleared");
    }

    /**
     * Check if medical data is loaded.
     */
    public boolean isMedicalDataLoaded() {
        return medicalDataset != null;
    }

    /**
     * Check if SAP data is loaded.
     */
    public boolean isSapDataLoaded() {
        return sapDataset != null;
    }
}
