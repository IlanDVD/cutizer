const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function waitFor(predicate, message, timeout = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeout) { if (await predicate()) return; await sleep(100); }
  throw new Error(message);
}
exports.run = async ({ app, main, getOverlay, getDismiss, snapshot, stopOverlay, persist }) => {
  const output = path.join(__dirname, '..', 'test-output'); fs.mkdirSync(output, { recursive: true });
  async function capture(win, name) {
    if (process.argv.includes('--no-captures')) return;
    fs.writeFileSync(path.join(output, name), (await win.webContents.capturePage(undefined, { stayAwake: true })).toPNG());
  }
  const errors = [];
  main.webContents.on('console-message', (_event, ...args) => { const message = args.find(a => a && typeof a === 'object' && 'message' in a); if (message?.level === 'error') errors.push(message.message); });
  const js = script => main.webContents.executeJavaScript(script, true);
  await waitFor(() => js('!!currentState'), 'Renderer state was not initialized');
  assert.equal(await js("document.getElementById('page-home').hidden"), false);
  assert.equal(await js("[...document.images].every(i => i.complete && i.naturalWidth > 0)"), true);
  await js("document.getElementById('pet-button').click()");
  assert.match(await js("document.getElementById('miso-speech').textContent"), /câlin/);
  await sleep(800);
  await capture(main, 'home.png');
  await js("document.querySelector('[data-page=stats]').click()");
  assert.equal(await js("document.querySelectorAll('.day-column').length"), 7);
  await sleep(150);
  await capture(main, 'stats.png');
  await js("document.querySelector('[data-page=apps]').click()");
  const beforeToggle = snapshot().rules[0].enabled;
  await js("document.querySelector('[data-rule=instagram]').click()");
  await waitFor(() => snapshot().rules[0].enabled !== beforeToggle, 'App toggle failed');
  await js("document.querySelector('[data-rule=instagram]').click()");
  await waitFor(() => snapshot().rules[0].enabled === beforeToggle, 'App toggle restore failed');
  await sleep(150);
  await capture(main, 'apps.png');
  await js("document.querySelector('[data-page=lab]').click()");
  await sleep(150);
  await capture(main, 'interventions.png');
  const realBefore = snapshot().today.total;
  for (const id of ['walk', 'rappel', 'judge', 'magic']) {
    await js(`document.querySelector('[data-preview=${id}]').click()`);
    await waitFor(() => getOverlay()?.isVisible(), `Overlay ${id} did not appear`);
    const win = getOverlay();
    assert.equal(win.isAlwaysOnTop(), true);
    assert.equal(win.isFocusable(), false);
    await sleep(id === 'walk' ? 3500 : 2200);
    assert.equal(await win.webContents.executeJavaScript("document.getElementById('sprite').naturalWidth > 0"), true);
    assert.match(await win.webContents.executeJavaScript("document.getElementById('effect').className"), new RegExp(id));
    if (id === 'magic') assert.equal(await win.webContents.executeJavaScript("document.querySelectorAll('.spell').length"), 72);
    await capture(win, `overlay-${id}.png`);
    await waitFor(() => getDismiss()?.isVisible(), `Dismiss button missing for ${id}`);
    assert.equal(getDismiss().isAlwaysOnTop(), true);
    await getDismiss().webContents.executeJavaScript("document.getElementById('hide').click()", true);
    await waitFor(() => !getOverlay(), `Hide did not dismiss ${id}`);
    assert.equal(getDismiss(), null);
    console.log(`PASS: ${id} transparent, above windows, dismissible`);
  }
  assert.equal(snapshot().today.total, realBefore, 'Previews must not affect real time');
  await js("window.cutizer.preview('walk', true)");
  await waitFor(() => getOverlay()?.isVisible(), 'Auto-dismiss overlay did not start');
  await waitFor(() => !getOverlay(), 'Short preview did not automatically end', 15000);
  const paused = snapshot().paused;
  await js("document.getElementById('pause-button').click()");
  await waitFor(() => snapshot().paused !== paused, 'Pause failed');
  await js("document.getElementById('pause-button').click()");
  await waitFor(() => snapshot().paused === paused, 'Resume failed');
  await waitFor(() => snapshot().tracking === 'ready', 'Windows activity monitor failed', 15000);
  // Prove that the real OS probe actually increments time, not only that it starts.
  if (!process.argv.includes('--smoke-ui-only')) {
  const rules = snapshot().rules;
  await js(`window.cutizer.saveRules([...currentState.rules, { id: 'test-electron', name: 'Test local', enabled: true, processes: ['electron'], keywords: [], color: '#7e9676', symbol: '◌' }])`);
  main.show(); main.focus();
  await waitFor(() => (snapshot().today.byApp['test-electron'] || 0) >= 2000, 'Foreground activity did not increment time');
  await js('window.cutizer.pause()');
  const pausedTotal = snapshot().today.total;
  await sleep(2200);
  assert.equal(snapshot().today.total, pausedTotal, 'Paused tracker incremented time');
  await js('window.cutizer.pause()');
  await js(`window.cutizer.saveRules(${JSON.stringify(rules)})`);
  }
  await js("document.querySelector('[data-page=home]').click()");
  await sleep(200);
  await capture(main, 'home.png');
  assert.deepEqual(errors, []);
  persist(); stopOverlay();
  console.log('PASS: dashboard, navigation, images, interactions, rules, pause, real Windows monitor, automatic dismissal, preview/stat isolation.');
  app.quit();
};
