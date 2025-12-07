import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import Heading from '@theme/Heading';

import styles from './index.module.css';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <Heading as="h1" className="hero__title">
          CEF Framework
        </Heading>
        <p className="hero__subtitle" style={{fontSize: '1.5rem', marginBottom: '2rem'}}>
          ORM for LLM Context Engineering
        </p>
        <p className={styles.heroDescription}>
          Just as Hibernate abstracts databases for transactions, 
          CEF abstracts knowledge stores for <strong>Context Engineering</strong>. Build, test, and benchmark 
          intelligent context models in minutes, without the complexity of enterprise graph infrastructure.
        </p>
        <div className={styles.badges}>
          <img src="https://img.shields.io/badge/version-v0.6-blue.svg" alt="Version" />
          <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License" />
          <img src="https://img.shields.io/badge/Java-17+-orange.svg" alt="Java" />
          <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg" alt="Spring Boot" />
        </div>
        <div className={styles.buttons}>
          <Link
            className="button button--secondary button--lg"
            to="/docs/quickstart">
            Get Started in 5 Minutes →
          </Link>
          <Link
            className="button button--outline button--secondary button--lg"
            to="/docs/intro"
            style={{marginLeft: '1rem'}}>
            Learn More
          </Link>
        </div>
        <div className={styles.codeExample}>
          <pre>
{`// Define knowledge model like JPA entities
Node patient = new Node(null, "Patient", 
    Map.of("name", "John", "age", 45), 
    "Patient with diabetes and hypertension");

// Persist with dual persistence (graph + vector)
indexer.indexNode(patient).block();

// Query with intelligent context assembly
SearchResult result = retriever.retrieve(
    RetrievalRequest.builder()
        .query("diabetes treatment plans")
        .depth(2)  // Multi-hop reasoning
        .topK(10)
        .build()
);`}
          </pre>
        </div>
      </div>
    </header>
  );
}

export default function Home(): JSX.Element {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title="ORM for LLM Context Engineering"
      description="CEF Framework - Hibernate for Knowledge Models. Build intelligent applications with graph-powered retrieval that outperforms vector-only approaches by 60-220%.">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
