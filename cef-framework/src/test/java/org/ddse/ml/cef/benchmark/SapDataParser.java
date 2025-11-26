package org.ddse.ml.cef.benchmark;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.parser.ParsedDocument;
import org.ddse.ml.cef.parser.ParserOptions;
import org.ddse.ml.cef.parser.impl.CsvParser;
import org.ddse.ml.cef.storage.GraphStore;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        loadVendors();
        loadCostCenters();
        loadFinancials();
        loadMaterials();
        loadBomStructure();
        loadPurchasingInfo();
        addExternalEvents(); // Manually adding the "Typhoon" context
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
        }
    }

    private void loadCostCenters() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/CSKS.csv");
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("KOSTL");
            Map<String, Object> props = new HashMap<>();
            props.put("name", row.get("KTEXT"));
            props.put("department", row.get("ABTEI"));

            costCenterNames.put(id, (String) row.get("KTEXT"));
            costCenterDepartments.put(id, (String) row.get("ABTEI"));

            Node node = new Node(UUID.nameUUIDFromBytes(("CC-" + id).getBytes()), "CostCenter", props, null);
            graphStore.addNode(node).block();
        }
    }

    private void loadFinancials() throws IOException {
        // Load Headers (BKPF)
        Map<String, String> docDates = new HashMap<>();
        List<Map<String, Object>> headers = readCsv("sap_data/BKPF.csv");
        for (Map<String, Object> row : headers) {
            docDates.put((String) row.get("BELNR"), (String) row.get("BLDAT"));
        }

        // Load Segments (BSEG) and create Transaction Nodes + Edges
        List<Map<String, Object>> segments = readCsv("sap_data/BSEG.csv");
        for (Map<String, Object> row : segments) {
            String docId = (String) row.get("BELNR");
            String vendorId = (String) row.get("LIFNR");
            String costCenterId = (String) row.get("KOSTL");
            String amount = (String) row.get("DMBTR");
            String date = docDates.get(docId);
            String ledgerAccount = (String) row.get("HKONT");

            // Create Transaction Node
            UUID transUuid = UUID.nameUUIDFromBytes(("TRANS-" + docId + "-" + row.get("BUZEI")).getBytes());
            Map<String, Object> transProps = new HashMap<>();
            transProps.put("doc_id", docId);
            transProps.put("date", date);
            transProps.put("amount", Double.parseDouble(amount));
            transProps.put("year", row.get("GJAHR"));

            Node transNode = new Node(transUuid, "Transaction", transProps, null);
            graphStore.addNode(transNode).block();

            // Edge: Transaction -> Vendor (PAID_TO)
            if (vendorId != null && !vendorId.isEmpty()) {
                UUID vendorUuid = UUID.nameUUIDFromBytes(("VENDOR-" + vendorId).getBytes());
                Edge edge = new Edge(UUID.randomUUID(), "PAID_TO", transUuid, vendorUuid,
                        Map.of("amount", Double.parseDouble(amount)), 1.0);
                graphStore.addEdge(edge).block();
            }

            // Edge: Transaction -> CostCenter (INCURRED_BY)
            if (costCenterId != null && !costCenterId.isEmpty()) {
                UUID ccUuid = UUID.nameUUIDFromBytes(("CC-" + costCenterId).getBytes());
                Edge edge = new Edge(UUID.randomUUID(), "INCURRED_BY", transUuid, ccUuid, Map.of(), 1.0);
                graphStore.addEdge(edge).block();
            }

            indexTransactionChunk(transUuid, docId, vendorId, costCenterId, amount, date, ledgerAccount);
        }
    }

    private void loadMaterials() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/MARA.csv");
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("MATNR");
            Map<String, Object> props = new HashMap<>();
            props.put("description", row.get("MAKTX"));
            props.put("type", row.get("MTART"));

            materialDescriptions.put(id, (String) row.get("MAKTX"));

            Node node = new Node(UUID.nameUUIDFromBytes(("MAT-" + id).getBytes()), "Material", props, null);
            graphStore.addNode(node).block();
        }
    }

    private void loadBomStructure() throws IOException {
        List<Map<String, Object>> mastRows = readCsv("sap_data/MAST.csv");
        for (Map<String, Object> row : mastRows) {
            bomToMaterial.put((String) row.get("STLNR"), (String) row.get("MATNR"));
        }

        // Link Components to Parent Material
        List<Map<String, Object>> stpoRows = readCsv("sap_data/STPO.csv");
        for (Map<String, Object> row : stpoRows) {
            String bomId = (String) row.get("STLNR");
            String componentId = (String) row.get("IDNRK");
            String parentId = bomToMaterial.get(bomId);
            String qty = (String) row.get("MENGE");

            if (parentId != null) {
                UUID parentUuid = UUID.nameUUIDFromBytes(("MAT-" + parentId).getBytes());
                UUID childUuid = UUID.nameUUIDFromBytes(("MAT-" + componentId).getBytes());

                Edge edge = new Edge(UUID.randomUUID(), "COMPOSED_OF", parentUuid, childUuid,
                        Map.of("quantity", Double.parseDouble(qty)), 1.0);
                graphStore.addEdge(edge).block();

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

    private void addExternalEvents() {
        // Add "Typhoon" Event
        UUID eventUuid = UUID.nameUUIDFromBytes("EVENT-Typhoon".getBytes());
        Map<String, Object> props = new HashMap<>();
        props.put("name", "Super Typhoon Kong-rey");
        props.put("type", "Natural Disaster");
        props.put("severity", "High");
        props.put("start_date", "2025-11-01");
        Node eventNode = new Node(eventUuid, "Event", props, null);
        graphStore.addNode(eventNode).block();

        // Link Event to Location (Country)
        // Find Vendor "Taiwan Semi" (V-2001) -> Country "TW"
        // In a real graph, "Country" might be a node. Here, it's a property of Vendor.
        // To make the connection explicit for the "Butterfly Effect", we'll link the
        // Event to the Vendor directly via "DISRUPTED_BY"
        // Or better: Event -> DISRUPTS -> Location. Vendor -> LOCATED_IN -> Location.

        // Let's create a Location Node for Taiwan
        UUID locUuid = UUID.nameUUIDFromBytes("LOC-TW".getBytes());
        Node locNode = new Node(locUuid, "Location", Map.of("name", "Taiwan", "code", "TW"), null);
        graphStore.addNode(locNode).block();

        // Link Event -> Location
        Edge disruptEdge = new Edge(UUID.randomUUID(), "AFFECTS_LOCATION", eventUuid, locUuid, Map.of(), 1.0);
        graphStore.addEdge(disruptEdge).block();

        // Link Taiwan Vendors to Location
        // We know V-2001 is in TW from LFA1 generation
        UUID vendorUuid = UUID.nameUUIDFromBytes("VENDOR-V-2001".getBytes());
        Edge locEdge = new Edge(UUID.randomUUID(), "LOCATED_IN", vendorUuid, locUuid, Map.of(), 1.0);
        graphStore.addEdge(locEdge).block();

        createEventChunks(eventUuid, locUuid, vendorUuid);
    }

    private List<Map<String, Object>> readCsv(String path) throws IOException {
        ParserOptions options = new ParserOptions();
        options.set(ParserOptions.SKIP_HEADER, true);

        ParsedDocument doc = csvParser.parse(new ClassPathResource(path).getInputStream(), options).block();
        return doc != null ? doc.getRecords() : List.of();
    }

    private void indexTransactionChunk(UUID transUuid, String docId, String vendorId, String costCenterId,
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

        indexChunk(transUuid, "CHUNK-TRANS-" + docId + "-" + costCenterId + "-" + vendorId, content.toString(),
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
                        " %s", vendorName,
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
