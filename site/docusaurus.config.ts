import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'CEF Framework',
  tagline: 'ORM for LLM Context Engineering - Build intelligent applications with graph-powered retrieval',
  favicon: 'img/favicon.svg',

  // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
  future: {
    v4: true, // Improve compatibility with the upcoming Docusaurus v4
  },

  // Set the production url of your site here
  url: 'https://ddse-foundation.github.io',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/cef/',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'ddse-foundation', // Usually your GitHub org/user name.
  projectName: 'cef', // Usually your repo name.

  onBrokenLinks: 'throw',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/ddse-foundation/cef/tree/main/site/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/logo.svg',
    metadata: [
      {name: 'keywords', content: 'java, llm, rag, knowledge graph, vector database, context engineering, orm, spring boot, ai'},
      {name: 'description', content: 'CEF Framework - ORM for LLM Context Engineering. Build intelligent Java applications with graph-powered retrieval that outperforms vector-only approaches by 60-220%.'},
      {property: 'og:type', content: 'website'},
      {property: 'og:title', content: 'CEF Framework - ORM for LLM Context Engineering'},
      {property: 'og:description', content: 'Build intelligent Java applications with proven graph-powered retrieval. 60-220% more relevant content vs vector-only RAG.'},
    ],
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'CEF Framework',
      logo: {
        alt: 'CEF Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: 'Documentation',
        },
        {
          href: 'https://github.com/ddse-foundation/cef',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Documentation',
          items: [
            {
              label: 'Quick Start',
              to: '/docs/intro',
            },
            {
              label: 'Architecture',
              to: '/docs/architecture',
            },
            {
              label: 'User Guide',
              to: '/docs/user-guide',
            },
          ],
        },
        {
          title: 'Community',
          items: [
            {
              label: 'GitHub Discussions',
              href: 'https://github.com/ddse-foundation/cef/discussions',
            },
            {
              label: 'GitHub Issues',
              href: 'https://github.com/ddse-foundation/cef/issues',
            },
          ],
        },
        {
          title: 'More',
          items: [
            {
              label: 'DDSE Foundation',
              href: 'https://ddse-foundation.github.io/',
            },
            {
              label: 'GitHub',
              href: 'https://github.com/ddse-foundation/cef',
            },
            {
              label: 'Release Notes',
              to: '/docs/release-notes',
            },
          ],
        },
      ],
      copyright: `Copyright © 2024-${new Date().getFullYear()} DDSE Foundation. Licensed under MIT. Built by Mahmudur R Manna (mrmanna).`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
