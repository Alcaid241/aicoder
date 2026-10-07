<script setup lang="ts">
import { computed } from 'vue'
import { Marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark-dimmed.css'

const props = defineProps<{ content: string }>()

const markedInstance = new Marked({
  breaks: true,
  gfm: true
})

markedInstance.use({
  renderer: {
    code({ text, lang }: { text: string; lang?: string }) {
      const language = lang && hljs.getLanguage(lang) ? lang : ''
      const highlighted = language
        ? hljs.highlight(text, { language }).value
        : hljs.highlightAuto(text).value
      return `<pre><code class="hljs${language ? ` language-${language}` : ''}">${highlighted}</code></pre>`
    }
  }
})

const rendered = computed(() => {
  if (!props.content) return ''
  return markedInstance.parse(props.content) as string
})
</script>

<template>
  <div class="markdown-body" v-html="rendered" />
</template>

<style lang="scss">
.markdown-body {
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-primary);
  word-break: break-word;

  &:first-child { margin-top: 0; }
  &:last-child { margin-bottom: 0; }

  h1, h2, h3, h4, h5, h6 {
    margin-top: 20px;
    margin-bottom: 10px;
    font-weight: 600;
    color: var(--text-primary);
    line-height: 1.4;
  }
  h1 { font-size: 1.5em; border-bottom: 1px solid var(--border-default); padding-bottom: 8px; }
  h2 { font-size: 1.3em; border-bottom: 1px solid var(--border-default); padding-bottom: 6px; }
  h3 { font-size: 1.15em; }
  h4 { font-size: 1.05em; }

  p {
    margin-bottom: 12px;
    &:last-child { margin-bottom: 0; }
  }

  strong { font-weight: 600; color: var(--text-primary); }

  em { font-style: italic; }

  del { text-decoration: line-through; color: var(--text-muted); }

  ul, ol {
    padding-left: 24px;
    margin-bottom: 12px;
    li { margin-bottom: 4px; }
  }

  blockquote {
    border-left: 3px solid var(--accent);
    padding: 8px 16px;
    margin: 12px 0;
    color: var(--text-secondary);
    background: var(--bg-overlay);
    border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
    p { margin-bottom: 4px; }
  }

  code {
    background: var(--bg-overlay);
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 0.9em;
    font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
    color: var(--danger);
    word-break: break-all;
  }

  pre {
    background: #1e293b;
    border-radius: var(--radius-md);
    padding: 16px;
    overflow-x: auto;
    margin: 12px 0;

    code {
      background: none;
      padding: 0;
      color: #e2e8f0;
      font-size: 13px;
      word-break: normal;
    }
  }

  table {
    width: 100%;
    border-collapse: collapse;
    margin: 12px 0;
    display: block;
    overflow-x: auto;

    th, td {
      border: 1px solid var(--border-default);
      padding: 8px 14px;
      text-align: left;
      white-space: nowrap;
    }
    th {
      background: var(--bg-overlay);
      font-weight: 600;
      color: var(--text-primary);
    }
    td { color: var(--text-primary); white-space: normal; }
    tr:nth-child(even) td { background: var(--bg-root); }
  }

  a {
    color: var(--accent);
    text-decoration: underline;
    &:hover { opacity: 0.8; }
  }

  hr {
    border: none;
    border-top: 1px solid var(--border-default);
    margin: 20px 0;
  }

  img {
    max-width: 100%;
    border-radius: var(--radius-md);
    margin: 8px 0;
  }

  details {
    margin: 8px 0;
    summary {
      cursor: pointer;
      color: var(--accent);
      font-weight: 500;
    }
  }
}
</style>
