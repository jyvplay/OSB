#!/usr/bin/env node
// SPDX-License-Identifier: Apache-2.0
// R87 independent renderer lane: open every .docx in a directory with docx-preview (a third-party
// OOXML-to-HTML renderer) inside jsdom, and dump the paragraphs it produced as JSON.
//
// This is a renderer, not Word: it has no pagination, no font metrics and no tab-stop arithmetic of its
// own. What it does prove is that an implementation that never saw our writer turns our bytes into the
// paragraph structure, colours and margins we declared.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { JSDOM } from 'jsdom';

const require = createRequire(import.meta.url);
const { renderAsync } = require('docx-preview');
// The tool's own pins are authoritative: they are what `npm ci` installs here.
const pins = JSON.parse(fs.readFileSync(new URL('./package.json', import.meta.url), 'utf8')).dependencies;

const [directory, output] = process.argv.slice(2);
if (!directory || !output) {
  console.error('usage: render_docx.mjs <artifact-dir> <output.json>');
  process.exit(2);
}

const dom = new JSDOM('<!doctype html><html><head></head><body><div id="host"></div></body></html>', {
  pretendToBeVisual: true,
});
for (const key of ['window', 'document', 'DOMParser', 'XMLSerializer', 'Blob', 'Node', 'HTMLElement',
  'Element', 'getComputedStyle', 'navigator']) {
  if (dom.window[key] === undefined) continue;
  try {
    global[key] = dom.window[key];
  } catch {
    // Node 22 exposes `navigator` as a getter-only global; docx-preview does not need it here.
  }
}
global.self = dom.window;

const host = dom.window.document.getElementById('host');

function normalise(value) {
  return value.replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
}

function styleMap(element) {
  const raw = element.getAttribute('style') || '';
  const map = {};
  for (const part of raw.split(';')) {
    const index = part.indexOf(':');
    if (index <= 0) continue;
    map[part.slice(0, index).trim()] = part.slice(index + 1).trim();
  }
  return map;
}

// docx-preview renders `w:tab` as a span holding one space with no children of its own.
function tabSpans(element) {
  return [...element.querySelectorAll('span')]
    .filter((span) => span.children.length === 0 && span.textContent.length === 1 && span.textContent.trim() === '')
    .length;
}

const files = [];
const errors = [];
for (const name of fs.readdirSync(directory).filter((entry) => entry.endsWith('.docx')).sort()) {
  const bytes = fs.readFileSync(path.join(directory, name));
  host.innerHTML = '';
  try {
    await renderAsync(bytes, host, undefined, {
      inWrapper: true, breakPages: false, ignoreWidth: false, ignoreHeight: false, useBase64URL: true,
    });
  } catch (error) {
    errors.push({ name, error: String((error && error.message) || error) });
    continue;
  }
  const articles = [...host.querySelectorAll('section.docx article')];
  const paragraphs = articles.flatMap((article) => [...article.children])
    .filter((element) => element.tagName === 'P')
    .map((element) => ({
      classes: (element.getAttribute('class') || '').split(/\s+/).filter(Boolean),
      text: normalise(element.textContent),
      style: styleMap(element),
      tabSpans: tabSpans(element),
      // R89: per-run children in document order, so a run colour or size can be read back from the CSS
      // docx-preview actually applied. Unstyled runs may surface as bare text nodes (tag `#text`).
      runs: [...element.childNodes]
        .filter((node) => (node.nodeType === 1 && normalise(node.textContent) !== '')
          || (node.nodeType === 3 && normalise(node.textContent) !== ''))
        .map((node) => ({
          tag: node.nodeType === 1 ? node.tagName : '#text',
          text: normalise(node.textContent),
          style: node.nodeType === 1 ? styleMap(node) : {},
        })),
    }));
  files.push({
    name,
    sections: host.querySelectorAll('section.docx').length,
    tables: host.querySelectorAll('article > table').length,
    paragraphs,
  });
}

const payload = {
  renderer: `docx-preview@${pins['docx-preview']}`,
  jsdom: pins.jsdom,
  directory,
  files,
  errors,
};
fs.writeFileSync(output, `${JSON.stringify(payload, null, 1)}\n`);
for (const error of errors) console.error(`render failed: ${error.name}: ${error.error}`);
console.log(`R87 render: ${payload.renderer} rendered ${files.length} package(s), ${errors.length} failure(s)`);
process.exit(errors.length === 0 ? 0 : 4);
