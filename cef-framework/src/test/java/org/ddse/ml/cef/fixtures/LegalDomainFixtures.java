package org.ddse.ml.cef.fixtures;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pre-configured legal domain test fixtures.
 * Creates realistic legal contract knowledge graphs for integration tests.
 * 
 * <p>
 * Scenarios:
 * <ul>
 * <li>Software license agreement with parties and payment clauses</li>
 * <li>Consulting services agreement with deliverables and obligations</li>
 * </ul>
 * 
 * @author mrmanna
 */
@Component
public class LegalDomainFixtures {

    /**
     * Creates a software license agreement scenario.
     * 
     * <p>
     * Structure:
     * 
     * <pre>
     * Contract (C-2024-001, Software License Agreement)
     *   ├─[PARTY_TO]─→ Party (ACME Corp, Licensor)
     *   ├─[PARTY_TO]─→ Party (TechCo Inc, Licensee)
     *   ├─[HAS_CLAUSE]─→ Clause (Payment Terms, $50,000 annually)
     *   ├─[HAS_CLAUSE]─→ Clause (License Grant, Perpetual non-exclusive)
     *   └─[HAS_CLAUSE]─→ Clause (Termination, 30 days notice)
     * 
     * Obligations:
     *   Licensee -[HAS_OBLIGATION]→ Obligation (Annual payment)
     *   Licensor -[HAS_OBLIGATION]→ Obligation (Software updates)
     * 
     * Chunks (with real embeddings):
     *   - Payment terms explanation
     *   - License scope and restrictions
     *   - Termination procedures
     * </pre>
     * 
     * @param embeddingModel Real Ollama embedding model for generating embeddings
     * @return Complete legal scenario with nodes, edges, and chunks
     */
    public LegalScenario createSoftwareLicenseScenario(EmbeddingModel embeddingModel) {
        // Nodes
        Node contract = TestDataBuilder.node()
                .label("Contract")
                .property("contractId", "C-2024-001")
                .property("title", "Software License Agreement")
                .property("effectiveDate", "2024-01-01")
                .property("expiryDate", "2029-12-31")
                .property("status", "Active")
                .vectorizableContent(
                        "Software License Agreement between ACME Corporation and TechCo Inc for enterprise software")
                .build();

        Node acmeCorp = TestDataBuilder.node()
                .label("Party")
                .property("partyId", "ACME-001")
                .property("name", "ACME Corporation")
                .property("role", "Licensor")
                .property("entityType", "Corporation")
                .property("jurisdiction", "Delaware")
                .vectorizableContent("ACME Corporation, software vendor and licensor")
                .build();

        Node techCo = TestDataBuilder.node()
                .label("Party")
                .property("partyId", "TECH-001")
                .property("name", "TechCo Inc")
                .property("role", "Licensee")
                .property("entityType", "Corporation")
                .property("jurisdiction", "California")
                .vectorizableContent("TechCo Inc, technology company and software licensee")
                .build();

        Node paymentClause = TestDataBuilder.node()
                .label("Clause")
                .property("clauseId", "CL-001")
                .property("title", "Payment Terms")
                .property("section", "4.1")
                .property("category", "Financial")
                .vectorizableContent("Payment Terms: Licensee shall pay annual license fee of $50,000 USD")
                .build();

        Node licenseGrantClause = TestDataBuilder.node()
                .label("Clause")
                .property("clauseId", "CL-002")
                .property("title", "License Grant")
                .property("section", "2.1")
                .property("category", "Rights")
                .vectorizableContent("License Grant: Perpetual, non-exclusive, worldwide license to use the software")
                .build();

        Node terminationClause = TestDataBuilder.node()
                .label("Clause")
                .property("clauseId", "CL-003")
                .property("title", "Termination")
                .property("section", "8.1")
                .property("category", "Termination")
                .vectorizableContent("Either party may terminate with 30 days written notice")
                .build();

        Node paymentObligation = TestDataBuilder.node()
                .label("Obligation")
                .property("obligationId", "OB-001")
                .property("description", "Annual license fee payment")
                .property("amount", 50000)
                .property("currency", "USD")
                .property("frequency", "Annual")
                .property("dueDate", "January 1st each year")
                .vectorizableContent("Licensee obligation to pay $50,000 annual license fee")
                .build();

        Node updateObligation = TestDataBuilder.node()
                .label("Obligation")
                .property("obligationId", "OB-002")
                .property("description", "Provide software updates and patches")
                .property("frequency", "As needed")
                .property("sla", "Critical patches within 48 hours")
                .vectorizableContent("Licensor obligation to provide software maintenance and security updates")
                .build();

        // Edges
        Edge acmePartyTo = TestDataBuilder.edge()
                .from(contract.getId())
                .to(acmeCorp.getId())
                .relationType("PARTY_TO")
                .property("signedDate", "2023-12-15")
                .property("signatoryTitle", "CEO")
                .build();

        Edge techCoPartyTo = TestDataBuilder.edge()
                .from(contract.getId())
                .to(techCo.getId())
                .relationType("PARTY_TO")
                .property("signedDate", "2023-12-18")
                .property("signatoryTitle", "CTO")
                .build();

        Edge hasPaymentClause = TestDataBuilder.edge()
                .from(contract.getId())
                .to(paymentClause.getId())
                .relationType("HAS_CLAUSE")
                .property("importance", "Critical")
                .build();

        Edge hasLicenseClause = TestDataBuilder.edge()
                .from(contract.getId())
                .to(licenseGrantClause.getId())
                .relationType("HAS_CLAUSE")
                .property("importance", "Critical")
                .build();

        Edge hasTerminationClause = TestDataBuilder.edge()
                .from(contract.getId())
                .to(terminationClause.getId())
                .relationType("HAS_CLAUSE")
                .property("importance", "Important")
                .build();

        Edge techCoPaymentObligation = TestDataBuilder.edge()
                .from(techCo.getId())
                .to(paymentObligation.getId())
                .relationType("HAS_OBLIGATION")
                .property("startDate", "2024-01-01")
                .property("endDate", "2029-12-31")
                .build();

        Edge acmeUpdateObligation = TestDataBuilder.edge()
                .from(acmeCorp.getId())
                .to(updateObligation.getId())
                .relationType("HAS_OBLIGATION")
                .property("startDate", "2024-01-01")
                .property("endDate", "2029-12-31")
                .build();

        // Chunks with real embeddings
        Chunk paymentTermsDoc = TestDataBuilder.chunk()
                .content(
                        "Payment Terms and Conditions: The Licensee agrees to pay an annual license fee of fifty thousand "
                                +
                                "US Dollars ($50,000.00) for the perpetual license to the Software. Payment shall be made in advance "
                                +
                                "on January 1st of each year, commencing on the Effective Date. Late payments shall incur interest "
                                +
                                "at the rate of 1.5% per month or the maximum rate permitted by law, whichever is lower. "
                                +
                                "All payments are non-refundable except as explicitly provided in the termination clause. "
                                +
                                "Payment shall be made via wire transfer to the Licensor's designated bank account.")
                .linkedTo(paymentClause.getId())
                .withRealEmbedding(embeddingModel)
                .metadata("source", "contract_document.pdf")
                .metadata("page", 8)
                .metadata("section", "4.1")
                .build();

        Chunk licenseGrantDoc = TestDataBuilder.chunk()
                .content(
                        "Grant of License: Subject to the terms and conditions of this Agreement, Licensor hereby grants "
                                +
                                "to Licensee a perpetual, non-exclusive, non-transferable, worldwide license to use the Software "
                                +
                                "for Licensee's internal business operations only. This license includes the right to install and "
                                +
                                "use the Software on up to 100 concurrent users. Licensee may not sublicense, rent, lease, or "
                                +
                                "distribute the Software to third parties. The Software may be used across multiple geographic "
                                +
                                "locations owned or controlled by Licensee. Licensee shall not reverse engineer, decompile, or "
                                +
                                "disassemble the Software except as expressly permitted by applicable law.")
                .linkedTo(licenseGrantClause.getId())
                .withRealEmbedding(embeddingModel)
                .metadata("source", "contract_document.pdf")
                .metadata("page", 4)
                .metadata("section", "2.1")
                .build();

        Chunk terminationDoc = TestDataBuilder.chunk()
                .content(
                        "Termination Rights: Either party may terminate this Agreement for convenience upon thirty (30) days "
                                +
                                "prior written notice to the other party. Additionally, either party may terminate immediately upon "
                                +
                                "written notice if the other party materially breaches this Agreement and fails to cure such breach "
                                +
                                "within fifteen (15) days after receiving written notice of the breach. Upon termination, Licensee "
                                +
                                "shall immediately cease all use of the Software and destroy all copies in its possession. "
                                +
                                "Licensor shall refund any prepaid fees on a pro-rata basis for the remaining license period. "
                                +
                                "Sections relating to confidentiality, intellectual property, and limitations of liability shall "
                                +
                                "survive termination.")
                .linkedTo(terminationClause.getId())
                .withRealEmbedding(embeddingModel)
                .metadata("source", "contract_document.pdf")
                .metadata("page", 14)
                .metadata("section", "8.1")
                .build();

        Chunk obligationsOverview = TestDataBuilder.chunk()
                .content("Key obligations under this Software License Agreement include: (1) Licensee must make timely "
                        +
                        "annual payments of $50,000 and maintain accurate usage records; (2) Licensor must provide " +
                        "software updates, security patches, and technical support during business hours; (3) Both parties "
                        +
                        "must maintain confidentiality of proprietary information; (4) Licensee must comply with all " +
                        "license restrictions and not exceed the 100 concurrent user limit; (5) Licensor must ensure " +
                        "the Software substantially conforms to documented specifications. Critical security patches " +
                        "must be delivered within 48 hours of discovery of vulnerabilities.")
                .linkedTo(contract.getId())
                .withRealEmbedding(embeddingModel)
                .metadata("source", "contract_summary.docx")
                .metadata("category", "Obligations")
                .build();

        return new LegalScenario(
                List.of(contract, acmeCorp, techCo, paymentClause, licenseGrantClause,
                        terminationClause, paymentObligation, updateObligation),
                List.of(acmePartyTo, techCoPartyTo, hasPaymentClause, hasLicenseClause,
                        hasTerminationClause, techCoPaymentObligation, acmeUpdateObligation),
                List.of(paymentTermsDoc, licenseGrantDoc, terminationDoc, obligationsOverview),
                contract,
                acmeCorp,
                techCo);
    }

    /**
     * Container for a complete legal scenario.
     */
    public record LegalScenario(
            List<Node> nodes,
            List<Edge> edges,
            List<Chunk> chunks,
            Node primaryContract,
            Node licensor,
            Node licensee) {
    }
}
