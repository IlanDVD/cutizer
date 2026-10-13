const { app, BrowserWindow, ipcMain, screen, globalShortcut, powerMonitor } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const { spawn } = require('node:child_process');
const readline = require('node:readline');
const { STAGES, createState, restoreState, validRule, today, dayKey, matchRule, record } = require('./engine.cjs');
const smoke = process.argv.includes('--smoke-test');
if (smoke) app.setPath('userData', path.join(__dirname, '..', 'test-output', 'profile'));
let state = createState(), main, overlay, dismissWindow, worker, watchdog, saveTimer, overlayTimer, quitting = false;
let tracking = 'starting', storageError = '', active = null, previousRule = null, previousAt = 0, lastSample = 0, suspended = false;
let overlayData = null, overlayBounds = null, lastProbe = null;
if (!smoke && !app.requestSingleInstanceLock()) { app.quit(); return; }
app.on('second-instance', () => { if (main && !main.isDestroyed()) { if (main.isMinimized()) main.restore(); main.show(); main.focus(); } });
const storePath = () => path.join(app.getPath('userData'), 'cutizer.json');
function persist() {
  try {
    fs.mkdirSync(app.getPath('userData'), { recursive: true });
    fs.writeFileSync(`${storePath()}.tmp`, JSON.stringify(state, null, 2));
    fs.renameSync(`${storePath()}.tmp`, storePath());
    storageError = '';
  } catch { storageError = 'La sauvegarde locale est indisponible. Les statistiques risquent de ne pas être conservées.'; }
}
function snapshot() {
  return { ...state, today: today(state), date: dayKey(), stages: STAGES, active: active ? { id: active.id, name: active.name } : null, tracking, storageError, overlay: overlayData && { id: overlayData.id, preview: overlayData.preview }, platform: process.platform };
}
function broadcast() { if (main && !main.isDestroyed()) main.webContents.send('state-update', snapshot()); }
function stopOverlay() {
  clearTimeout(overlayTimer);
  if (dismissWindow && !dismissWindow.isDestroyed()) dismissWindow.close();
  dismissWindow = null;
  if (overlay && !overlay.isDestroyed()) overlay.close();
  overlay = null; overlayData = null;
  broadcast();
}
function secureWindow(win) {
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', event => event.preventDefault());
}
function showOverlay(id, preview = false, short = false) {
  const stage = STAGES.find(s => s.id === id);
  if (!stage) throw new Error('Animation inconnue');
  stopOverlay();
  overlayData = { ...stage, preview, duration: short ? Math.min(stage.duration, 12000) : stage.duration };
  overlayBounds = screen.getDisplayNearestPoint(screen.getCursorScreenPoint()).bounds;
  overlay = new BrowserWindow({ ...overlayBounds, frame: false, transparent: true, resizable: false, movable: false, focusable: false, skipTaskbar: true, alwaysOnTop: true, hasShadow: false, show: false, backgroundColor: '#00000000', webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true, backgroundThrottling: false } });
  secureWindow(overlay);
  overlay.setAlwaysOnTop(true, 'screen-saver');
  overlay.setIgnoreMouseEvents(true, { forward: true });
  overlay.loadFile(path.join(__dirname, 'overlay.html'));
  // A separate small native window keeps dismissal clickable without ever
  // intercepting clicks in the rest of the full-screen effect.
  dismissWindow = new BrowserWindow({ x: overlayBounds.x + overlayBounds.width - 300, y: overlayBounds.y + 24, width: 276, height: 56, frame: false, transparent: true, resizable: false, movable: false, focusable: false, skipTaskbar: true, alwaysOnTop: true, hasShadow: false, show: false, backgroundColor: '#00000000', webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true, backgroundThrottling: false } });
  secureWindow(dismissWindow);
  dismissWindow.setAlwaysOnTop(true, 'screen-saver');
  dismissWindow.loadFile(path.join(__dirname, 'dismiss.html'));
  // Failsafe also applies if the renderer fails to load.
  overlayTimer = setTimeout(stopOverlay, overlayData.duration + 5000);
  broadcast();
  return true;
}
function startMonitor() {
  if (worker) worker.kill();
  clearInterval(watchdog);
  active = null; previousRule = null; previousAt = 0; lastSample = Date.now();
  if (process.platform !== 'win32') { tracking = 'unsupported'; broadcast(); return; }
  tracking = 'starting';
  const powershell = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
  const child = spawn(powershell, ['-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', path.join(__dirname, 'activity.ps1')], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  worker = child;
  child.stderr.on('data', () => {});
  const fail = () => { if (worker !== child || quitting) return; tracking = 'error'; active = null; previousRule = null; broadcast(); };
  child.on('error', fail); child.on('exit', fail);
  readline.createInterface({ input: child.stdout }).on('line', line => {
    if (worker !== child) return;
    try {
      const sample = JSON.parse(line);
      lastProbe = { process: sample.process, idleSeconds: sample.idleSeconds };
      const now = Date.now();
      tracking = 'ready'; lastSample = now;
      active = state.paused || suspended ? null : matchRule(state.rules, sample);
      // Only count intervals whose two endpoints belong to the same app.
      // Never count a stale interval after sleep, lock or a monitor restart.
      if (active && previousRule === active.id && previousAt && dayKey(new Date(previousAt)) === dayKey(new Date(now))) {
        const crossed = record(state, active.id, now - previousAt, new Date(now));
        if (crossed.length) {
          const next = crossed.at(-1);
          today(state).interventions++;
          persist(); showOverlay(next.id);
        }
      }
      previousRule = active?.id || null; previousAt = now;
      broadcast();
    } catch { fail(); }
  });
  watchdog = setInterval(() => { if (Date.now() - lastSample > 10000) fail(); }, 4000);
  broadcast();
}
function trusted(event, allowOverlay = false) {
  return event.sender === main?.webContents || (allowOverlay && (event.sender === overlay?.webContents || event.sender === dismissWindow?.webContents));
}
app.whenReady().then(async () => {
  try { if (!smoke) state = restoreState(JSON.parse(fs.readFileSync(storePath(), 'utf8'))); } catch (error) { if (error.code !== 'ENOENT') storageError = 'La précédente sauvegarde est illisible. Une nouvelle session a été ouverte.'; }
  main = new BrowserWindow({ width: 1160, height: 820, minWidth: 760, minHeight: 650, title: 'Cutizer · Miso', backgroundColor: '#f7f8f2', autoHideMenuBar: true, webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true } });
  main.setMenuBarVisibility(false); secureWindow(main);
  main.on('closed', () => { main = null; app.quit(); });
  ipcMain.handle('state', event => { if (trusted(event)) return snapshot(); });
  ipcMain.handle('pause', event => { if (!trusted(event)) return; state.paused = !state.paused; active = null; previousRule = null; if (state.paused) stopOverlay(); persist(); broadcast(); return snapshot(); });
  ipcMain.handle('rules', (event, rules) => {
    if (!trusted(event)) return;
    if (!Array.isArray(rules) || rules.length > 40 || !rules.every(validRule) || new Set(rules.map(r => r.id)).size !== rules.length) throw new Error('Règles invalides');
    state.rules = rules.map(r => ({ id: r.id, name: r.name.trim(), enabled: r.enabled, symbol: typeof r.symbol === 'string' ? r.symbol.slice(0, 4) : '◌', color: /^#[0-9a-f]{6}$/i.test(r.color) ? r.color : '#7e9676', processes: r.processes.map(v => v.trim()), keywords: r.keywords.map(v => v.trim()) }));
    previousRule = null; active = null; persist(); broadcast(); return snapshot();
  });
  ipcMain.handle('preview', (event, id, short) => { if (trusted(event)) return showOverlay(id, true, short === true); });
  ipcMain.handle('hide', event => { if (trusted(event, true)) stopOverlay(); });
  ipcMain.handle('retry', event => { if (trusted(event)) startMonitor(); });
  ipcMain.on('overlay-ready', event => {
    if (!overlayData) return;
    if (event.sender === dismissWindow?.webContents) { event.sender.send('overlay-start', overlayData); dismissWindow.showInactive(); return; }
    if (event.sender !== overlay?.webContents) return;
    event.sender.send('overlay-start', overlayData);
    overlay.showInactive();
    if (dismissWindow?.isVisible()) dismissWindow.moveTop();
    clearTimeout(overlayTimer); overlayTimer = setTimeout(stopOverlay, overlayData.duration + 150);
  });
  globalShortcut.register('CommandOrControl+Alt+M', stopOverlay);
  const suspend = () => { suspended = true; active = null; previousRule = null; stopOverlay(); broadcast(); };
  const resume = () => { suspended = false; previousRule = null; previousAt = 0; };
  powerMonitor.on('suspend', suspend); powerMonitor.on('lock-screen', suspend);
  powerMonitor.on('resume', resume); powerMonitor.on('unlock-screen', resume);
  screen.on('display-removed', stopOverlay);
  await main.loadFile(path.join(__dirname, 'index.html'));
  startMonitor(); saveTimer = setInterval(persist, 15000);
  if (smoke) require('../tests/desktop.cjs').run({ app, main, getOverlay: () => overlay, getDismiss: () => dismissWindow, snapshot, showOverlay, stopOverlay, persist }).catch(error => { console.error(error); console.error('Last activity probe:', lastProbe); app.exit(1); });
});
app.on('before-quit', () => { quitting = true; clearInterval(saveTimer); clearInterval(watchdog); clearTimeout(overlayTimer); worker?.kill(); persist(); globalShortcut.unregisterAll(); });
app.on('window-all-closed', () => app.quit());
