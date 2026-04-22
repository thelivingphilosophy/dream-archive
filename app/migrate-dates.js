#!/usr/bin/env node
// Retroactively fix dream dates using day+month from the source audio filename.
'use strict';

const fs   = require('fs');
const path = require('path');

const VAULT    = path.join(__dirname, '..', 'Dreams', 'Dream Vault');
const LOG_PATH = path.join(VAULT, 'processed_log.json');

const MONTHS = ['January','February','March','April','May','June',
                'July','August','September','October','November','December'];

const MONTH_IDX = {
  jan:0, feb:1, mar:2, apr:3, may:4, jun:5, jul:6, aug:7, sep:8, oct:9, nov:10, dec:11,
  gen:0, mag:4, giu:5, lug:6, ago:7, set:8, ott:9, dic:11,
};
const monthPat = Object.keys(MONTH_IDX).join('|');

function inferDate(filename) {
  // Try day + month (no year)
  const m = filename.match(new RegExp(`(\\d{1,2})[\\s_\\-]+(${monthPat})[a-z]*`, 'i'));
  if (!m) return null;
  const day = parseInt(m[1]);
  const mon = MONTH_IDX[m[2].toLowerCase().slice(0, 3)];
  if (mon === undefined) return null;
  const now = new Date();
  let year = now.getFullYear();
  if (new Date(year, mon, day) > now) year -= 1;
  return new Date(year, mon, day);
}

function formatDate(d) {
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

function findMdFiles(dir) {
  const results = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) results.push(...findMdFiles(full));
    else if (entry.name.endsWith('.md')) results.push(full);
  }
  return results;
}

function parseFrontmatter(content) {
  const match = content.match(/^---\n([\s\S]*?)\n---/);
  if (!match) return null;
  const fm = {};
  for (const line of match[1].split('\n')) {
    const i = line.indexOf(':');
    if (i === -1) continue;
    fm[line.slice(0, i).trim()] = line.slice(i + 1).trim().replace(/^"|"$/g, '');
  }
  return fm;
}

const log = JSON.parse(fs.readFileSync(LOG_PATH, 'utf8'));
const mdFiles = findMdFiles(VAULT).filter(f => !f.endsWith('processed_log.json'));

let changed = 0;

for (const mdPath of mdFiles) {
  const content = fs.readFileSync(mdPath, 'utf8');
  const fm = parseFrontmatter(content);
  if (!fm || !fm.source) continue;

  const newDate = inferDate(fm.source);
  if (!newDate) continue;

  const newDateStr = formatDate(newDate);
  if (fm.date === newDateStr) continue;

  const oldDateStr = fm.date;
  const title = path.basename(mdPath).replace(/^\d{4}-\d{2}-\d{2} - /, '').replace(/\.md$/, '');

  // Build new folder and path
  const year  = String(newDate.getFullYear());
  const month = String(newDate.getMonth() + 1).padStart(2, '0');
  const monthName = MONTHS[newDate.getMonth()];
  const newFolder = path.join(VAULT, year, `${year}_${month}_${monthName}`);
  fs.mkdirSync(newFolder, { recursive: true });
  const newFilename = `${newDateStr} - ${title}.md`;
  const newPath = path.join(newFolder, newFilename);

  // Update frontmatter in content
  const newContent = content.replace(`date: ${oldDateStr}`, `date: ${newDateStr}`);
  fs.writeFileSync(newPath, newContent, 'utf8');
  if (newPath !== mdPath) fs.unlinkSync(mdPath);

  // Update log entries
  for (const [key, entry] of Object.entries(log)) {
    if (entry.mdPath === mdPath) {
      log[key].date = newDateStr;
      log[key].mdPath = newPath;
    }
  }

  console.log(`${path.basename(mdPath)} → ${newFilename}`);
  changed++;
}

fs.writeFileSync(LOG_PATH, JSON.stringify(log, null, 2), 'utf8');
console.log(`\nDone. ${changed} file(s) updated.`);
