import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  icon: string;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: 'ORM for Knowledge Models',
    icon: '🗄️',
    description: (
      <>
        Define entities (nodes) and relationships (edges) like JPA <code>@Entity</code>. 
        Framework manages dual persistence: graph store for relationships, vector store for semantics.
        Think Hibernate for LLM context engineering.
      </>
    ),
  },
  {
    title: 'Intelligent Context Assembly',
    icon: '🔍',
    description: (
      <>
        Multi-strategy retrieval: relationship navigation + semantic search + keyword fallback.
        Benchmarks prove <strong>60-220% more relevant content</strong> vs vector-only approaches
        for queries requiring graph reasoning.
      </>
    ),
  },
  {
    title: 'Production-Ready Patterns',
    icon: '⚡',
    description: (
      <>
        Repository layer, service patterns, reactive API with Spring WebFlux + R2DBC.
        Pluggable backends: DuckDB, PostgreSQL, Neo4j, Qdrant. 
        Comprehensive test suite with real medical and financial domain examples.
      </>
    ),
  },
  {
    title: 'Storage Agnostic',
    icon: '📦',
    description: (
      <>
        Pluggable graph stores (JGraphT, Neo4j) and vector stores (DuckDB, PostgreSQL, Qdrant).
        Start with embedded DuckDB, scale to PostgreSQL + pgvector, or use specialized databases.
        Configuration-driven, no code changes required.
      </>
    ),
  },
  {
    title: 'LLM Integration',
    icon: '🤖',
    description: (
      <>
        Tested with vLLM (Qwen3-Coder-30B), Ollama, OpenAI. MCP tool support for agentic workflows.
        Automatic embedding generation, chunking strategies, and context assembly for optimal LLM input.
      </>
    ),
  },
  {
    title: 'Developer Experience',
    icon: '💻',
    description: (
      <>
        Familiar JPA-style API: <code>KnowledgeIndexer</code> (like EntityManager),
        <code>KnowledgeRetriever</code> (like Repository). YAML configuration,
        comprehensive documentation, and 30-minute quickstart tutorial.
      </>
    ),
  },
];

function Feature({title, icon, description}: FeatureItem) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <div className={styles.featureIcon}>{icon}</div>
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="text--center margin-bottom--xl">
          <Heading as="h2">Why CEF Framework?</Heading>
          <p style={{fontSize: '1.1rem', maxWidth: '800px', margin: '1rem auto 0'}}>
            The only Java framework that treats LLM context as first-class domain entities,
            with proven benchmarks showing superior performance over naive vector search.
          </p>
        </div>
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
        
        <div className="margin-top--xl" style={{background: 'var(--ifm-background-surface-color)', padding: '3rem 0', borderRadius: '8px'}}>
          <div className="text--center margin-bottom--lg">
            <Heading as="h2">Proven Performance</Heading>
            <p style={{fontSize: '1.1rem', maxWidth: '800px', margin: '1rem auto 0'}}>
              Comprehensive benchmarks with real medical and financial data demonstrate
              Knowledge Model superiority over vector-only approaches.
            </p>
          </div>
          <div className="row" style={{maxWidth: '900px', margin: '0 auto'}}>
            <div className="col col--6">
              <div style={{padding: '2rem', textAlign: 'center'}}>
                <div style={{fontSize: '3rem', fontWeight: 'bold', color: 'var(--ifm-color-primary)'}}>60-220%</div>
                <div style={{fontSize: '1.2rem', fontWeight: 'bold', marginBottom: '0.5rem'}}>More Relevant Content</div>
                <p>
                  Knowledge Model retrieves significantly more relevant content
                  for complex queries requiring relationship reasoning.
                </p>
              </div>
            </div>
            <div className="col col--6">
              <div style={{padding: '2rem', textAlign: 'center'}}>
                <div style={{fontSize: '3rem', fontWeight: 'bold', color: 'var(--ifm-color-success)'}}>+19.5%</div>
                <div style={{fontSize: '1.2rem', fontWeight: 'bold', marginBottom: '0.5rem'}}>Latency Overhead</div>
                <p>
                  Minimal latency increase (26ms vs 21.8ms average) for
                  dramatic improvement in context quality and relevance.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
