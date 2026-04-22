'use strict';
const { app, BrowserWindow, dialog, shell } = require('electron');
const fs   = require('fs');
const path = require('path');

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) { app.quit(); process.exit(0); }

let mainWindow = null;
app.on('second-instance', () => {
  if (mainWindow) { if (mainWindow.isMinimized()) mainWindow.restore(); mainWindow.focus(); }
});

process.env.ELECTRON_RUN = '1';
if (app.isPackaged) process.env.ELECTRON_PACKAGED = '1';

function getEnvPath() {
  return process.env.ELECTRON_PACKAGED
    ? path.join(app.getPath('userData'), '.env')
    : path.join(__dirname, 'app', '.env');
}

function hasApiKey() {
  const p = getEnvPath();
  if (!fs.existsSync(p)) return false;
  return fs.readFileSync(p, 'utf8').includes('OPENAI_API_KEY=');
}

app.whenReady().then(() => {
  if (process.env.ELECTRON_PACKAGED && !hasApiKey()) {
    const envPath = getEnvPath();
    const btn = dialog.showMessageBoxSync({
      type: 'info', title: 'Dream Archive — Setup Required',
      message: 'OpenAI API key not found',
      detail: `Create a file at:\n\n${envPath}\n\nContaining:\n\nOPENAI_API_KEY=sk-...\n\nThen relaunch the app.`,
      buttons: ['Open Folder', 'Quit'],
    });
    if (btn === 0) shell.showItemInFolder(app.getPath('userData'));
    app.quit();
    return;
  }
  startServer();
});

function startServer() {
  const server = require('./app/app.js');

  process.once('server-ready', createWindow);

  process.once('server-error', (err) => {
    dialog.showErrorBox('Dream Archive', `Could not start: ${err.message}\n\nIs another instance already running?`);
    app.quit();
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 780, height: 820, minWidth: 600, minHeight: 600,
    title: "Conn's Dream Archive",
    backgroundColor: '#09080d',
    webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true },
  });
  mainWindow.setMenuBarVisibility(false);
  if (process.platform !== 'darwin') mainWindow.setMenu(null);
  mainWindow.loadURL('http://127.0.0.1:3000');
  mainWindow.on('closed', () => { mainWindow = null; });
}

app.on('window-all-closed', () => app.quit());
app.on('activate', () => { if (!mainWindow) createWindow(); });

app.on('before-quit', () => {
  const mod = require.cache[require.resolve('./app/app.js')];
  if (mod && mod.exports && mod.exports.close) mod.exports.close();
});
