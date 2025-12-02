package org.ddse.ml.cef.benchmark;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.parser.ParsedDocument;
import org.ddse.ml.cef.parser.ParserOptions;
import org.ddse.ml.cef.parser.impl.CsvParser;
import org.ddse.ml.cef.graph.GraphStore;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class SapDataParser {

    private final GraphStore graphStore;
    private final CsvParser csvParser;
    private final KnowledgeIndexer knowledgeIndexer;

    private final Map<String, String> vendorNames = new HashMap<>();
    private final Map<String, String> vendorCities = new HashMap<>();
    private final Map<String, String> vendorCountries = new HashMap<>();
    private final Map<String, String> costCenterNames = new HashMap<>();
    private final Map<String, String> costCenterDepartments = new HashMap<>();
    private final Map<String, String> materialDescriptions = new HashMap<>();
    private final Map<String, List<String>> materialComponents = new HashMap<>();
    private final Map<String, List<String>> materialSuppliers = new HashMap<>();
    private final Map<String, Integer> supplierLeadTimes = new HashMap<>();
    private final Map<String, String> bomToMaterial = new HashMap<>();

    private static final DateTimeFormatter SAP_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    public SapDataParser(GraphStore graphStore, CsvParser csvParser, KnowledgeIndexer knowledgeIndexer) {
        this.graphStore = graphStore;
        this.csvParser = csvParser;
        this.knowledgeIndexer = knowledgeIndexer;
    }

    public void parseAndLoad() throws IOException {
        loadDepartments();
        loadBudgets();
        loadVendors();
        loadCostCenters();
        loadProjects();
        loadMaterials();
        loadBomStructure();
        loadProducts();
        loadPurchasingInfo();
        loadVendorSupplies();
        loadFinancials();
        loadCustomerOrders();
        addExternalEvents();
    }

    private void loadDepartments() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/DEPARTMENTS.csv");
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("DEPT_ID");
            Map<String, Object> props = new HashMap<>();
            props.put("name", row.get("NAME"));
            props.put("has_overrun", "Y".equals(row.get("HAS_OVERRUN")));

            Node node = new Node(UUID.nameUUIDFromBytes(("DEPT-" + id).getBytes()), "Department", props, null);
            graphStore.addNode(node).block();

            String deptChunk = String.format("**DEPARTMENT**\nDepartment ID: %s\nName: %s\nCost Overrun Status: %s",
                    id, row.get("NAME"), "Y".equals(row.get("HAS_OVERRUN")) ? "Has overruns" : "On budget");
            indexChunk(node.getId(), "CHUNK-DEPT-" + id, deptChunk,
                    Map.of("type", "department", "dept_id", id, "has_overrun", "Y".equals(row.get("HAS_OVERRUN"))));

            if ("Y".equals(row.get("HAS_OVERRUN"))) {
                UUID overrunUuid = UUID.nameUUIDFromBytes(("OVERRUN-" + id).getBytes());
                Node overrunNode = new Node(overrunUuid, "FinancialStatus",
                        Map.of("type", "overrun", "severity", "high"), null);
                graphStore.addNode(overrunNode).block();

                Edge edge = new Edge(UUID.randomUUID(), "HAS_OVERRUN", node.getId(), overrunUuid, Map.of(), 1.0);
                graphStore.addEdge(edge).block();
            }
        }
    }

    private void loadBudgets() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/BUDGETS.csv");
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("BUDGET_ID");
            String deptId = (String) row.get("DEPT_ID");
            String approvedVendors = (String) row.get("APPROVED_VENDORS");

            Map<String, Object> props = new HashMap<>();
            props.put("fiscal_year", row.get("FISCAL_YEAR"));
            props.put("approved_vendors", approvedVendors);

            Node node = new Node(UUID.nameUUIDFromBytes(("BUDGET-" + id).getBytes()), "Budget", props, null);
            graphStore.addNode(node).block();

            String budgetChunk = String.format(
                    "**BUDGET**\nBudget ID: %s\nDepartment: %s\nFiscal Year: %s\nApproved Vendors: %s",
                    id, deptId, row.get("FISCAL_YEAR"), approvedVendors);
            indexChunk(node.getId(), "CHUNK-BUDGET-" + id, budgetChunk,
                    Map.of("type", "budget", "budget_id", id, "dept_id", deptId));

            UUID deptUuid = UUID.nameUUIDFromBytes(("DEPT-" + deptId).getBytes());
            Edge edge = new Edge(UUID.randomUUID(), "HAS_BUDGET", deptUuid, node.getId(), Map.of(), 1.0);
            graphStore.addEdge(edge).block();
        }
    }

    private void loadProjects() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/PROJECTS.csv");
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("PROJECT_ID");
            String deptId = (String) row.get("DEPT_ID");
            Map<String, Object> props = new HashMap<>();
            props.put("name", row.get("NAME"));
            props.put("status", row.get("STATUS"));

            Node node = new Node(UUID.nameUUIDFromBytes(("PROJ-" + id).getBytes()), "Project", props, null);
            graphStore.addNode(node).block();

            String projChunk = String.format("**PROJECT**\nProject ID: %s\nName: %s\nDepartment: %s\nStatus: %s",
                    id, row.get("NAME"), deptId, row.get("STATUS"));
            indexChunk(node.getId(), "CHUNK-PROJ-" + id, projChunk,
                    Map.of("type", "project", "project_id", id, "dept_id", deptId, "status", row.get("STATUS")));

            UUID deptUuid = UUID.nameUUIDFromBytes(("DEPT-" + deptId).getBytes());
            Edge edge = new Edge(UUID.randomUUID(), "FUNDED_BY", node.getId(), deptUuid, Map.of(), 1.0);
            graphStore.addEdge(edge).block();
        }
    }

    private void loadVendors() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/LFA1.csv");
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("LIFNR");
            Map<String, Object> props = new HashMap<>();
            props.put("name", row.get("NAME1"));
            props.put("city", row.get("ORT01"));
            props.put("country", row.get("LAND1"));

            vendorNames.put(id, (String) row.get("NAME1"));
            vendorCities.put(id, (String) row.get("ORT01"));
            vendorCountries.put(id, (String) row.get("LAND1"));

            Node node = new Node(UUID.nameUUIDFromBytes(("VENDOR-" + id).getBytes()), "Vendor", props, null);
            graphStore.addNode(node).block();

            String vendorChunk = String.format("**VENDOR MASTER DATA**\nVendor ID: %s\nName: %s\nCity: %s\nCountry: %s",
                    id, row.get("NAME1"), row.get("ORT01"), row.get("LAND1"));
            indexChunk(node.getId(), "CHUNK-VENDOR-" + id, vendorChunk,
                    Map.of("type", "vendor_profile", "vendor_id", id));
        }
    }

    private void loadCostCenters() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/CSKS.csv");
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("KOSTL");
            String deptId = (String) row.get("ABTEI");
            Map<String, Object> props = new HashMap<>();
            props.put("name", row.get("KTEXT"));
            props.put("department", deptId);

            costCenterNames.put(id, (String) row.get("KTEXT"));
            costCenterDepartments.put(id, deptId);

            Node node = new Node(UUID.nameUUIDFromBytes(("CC-" + id).getBytes()), "CostCenter", props, null);
            graphStore.addNode(node).block();

            String ccChunk = String.format("**COST CENTER PROFILE**\nCost Center: %s\nName: %s\nDepartment: %s",
                    id, row.get("KTEXT"), deptId);
            indexChunk(node.getId(), "CHUNK-CC-" + id, ccChunk,
                    Map.of("type", "cost_center_profile", "cost_center_id", id, "department", deptId));

            UUID deptUuid = UUID.nameUUIDFromBytes(("DEPT-" + deptId).getBytes());
            Edge belongsEdge = new Edge(UUID.randomUUID(), "BELONGS_TO", node.getId(), deptUuid, Map.of(), 1.0);
            graphStore.addEdge(belongsEdge).block();

            Edge hasEdge = new Edge(UUID.randomUUID(), "HAS_COST_CENTER", deptUuid, node.getId(), Map.of(), 1.0);
            graphStore.addEdge(hasEdge).block();
        }
    }

    private void loadFinancials() throws IOException {
        Map<String, String> docDates = new HashMap<>();
        List<Map<String, Object>> headers = readCsv("sap_data/BKPF.csv");
        for (Map<String, Object> row : headers) {
            docDates.put((String) row.get("BELNR"), (String) row.get("BLDAT"));
        }

        List<Map<String, Object>> segments = readCsv("sap_data/BSEG.csv");
        for (Map<String, Object> row : segments) {
            String docId = (String) row.get("BELNR");
            String vendorId = (String) row.get("LIFNR");
            String costCenterId = (String) row.get("KOSTL");
            String amount = (String) row.get("DMBTR");
            String date = docDates.get(docId);
            String ledgerAccount = (String) row.get("HKONT");

            UUID invoiceUuid = UUID.nameUUIDFromBytes(("INVOICE-" + docId + "-" + row.get("BUZEI")).getBytes());
            Map<String, Object> invoiceProps = new HashMap<>();
            invoiceProps.put("doc_id", docId);
            invoiceProps.put("date", date);
            invoiceProps.put("amount", Double.parseDouble(amount));
            invoiceProps.put("year", row.get("GJAHR"));

            Node invoiceNode = new Node(invoiceUuid, "Invoice", invoiceProps, null);
            graphStore.addNode(invoiceNode).block();

            if (vendorId != null && !vendorId.isEmpty()) {
                UUID vendorUuid = UUID.nameUUIDFromBytes(("VENDOR-" + vendorId).getBytes());
                Edge edge = new Edge(UUID.randomUUID(), "PAID_TO", invoiceUuid, vendorUuid,
                        Map.of("amount", Double.parseDouble(amount)), 1.0);
                graphStore.addEdge(edge).block();
            }

            if (costCenterId != null && !costCenterId.isEmpty()) {
                UUID ccUuid = UUID.nameUUIDFromBytes(("CC-" + costCenterId).getBytes());
                Edge edge = new Edge(UUID.randomUUID(), "INCURRED_BY", invoiceUuid, ccUuid, Map.of(), 1.0);
                graphStore.addEdge(edge).block();

                if (vendorId != null && !vendorId.isEmpty()) {
                    UUID vendorUuid = UUID.nameUUIDFromBytes(("VENDOR-" + vendorId).getBytes());
                    Edge paysEdge = new Edge(UUID.randomUUID(), "PAYS", ccUuid, vendorUuid,
                            Map.of("amount", Double.parseDouble(amount)), 1.0);
                    graphStore.addEdge(paysEdge).block();
                }
            }

            indexTransactionChunk(invoiceUuid, docId, vendorId, costCenterId, amount, date, ledgerAccount);
        }
    }

    private void loadMaterials() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/MARA.csv");
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("MATNR");
            String projectId = (String) row.get("PROJECT_ID");
            Map<String, Object> props = new HashMap<>();
            props.put("description", row.get("MAKTX"));
            props.put("type", row.get("MTART"));

            materialDescriptions.put(id, (String) row.get("MAKTX"));

            Node node = new Node(UUID.nameUUIDFromBytes(("MAT-" + id).getBytes()), "Material", props, null);
            graphStore.addNode(node).block();

            String matChunk = String.format("**MATERIAL MASTER DATA**\nMaterial ID: %s\nDescription: %s\nType: %s",
                    id, row.get("MAKTX"), row.get("MTART"));
            indexChunk(node.getId(), "CHUNK-MAT-" + id, matChunk,
                    Map.of("type", "material_profile", "material_id", id, "material_type", row.get("MTART")));

            if (projectId != null && !projectId.isEmpty()) {
                UUID projectUuid = UUID.nameUUIDFromBytes(("PROJ-" + projectId).getBytes());
                Edge edge = new Edge(UUID.randomUUID(), "USED_IN", node.getId(), projectUuid, Map.of(), 1.0);
                graphStore.addEdge(edge).block();
            }
        }
    }

    private void loadProducts() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/PRODUCTS.csv");
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("PRODUCT_ID");
            Map<String, Object> props = new HashMap<>();
            props.put("name", row.get("NAME"));
            props.put("category", row.get("CATEGORY"));

            Node node = new Node(UUID.nameUUIDFromBytes(("PRODUCT-" + id).getBytes()), "Product", props, null);
            graphStore.addNode(node).block();

            String prodChunk = String.format("**PRODUCT**\nProduct ID: %s\nName: %s\nCategory: %s",
                    id, row.get("NAME"), row.get("CATEGORY"));
            indexChunk(node.getId(), "CHUNK-PRODUCT-" + id, prodChunk,
                    Map.of("type", "product", "product_id", id));

            // Link Product to Material if they share the same ID
            UUID materialUuid = UUID.nameUUIDFromBytes(("MAT-" + id).getBytes());
            Edge materializedAsEdge = new Edge(UUID.randomUUID(), "MATERIALIZED_AS", node.getId(), materialUuid,
                    Map.of(), 1.0);
            graphStore.addEdge(materializedAsEdge).block();

            // Reverse edge so we can navigate both ways
            Edge isProductEdge = new Edge(UUID.randomUUID(), "IS_PRODUCT", materialUuid, node.getId(), Map.of(), 1.0);
            graphStore.addEdge(isProductEdge).block();

            // Copy BOM structure to Product node (for queries starting from Product)
            List<String> components = materialComponents.getOrDefault(id, List.of());
            for (String componentId : components) {
                UUID componentUuid = UUID.nameUUIDFromBytes(("MAT-" + componentId).getBytes());
                Edge composedEdge = new Edge(UUID.randomUUID(), "COMPOSED_OF", node.getId(), componentUuid, Map.of(),
                        1.0);
                graphStore.addEdge(composedEdge).block();

                Edge containsEdge = new Edge(UUID.randomUUID(), "CONTAINS", node.getId(), componentUuid, Map.of(), 1.0);
                graphStore.addEdge(containsEdge).block();

                Edge madeFromEdge = new Edge(UUID.randomUUID(), "MADE_FROM", node.getId(), componentUuid, Map.of(),
                        1.0);
                graphStore.addEdge(madeFromEdge).block();
            }
        }
    }

    private void loadBomStructure() throws IOException {
        List<Map<String, Object>> mastRows = readCsv("sap_data/MAST.csv");
        for (Map<String, Object> row : mastRows) {
            bomToMaterial.put((String) row.get("STLNR"), (String) row.get("MATNR"));
        }

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
                graphStore.addEdge(composedEdge).block();

                Edge containsEdge = new Edge(UUID.randomUUID(), "CONTAINS", parentUuid, childUuid,
                        Map.of("quantity", Double.parseDouble(qty)), 1.0);
                graphStore.addEdge(containsEdge).block();

                Edge madeFromEdge = new Edge(UUID.randomUUID(), "MADE_FROM", parentUuid, childUuid,
                        Map.of("quantity", Double.parseDouble(qty)), 1.0);
                graphStore.addEdge(madeFromEdge).block();

                Edge componentEdge = new Edge(UUID.randomUUID(), "COMPONENT_OF", childUuid, parentUuid,
                        Map.of("quantity", Double.parseDouble(qty)), 1.0);
                graphStore.addEdge(componentEdge).block();

                materialComponents.computeIfAbsent(parentId, k -> new ArrayList<>()).add(componentId);
            }
        }
    }

    private void loadPurchasingInfo() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/EINA.csv");
        for (Map<String, Object> row : rows) {
            String matId = (String) row.get("MATNR");
            String vendorId = (String) row.get("LIFNR");
            String leadTime = (String) row.get("APLFZ");

            UUID matUuid = UUID.nameUUIDFromBytes(("MAT-" + matId).getBytes());
            UUID vendorUuid = UUID.nameUUIDFromBytes(("VENDOR-" + vendorId).getBytes());

            Edge edge = new Edge(UUID.randomUUID(), "SUPPLIED_BY", matUuid, vendorUuid,
                    Map.of("lead_time_days", Integer.parseInt(leadTime)), 1.0);
            graphStore.addEdge(edge).block();

            materialSuppliers.computeIfAbsent(matId, k -> new ArrayList<>()).add(vendorId);
            supplierLeadTimes.put(matId + ":" + vendorId, Integer.parseInt(leadTime));
        }
    }

    private void loadVendorSupplies() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/VENDOR_SUPPLIES.csv");
        for (Map<String, Object> row : rows) {
            String vendorId = (String) row.get("VENDOR_ID");
            String componentId = (String) row.get("COMPONENT_ID");

            UUID vendorUuid = UUID.nameUUIDFromBytes(("VENDOR-" + vendorId).getBytes());
            UUID componentUuid = UUID.nameUUIDFromBytes(("MAT-" + componentId).getBytes());

            // SUPPLIES: Vendor→Material (outgoing from Vendor)
            Edge suppliesEdge = new Edge(UUID.randomUUID(), "SUPPLIES", vendorUuid, componentUuid, Map.of(), 1.0);
            graphStore.addEdge(suppliesEdge).block();

            // SUPPLIED_BY: Vendor→Material (outgoing from Vendor - for pattern matching)
            Edge suppliedByOutgoingEdge = new Edge(UUID.randomUUID(), "SUPPLIED_BY", vendorUuid, componentUuid,
                    Map.of(), 1.0);
            graphStore.addEdge(suppliedByOutgoingEdge).block();

            // SUPPLIED_BY: Material→Vendor (incoming to Vendor - for reverse traversal)
            Edge suppliedByIncomingEdge = new Edge(UUID.randomUUID(), "SUPPLIED_BY", componentUuid, vendorUuid,
                    Map.of(), 1.0);
            graphStore.addEdge(suppliedByIncomingEdge).block();

            // Also link to Product if this component is a finished product (check if
            // Product node exists)
            UUID productUuid = UUID.nameUUIDFromBytes(("PRODUCT-" + componentId).getBytes());
            if (graphStore.getNode(productUuid).block() != null) {
                // SUPPLIED_BY: Vendor→Product (outgoing from Vendor)
                Edge vendorToProductEdge = new Edge(UUID.randomUUID(), "SUPPLIED_BY", vendorUuid, productUuid, Map.of(),
                        1.0);
                graphStore.addEdge(vendorToProductEdge).block();

                // SUPPLIED_BY: Product→Vendor (incoming to Vendor)
                Edge productToVendorEdge = new Edge(UUID.randomUUID(), "SUPPLIED_BY", productUuid, vendorUuid, Map.of(),
                        1.0);
                graphStore.addEdge(productToVendorEdge).block();
            }
        }
    }

    private void loadCustomerOrders() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/CUSTOMER_ORDERS.csv");
        for (Map<String, Object> row : rows) {
            String orderId = (String) row.get("ORDER_ID");
            String productId = (String) row.get("PRODUCT_ID");
            Map<String, Object> props = new HashMap<>();
            props.put("customer", row.get("CUSTOMER"));
            props.put("quantity", Integer.parseInt((String) row.get("QUANTITY")));
            props.put("order_date", row.get("ORDER_DATE"));
            props.put("status", row.get("STATUS"));

            Node node = new Node(UUID.nameUUIDFromBytes(("ORDER-" + orderId).getBytes()), "CustomerOrder", props,
                    null);
            graphStore.addNode(node).block();

            String orderChunk = String.format(
                    "**CUSTOMER ORDER**\nOrder ID: %s\nProduct: %s\nCustomer: %s\nQuantity: %s\nStatus: %s",
                    orderId, productId, row.get("CUSTOMER"), row.get("QUANTITY"), row.get("STATUS"));
            indexChunk(node.getId(), "CHUNK-ORDER-" + orderId, orderChunk,
                    Map.of("type", "customer_order", "order_id", orderId, "product_id", productId));

            // Link to Product node (which then links to Material via MATERIALIZED_AS)
            UUID productUuid = UUID.nameUUIDFromBytes(("PRODUCT-" + productId).getBytes());
            Edge ordersEdge = new Edge(UUID.randomUUID(), "ORDERS", node.getId(), productUuid,
                    Map.of("quantity", Integer.parseInt((String) row.get("QUANTITY"))), 1.0);
            graphStore.addEdge(ordersEdge).block();

            // Also create reverse edge from Product to Order for easier traversal
            Edge orderedInEdge = new Edge(UUID.randomUUID(), "ORDERED_IN", productUuid, node.getId(),
                    Map.of("quantity", Integer.parseInt((String) row.get("QUANTITY"))), 1.0);
            graphStore.addEdge(orderedInEdge).block();
        }
    }

    private void addExternalEvents() {
        UUID eventUuid = UUID.nameUUIDFromBytes("EVENT-Typhoon".getBytes());
        Map<String, Object> props = new HashMap<>();
        props.put("name", "Super Typhoon Kong-rey");
        props.put("type", "Natural Disaster");
        props.put("severity", "High");
        props.put("start_date", "2025-11-01");
        Node eventNode = new Node(eventUuid, "Event", props, null);
        graphStore.addNode(eventNode).block();

        UUID locUuid = UUID.nameUUIDFromBytes("LOC-TW".getBytes());
        Node locNode = new Node(locUuid, "Location", Map.of("name", "Taiwan", "code", "TW"), null);
        graphStore.addNode(locNode).block();

        Edge disruptEdge = new Edge(UUID.randomUUID(), "AFFECTS_LOCATION", eventUuid, locUuid, Map.of(), 1.0);
        graphStore.addEdge(disruptEdge).block();

        Edge affectedByEdge = new Edge(UUID.randomUUID(), "AFFECTED_BY", locUuid, eventUuid, Map.of(), 1.0);
        graphStore.addEdge(affectedByEdge).block();

        UUID vendorUuid = UUID.nameUUIDFromBytes("VENDOR-V-2001".getBytes());

        // Bidirectional: Vendor LOCATED_IN Location (incoming)
        Edge locEdge = new Edge(UUID.randomUUID(), "LOCATED_IN", vendorUuid, locUuid, Map.of(), 1.0);
        graphStore.addEdge(locEdge).block();

        // Bidirectional: Location LOCATED_IN Vendor (outgoing - for pattern traversal)
        Edge locReverseEdge = new Edge(UUID.randomUUID(), "LOCATED_IN", locUuid, vendorUuid, Map.of(), 1.0);
        graphStore.addEdge(locReverseEdge).block();

        // Also HAS_VENDOR for semantic clarity
        Edge hasVendorEdge = new Edge(UUID.randomUUID(), "HAS_VENDOR", locUuid, vendorUuid, Map.of(), 1.0);
        graphStore.addEdge(hasVendorEdge).block();

        createEventChunks(eventUuid, locUuid, vendorUuid);
    }

    private List<Map<String, Object>> readCsv(String path) throws IOException {
        ParserOptions options = new ParserOptions();
        options.set(ParserOptions.SKIP_HEADER, true);

        ParsedDocument doc = csvParser.parse(new ClassPathResource(path).getInputStream(), options).block();
        return doc != null ? doc.getRecords() : List.of();
    }

    private void indexTransactionChunk(UUID invoiceUuid, String docId, String vendorId, String costCenterId,
            String amountRaw, String sapDate, String ledgerAccount) {
        double amount = Double.parseDouble(amountRaw);
        LocalDate parsedDate = parseDate(sapDate);
        String isoDate = parsedDate != null ? parsedDate.toString() : sapDate;
        String quarter = parsedDate != null ? determineQuarter(parsedDate) : "Unknown";

        String vendorName = vendorNames.getOrDefault(vendorId, vendorId != null ? vendorId : "Unknown Vendor");
        String costCenterName = costCenterNames.getOrDefault(costCenterId, costCenterId != null ? costCenterId : "N/A");
        String department = costCenterDepartments.getOrDefault(costCenterId, "Unknown");

        boolean isEngineering = "ENG".equalsIgnoreCase(department);
        boolean isSuspiciousVendor = vendorId == null || vendorId.isBlank() || vendorName.startsWith("Unknown");

        StringBuilder content = new StringBuilder();
        content.append("Invoice ").append(docId).append(" posted on ").append(isoDate)
                .append(" captured ").append(String.format(Locale.US, "%.2f", amount)).append(" USD")
                .append(" against cost center ").append(costCenterName)
                .append(" (department: ").append(department).append(")");

        if (isEngineering) {
            content.append(" associated with the Engineering department's software subscription budget.");
        } else {
            content.append(" in the ").append(department).append(" organization.");
        }

        content.append(" Vendor recorded: ").append(vendorName).append(".");

        if (isSuspiciousVendor) {
            content.append(" Vendor master data incomplete — flag for manual review.");
        }

        if (ledgerAccount != null && !ledgerAccount.isBlank()) {
            content.append(" Posted to GL account ").append(ledgerAccount).append('.');
        }

        content.append(" Period: ").append(quarter).append('.');

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("docId", docId);
        metadata.put("vendorId", vendorId);
        metadata.put("vendorName", vendorName);
        metadata.put("costCenterId", costCenterId);
        metadata.put("costCenterName", costCenterName);
        metadata.put("amount", amount);
        metadata.put("date", isoDate);
        metadata.put("quarter", quarter);
        metadata.put("ledgerAccount", ledgerAccount);
        metadata.put("department", department);
        metadata.put("suspiciousVendor", isSuspiciousVendor);

        indexChunk(invoiceUuid, "CHUNK-TRANS-" + docId + "-" + costCenterId + "-" + vendorId, content.toString(),
                metadata);
    }

    private void createEventChunks(UUID eventUuid, UUID locationUuid, UUID vendorUuid) {
        String vendorId = "V-2001";
        String vendorName = vendorNames.getOrDefault(vendorId, "Taiwan Supplier");
        String vendorCity = vendorCities.getOrDefault(vendorId, "Hsinchu");
        String vendorCountry = vendorCountries.getOrDefault(vendorId, "TW");

        UUID holidayLaptopUuid = UUID.nameUUIDFromBytes("MAT-M-9000".getBytes());
        List<String> components = materialComponents.getOrDefault("M-9000", List.of());
        List<String> componentNames = components.stream()
                .map(id -> materialDescriptions.getOrDefault(id, id))
                .collect(Collectors.toList());

        String supplySummary = componentNames.isEmpty()
                ? "Holiday Laptop Pro bill of materials recorded."
                : "Holiday Laptop Pro requires components: " + String.join(", ", componentNames) + '.';

        String leadTimeDetail = "14";
        Integer cpuLead = supplierLeadTimes.get("M-7001:" + vendorId);
        Integer gpuLead = supplierLeadTimes.get("M-7002:" + vendorId);
        if (cpuLead != null && gpuLead != null) {
            leadTimeDetail = cpuLead.equals(gpuLead)
                    ? cpuLead.toString()
                    : cpuLead + "/" + gpuLead;
        } else if (cpuLead != null) {
            leadTimeDetail = cpuLead.toString();
        } else if (gpuLead != null) {
            leadTimeDetail = gpuLead.toString();
        }

        String eventContent = String.format(Locale.US,
                "Super Typhoon Kong-rey stalled shipments in Taiwan and directly impacted %s in %s, %s." +
                        " Holiday Laptop Pro components (CPU Ryzen 9 and GPU RTX 5000) depend on this supplier,"
                        + " driving schedule risks for December deliveries.",
                vendorName, vendorCity, vendorCountry);

        Map<String, Object> eventMetadata = new LinkedHashMap<>();
        eventMetadata.put("event", "Typhoon Kong-rey");
        eventMetadata.put("location", "Taiwan");
        eventMetadata.put("affectedVendor", vendorName);
        eventMetadata.put("leadTimeDays", leadTimeDetail);

        indexChunk(eventUuid, "CHUNK-TYPHOON-IMPACT", eventContent, eventMetadata);

        String materialContent = String.format(Locale.US,
                "%s %s Lead Time: %s days. Typhoon disruptions in Taiwan extend the Holiday Laptop delivery queue." +
                        " %s",
                vendorName,
                vendorCity, leadTimeDetail, supplySummary);

        Map<String, Object> materialMetadata = new LinkedHashMap<>();
        materialMetadata.put("material", "Holiday Laptop Pro");
        materialMetadata.put("criticalSupplier", vendorName);
        materialMetadata.put("leadTimeDays", leadTimeDetail);
        materialMetadata.put("components", componentNames);

        indexChunk(holidayLaptopUuid, "CHUNK-HOLIDAY-LAPTOP-SUPPLY", materialContent, materialMetadata);
    }

    private void indexChunk(UUID linkedNodeId, String chunkKey, String content, Map<String, Object> metadata) {
        Chunk chunk = new Chunk();
        chunk.setId(UUID.nameUUIDFromBytes(chunkKey.getBytes()));
        chunk.setLinkedNodeId(linkedNodeId);
        chunk.setContent(content);
        chunk.setMetadata(metadata);
        chunk.setNew(false);
        knowledgeIndexer.indexChunk(chunk).block();
    }

    private LocalDate parseDate(String sapDate) {
        if (sapDate == null || sapDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(sapDate, SAP_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String determineQuarter(LocalDate date) {
        int quarter = ((date.getMonthValue() - 1) / 3) + 1;
        return "Q" + quarter + " " + date.getYear();
    }
}
