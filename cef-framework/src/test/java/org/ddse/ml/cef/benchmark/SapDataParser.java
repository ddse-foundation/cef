package org.ddse.ml.cef.benchmark;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.parser.ParsedDocument;
import org.ddse.ml.cef.parser.ParserOptions;
import org.ddse.ml.cef.parser.impl.CsvParser;
import org.ddse.ml.cef.storage.GraphStore;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.*;

public class SapDataParser {

    private final GraphStore graphStore;
    private final CsvParser csvParser;

    public SapDataParser(GraphStore graphStore, CsvParser csvParser) {
        this.graphStore = graphStore;
        this.csvParser = csvParser;
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
        }
    }

    private void loadMaterials() throws IOException {
        List<Map<String, Object>> rows = readCsv("sap_data/MARA.csv");
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("MATNR");
            Map<String, Object> props = new HashMap<>();
            props.put("description", row.get("MAKTX"));
            props.put("type", row.get("MTART"));

            Node node = new Node(UUID.nameUUIDFromBytes(("MAT-" + id).getBytes()), "Material", props, null);
            graphStore.addNode(node).block();
        }
    }

    private void loadBomStructure() throws IOException {
        // Map BOM ID to Parent Material
        Map<String, String> bomToMaterial = new HashMap<>();
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
    }

    private List<Map<String, Object>> readCsv(String path) throws IOException {
        ParserOptions options = new ParserOptions();
        options.set(ParserOptions.SKIP_HEADER, true);

        ParsedDocument doc = csvParser.parse(new ClassPathResource(path).getInputStream(), options).block();
        return doc != null ? doc.getRecords() : List.of();
    }
}
