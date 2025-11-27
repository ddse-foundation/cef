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
    title: 'Knowledge Model ORM',
    icon: '🗂️',
    description: (
      <>
        Define entities (nodes) and relationships (edges) like JPA @Entity. 
        CEF handles dual persistence to graph and vector stores automatically.
        Just like Hibernate for transactional data, but for knowledge models.
      </>
    ),
  },
  {
    title: 'Proven Superior Performance',
    icon: '📊',
    description: (
      <>
        Comprehensive benchmarks prove <strong>60-220% more relevant content</strong> retrieved 
        for complex queries requiring relationship reasoning. Medical and financial domain tests 
        validate production readiness.
      </>
    ),
  },
  {
    title: 'Production Ready',
    icon: '🚀',
    description: (
      <>
        Built with Spring Boot 3.3.5 and Java 17. Pluggable storage backends 
        (DuckDB, PostgreSQL, Neo4j). Tested with vLLM and Ollama. 
        Ready for enterprise deployment with <code>beta-0.5</code>.
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
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
