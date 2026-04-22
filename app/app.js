'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const { exec, execFile } = require('child_process');

// ─── Config ───────────────────────────────────────────────────────────────────
function getEnvPath() {
  if (process.env.ELECTRON_PACKAGED) {
    const { app } = require('electron');
    return require('path').join(app.getPath('userData'), '.env');
  }
  return path.join(__dirname, '.env');
}

function loadEnv() {
  const envPath = getEnvPath();
  if (!fs.existsSync(envPath)) return {};
  const env = {};
  for (const line of fs.readFileSync(envPath, 'utf8').split('\n')) {
    const t = line.trim();
    if (!t || t.startsWith('#')) continue;
    const i = t.indexOf('=');
    if (i < 0) continue;
    env[t.slice(0, i).trim()] = t.slice(i + 1).trim().replace(/^["']|["']$/g, '');
  }
  return env;
}

const ENV = loadEnv();
const PORT = 3000;
const OPENAI_KEY = ENV.OPENAI_API_KEY || '';

if (!OPENAI_KEY) {
  console.error('ERROR: No OPENAI_API_KEY in .env file');
  process.exit(1);
}
const TRANSCRIPTION_MODEL = ENV.TRANSCRIPTION_MODEL || 'gpt-4o-transcribe';
const SUMMARY_MODEL = ENV.SUMMARY_MODEL || 'gpt-4o-mini';

const SUPPORTED_EXT = new Set(['.m4a', '.mp3', '.wav', '.webm', '.mp4', '.mpeg', '.mpga', '.aac']);
const UNSUPPORTED_EXT = new Set(['.ogg', '.flac', '.opus', '.wma']);
const MAX_SIZE = 24 * 1024 * 1024; // 24MB — OpenAI limit
const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];
const MIME_MAP = {
  '.m4a': 'audio/mp4', '.mp3': 'audio/mpeg', '.wav': 'audio/wav',
  '.webm': 'audio/webm', '.mp4': 'audio/mp4', '.mpeg': 'audio/mpeg', '.mpga': 'audio/mpeg',
};

let stopFlag = false;

// ─── Router ───────────────────────────────────────────────────────────────────
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  try {
    if (req.method === 'GET'  && url.pathname === '/')             return serveHtml(res);
    if (req.method === 'GET'  && url.pathname === '/api/defaults') return handleDefaults(res);
    if (req.method === 'GET'  && url.pathname === '/api/browse')   return handleBrowse(res);
    if (req.method === 'GET'  && url.pathname === '/api/scan')     return handleScan(res, url);
    if (req.method === 'GET'  && url.pathname === '/api/verify')   return handleVerify(res, url);
    if (req.method === 'POST' && url.pathname === '/api/process')  return handleProcess(req, res);
    if (req.method === 'POST' && url.pathname === '/api/stop')     return handleStop(res);
    res.writeHead(404); res.end();
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: err.message }));
  }
});

// ─── Static ───────────────────────────────────────────────────────────────────
function serveHtml(res) {
  const html = fs.readFileSync(path.join(__dirname, 'index.html'));
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(html);
}

// ─── Defaults ─────────────────────────────────────────────────────────────────
function handleDefaults(res) {
  let base;
  if (process.env.ELECTRON_PACKAGED) {
    const { app } = require('electron');
    base = path.join(app.getPath('documents'), 'Dreams');
  } else {
    base = path.join(__dirname, '..', 'Dreams');
  }
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({
    inputDir:  path.join(base, 'Audio Recordings'),
    outputDir: path.join(base, 'Dream Vault'),
  }));
}

// ─── Browse ───────────────────────────────────────────────────────────────────
async function handleBrowse(res) {
  if (process.env.ELECTRON_RUN) {
    const { dialog } = require('electron');
    const result = await dialog.showOpenDialog({ properties: ['openDirectory'] });
    const chosen = (!result.canceled && result.filePaths.length) ? result.filePaths[0] : null;
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ path: chosen }));
  }
  let cmd;
  if (process.platform === 'darwin') {
    cmd = `osascript -e 'POSIX path of (choose folder)'`;
  } else {
    const ps = `Add-Type -AssemblyName System.Windows.Forms; $d = New-Object System.Windows.Forms.FolderBrowserDialog; $d.ShowDialog() | Out-Null; $d.SelectedPath`;
    cmd = `powershell -NoProfile -NonInteractive -Command "${ps}"`;
  }
  exec(cmd, { timeout: 60000 }, (err, stdout) => {
    const p = (stdout || '').trim().replace(/\/$/, '');
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ path: p || null }));
  });
}

// ─── Scan ─────────────────────────────────────────────────────────────────────
function handleScan(res, url) {
  const inputDir  = url.searchParams.get('inputDir')  || '';
  const outputDir = url.searchParams.get('outputDir') || '';

  res.writeHead(200, { 'Content-Type': 'application/json' });

  const { valid: inputValid, absPath: absInput } = validateDir(inputDir);
  if (!inputValid) {
    return res.end(JSON.stringify({ pending: 0, warnings: [], totalBytes: 0 }));
  }

  const log = loadLog(outputDir);
  const entries = fs.readdirSync(absInput);
  let pending = 0, totalBytes = 0;
  const warnings = [];

  for (const name of entries) {
    if (name === 'processed') continue;
    const ext = path.extname(name).toLowerCase();
    if (UNSUPPORTED_EXT.has(ext)) {
      warnings.push(`"${name}" — format not supported by OpenAI (${ext}); convert to mp3 first`);
      continue;
    }
    if (!SUPPORTED_EXT.has(ext)) continue;
    const stat = fs.statSync(path.join(absInput, name));
    if (stat.isDirectory()) continue;
    if (isAlreadyProcessed(log, name, stat.size, absInput)) continue;
    pending++;
    totalBytes += stat.size;
  }

  res.end(JSON.stringify({ pending, warnings, totalBytes }));
}

// ─── Path validation ──────────────────────────────────────────────────────────
function validateDir(p) {
  if (!p || !p.trim()) return { valid: false, error: 'No path provided' };
  try {
    const absPath = path.resolve(p.trim());
    if (!fs.existsSync(absPath))             return { valid: false, absPath, error: 'Path does not exist' };
    if (!fs.statSync(absPath).isDirectory()) return { valid: false, absPath, error: 'Path is not a directory' };
    return { valid: true, absPath };
  } catch (err) {
    return { valid: false, error: err.message };
  }
}

// ─── Verify ───────────────────────────────────────────────────────────────────
function handleVerify(res, url) {
  const p = url.searchParams.get('path') || '';
  const { valid, absPath, error } = validateDir(p);
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ valid, path: absPath || null, error: error || null }));
}

// ─── Stop ─────────────────────────────────────────────────────────────────────
function handleStop(res) {
  stopFlag = true;
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ ok: true }));
}

// ─── Process (SSE stream) ─────────────────────────────────────────────────────
async function handleProcess(req, res) {
  req.on('close', () => { stopFlag = true; });

  const body = JSON.parse(await readBody(req));
  const { inputDir, outputDir, hints = '', summary = true } = body;

  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
  });

  const send = (data) => { try { res.write(`data: ${JSON.stringify(data)}\n\n`); } catch {} };

  stopFlag = false;
  let processed = 0, skipped = 0, failed = 0;

  if (!OPENAI_KEY) {
    send({ type: 'error', file: '', msg: 'No OpenAI API key found in .env file' });
    send({ type: 'complete', processed: 0, skipped: 0, failed: 1 });
    return res.end();
  }

  const { valid: inputValid, absPath: absInput, error: inputErr } = validateDir(inputDir);
  if (!inputValid) {
    send({ type: 'error', file: '', msg: `Input folder invalid: ${inputErr}` });
    send({ type: 'complete', processed: 0, skipped: 0, failed: 1 });
    return res.end();
  }

  const { valid: outputValid, absPath: absOutput, error: outputErr } = validateDir(outputDir);
  if (!outputValid) {
    send({ type: 'error', file: '', msg: `Output folder invalid: ${outputErr}` });
    send({ type: 'complete', processed: 0, skipped: 0, failed: 1 });
    return res.end();
  }

  const log = loadLog(absOutput);
  const entries = fs.readdirSync(absInput);

  for (const name of entries) {
    if (stopFlag) { send({ type: 'stopped' }); break; }
    if (name === 'processed') continue;

    const ext = path.extname(name).toLowerCase();

    if (UNSUPPORTED_EXT.has(ext)) {
      failed++;
      send({ type: 'error', file: name, msg: `Unsupported format (${ext}) — convert to mp3 first` });
      continue;
    }

    if (!SUPPORTED_EXT.has(ext)) continue;

    const fullPath = path.join(absInput, name);
    const stat = fs.statSync(fullPath);
    if (stat.isDirectory()) continue;

    if (isAlreadyProcessed(log, name, stat.size, absInput)) {
      skipped++;
      send({ type: 'skip', file: name });
      continue;
    }

    if (stat.size > MAX_SIZE) {
      failed++;
      send({ type: 'error', file: name, msg: `Too large (${(stat.size/1024/1024).toFixed(1)} MB > 24 MB limit)` });
      continue;
    }

    send({ type: 'start', file: name });

    try {
      const date = extractDate(name, stat);

      send({ type: 'progress', file: name, step: 'transcribing...' });
      const transcript = await transcribe(fullPath, ext, hints);

      if (!transcript) throw new Error('Transcription returned empty — recording may be silent or too short');

      let title, summaryText = '';
      if (summary) {
        send({ type: 'progress', file: name, step: 'summarising...' });
        const result = await summarise(transcript, hints);
        title = result.title;
        summaryText = result.summary || '';
      } else {
        title = transcript.split(/\s+/).slice(0, 8).join(' ').replace(/[^\w\s''\-]/g, '').trim();
      }

      const mdPath = writeMd(absOutput, date, title, summaryText, transcript, name);

      const processedDir = path.join(absInput, 'processed');
      fs.mkdirSync(processedDir, { recursive: true });
      fs.copyFileSync(fullPath, path.join(processedDir, name));
      fs.unlinkSync(fullPath);

      log[logKey(name, stat.size)] = { date: formatDate(date), title, mdPath };
      saveLog(absOutput, log);

      processed++;
      send({ type: 'done', file: name, title, date: formatDate(date) });

    } catch (err) {
      failed++;
      send({ type: 'error', file: name, msg: err.message });
    }
  }

  send({ type: 'complete', processed, skipped, failed });
  res.end();
}

// ─── Skip logic ───────────────────────────────────────────────────────────────
function isAlreadyProcessed(log, name, size, inputDir) {
  if (!log[logKey(name, size)]) return false;
  const processedCopy = path.join(inputDir, 'processed', name);
  return fs.existsSync(processedCopy);
}

// ─── Date extraction ──────────────────────────────────────────────────────────
function extractDate(filename, stat) {
  const iso = filename.match(/(\d{4})[_\-]?(\d{2})[_\-]?(\d{2})/);
  if (iso) {
    const d = new Date(parseInt(iso[1]), parseInt(iso[2]) - 1, parseInt(iso[3]));
    if (!isNaN(d) && d.getFullYear() > 1990 && d.getFullYear() < 2100) return d;
  }
  const MONTH_IDX = {
    jan:0, feb:1, mar:2, apr:3, may:4, jun:5, jul:6, aug:7, sep:8, oct:9, nov:10, dec:11,
    // Italian abbreviations
    gen:0, mag:4, giu:5, lug:6, ago:7, set:8, ott:9, dic:11,
  };
  const monthPat = Object.keys(MONTH_IDX).join('|');
  let m = filename.match(new RegExp(`(${monthPat})[a-z]*[\\s_\\-]+(\\d{1,2})[\\s_\\-,]+(\\d{4})`, 'i'));
  if (m) {
    const d = new Date(parseInt(m[3]), MONTH_IDX[m[1].toLowerCase().slice(0,3)], parseInt(m[2]));
    if (!isNaN(d)) return d;
  }
  m = filename.match(new RegExp(`(\\d{1,2})[\\s_\\-]+(${monthPat})[a-z]*[\\s_\\-]+(\\d{4})`, 'i'));
  if (m) {
    const d = new Date(parseInt(m[3]), MONTH_IDX[m[2].toLowerCase().slice(0,3)], parseInt(m[1]));
    if (!isNaN(d)) return d;
  }
  // Day + month only, no year (e.g. "1 Jan, 11.25_" or "18 Mar,")
  m = filename.match(new RegExp(`(\\d{1,2})[\\s_\\-]+(${monthPat})[a-z]*`, 'i'));
  if (m) {
    const day = parseInt(m[1]);
    const mon = MONTH_IDX[m[2].toLowerCase().slice(0, 3)];
    if (mon !== undefined) {
      const now = new Date();
      let year = now.getFullYear();
      if (new Date(year, mon, day) > now) year -= 1;
      const d = new Date(year, mon, day);
      if (!isNaN(d)) return d;
    }
  }
  return new Date(stat.birthtimeMs || stat.mtimeMs);
}

function formatDate(d) {
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

// ─── Fetch with timeout ───────────────────────────────────────────────────────
function fetchWithTimeout(url, opts, ms) {
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), ms);
  return fetch(url, { ...opts, signal: ac.signal }).finally(() => clearTimeout(timer));
}

// ─── AAC conversion ───────────────────────────────────────────────────────────
function ffmpegBin() {
  const local = path.join(__dirname, 'ffmpeg');
  return fs.existsSync(local) ? local : 'ffmpeg';
}

function convertAac(srcPath) {
  const tmp = srcPath.replace(/\.aac$/i, `_${Date.now()}.m4a`);
  return new Promise((resolve, reject) => {
    // FIX #11: execFile avoids shell — no injection risk from special chars in paths
    execFile(ffmpegBin(), ['-y', '-i', srcPath, '-c:a', 'copy', tmp], { timeout: 60_000 }, (err) => {
      if (err) return reject(new Error(`ffmpeg conversion failed — is ffmpeg installed? (brew install ffmpeg / winget install ffmpeg)`));
      resolve(tmp);
    });
  });
}

// ─── OpenAI: transcribe ───────────────────────────────────────────────────────
async function transcribe(filePath, ext, hints) {
  let uploadPath = filePath;
  let tmpFile = null;
  if (ext === '.aac') {
    tmpFile = await convertAac(filePath);
    uploadPath = tmpFile;
  }
  try {
    const audio = fs.readFileSync(uploadPath);
    const form = new FormData();
    form.append('file', new Blob([audio], { type: 'audio/mp4' }), path.basename(uploadPath));
    form.append('model', TRANSCRIPTION_MODEL);
    if (hints.trim()) form.append('prompt', hints.trim());

    const res = await fetchWithTimeout('https://api.openai.com/v1/audio/transcriptions', {
      method: 'POST',
      headers: { Authorization: `Bearer ${OPENAI_KEY}` },
      body: form,
    }, 120_000);
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Transcription API error ${res.status}: ${text}`);
    }
    const json = await res.json();
    return (json.text || '').trim();
  } finally {
    if (tmpFile) try { fs.unlinkSync(tmpFile); } catch {}
  }
}

// ─── OpenAI: summarise ────────────────────────────────────────────────────────
async function summarise(transcript, hints) {
  const system = [
    'You analyse dream journal entries.',
    hints.trim() ? `Context about the dreamer: ${hints.trim()}` : '',
    'Given a dream transcript, return a JSON object with exactly two fields:',
    '  "title": an evocative 3–6 word title capturing the main image or emotion',
    '  "summary": 1–2 sentences on key events, symbols, and emotional tone',
    'Note Jungian themes (shadow, anima/animus, archetypes) and recurring motifs when present.',
    'Return ONLY valid JSON, nothing else.',
  ].filter(Boolean).join('\n');

  const res = await fetchWithTimeout('https://api.openai.com/v1/chat/completions', {
    method: 'POST',
    headers: { Authorization: `Bearer ${OPENAI_KEY}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: SUMMARY_MODEL,
      response_format: { type: 'json_object' },
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: transcript },
      ],
    }),
  }, 60_000);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Summary API error ${res.status}: ${text}`);
  }
  const json = await res.json();
  try {
    return JSON.parse(json.choices[0].message.content);
  } catch {
    return { title: transcript.split(/\s+/).slice(0, 8).join(' '), summary: '' };
  }
}

// ─── Write markdown ───────────────────────────────────────────────────────────
function writeMd(outputDir, date, title, summary, transcript, sourceFile) {
  const safeTitle = title.replace(/[/\\:*?"<>|]/g, '-').replace(/\s+/g, ' ').trim() || 'Untitled Dream';
  const dateStr   = formatDate(date);
  const year      = String(date.getFullYear());
  const month     = String(date.getMonth() + 1).padStart(2, '0');
  const monthName = MONTHS[date.getMonth()];

  const folder = path.join(outputDir, year, `${year}_${month}_${monthName}`);
  fs.mkdirSync(folder, { recursive: true });

  let filename = `${dateStr} - ${safeTitle}.md`;
  let outPath  = path.join(folder, filename);
  let n = 2;
  while (fs.existsSync(outPath)) {
    filename = `${dateStr} - ${safeTitle} (${n++}).md`;
    outPath  = path.join(folder, filename);
  }

  const frontmatter = [
    '---',
    `date: ${dateStr}`,
    `title: "${safeTitle.replace(/"/g, '\\"')}"`,
    summary ? `summary: "${summary.replace(/"/g, '\\"')}"` : null,
    `source: "[[${sourceFile}]]"`,
    '---',
  ].filter(v => v !== null).join('\n');

  fs.writeFileSync(outPath, `${frontmatter}\n\n# ${safeTitle}\n\n${transcript}\n`, 'utf8');
  return outPath;
}

// ─── processed_log.json ───────────────────────────────────────────────────────
function logKey(name, size) { return `${name}::${size}`; }

function loadLog(outputDir) {
  if (!outputDir || !fs.existsSync(outputDir)) return {};
  const p = path.join(outputDir, 'processed_log.json');
  if (!fs.existsSync(p)) return {};
  try { return JSON.parse(fs.readFileSync(p, 'utf8')); } catch { return {}; }
}

function saveLog(outputDir, log) {
  fs.mkdirSync(outputDir, { recursive: true });
  const p   = path.join(outputDir, 'processed_log.json');
  const tmp = p + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(log, null, 2), 'utf8');
  fs.renameSync(tmp, p);
}

// ─── Utility ──────────────────────────────────────────────────────────────────
function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', c => (data += c));
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

// ─── Start ────────────────────────────────────────────────────────────────────
server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    if (process.env.ELECTRON_RUN) {
      process.emit('server-error', err);
      return;
    }
    console.error('ERROR: Port 3000 already in use — is Dream Archive already running?');
    process.exit(1);
  }
  throw err;
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`\n  🌙  Conn's Dream Archive\n  http://localhost:${PORT}\n`);
  if (!process.env.ELECTRON_RUN) console.log('  Close this window to stop the server.\n');
  if (process.env.ELECTRON_RUN) process.emit('server-ready');
});

module.exports = server;

process.on('SIGTERM', () => server.close(() => process.exit(0)));
process.on('SIGINT',  () => server.close(() => process.exit(0)));
