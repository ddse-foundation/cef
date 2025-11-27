import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.
 */
const sidebars: SidebarsConfig = {
  tutorialSidebar: [
    {type: 'doc', id: 'intro', label: 'Introduction'},
    {
      type: 'category',
      label: 'Getting Started',
      collapsible: false,
      items: [
        'getting-started/installation',
        'getting-started/configuration',
        'quickstart',
      ],
    },
    {
      type: 'category',
      label: 'Core Concepts',
      collapsible: false,
      items: [
        'concepts/knowledge-model',
        'architecture',
        'user-guide',
      ],
    },
    {
      type: 'category',
      label: 'Tutorials',
      collapsible: false,
      items: [
        'tutorials/build-your-first-model',
      ],
    },
    {
      type: 'category',
      label: 'Benchmarks & Validation',
      collapsible: false,
      items: [
        'benchmarks',
        'validation',
      ],
    },
    {
      type: 'category',
      label: 'Reference',
      collapsible: false,
      items: [
        'release-notes',
        'known-issues',
      ],
    },
  ],
};

export default sidebars;
