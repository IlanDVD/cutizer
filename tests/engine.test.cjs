const { test } = require('node:test');
const assert = require('node:assert/strict');
const { STAGES, createState, restoreState, dayKey, today, record, matchRule, validRule } = require('../src/engine.cjs');
test('recognizes browser social tabs and native processes, not unrelated app titles', () => {
  const rules = createState().rules;
  assert.equal(matchRule(rules, { process: 'msedge', title: 'Instagram • Photos', idleSeconds: 0 }).id, 'instagram');
  assert.equal(matchRule(rules, { process: 'Instagram.exe', title: '', idleSeconds: 0 }).id, 'instagram');
  assert.equal(matchRule(rules, { process: 'notepad', title: 'YouTube notes', idleSeconds: 0 }), null);
  assert.equal(matchRule(rules, { process: 'chrome', title: 'Mailbox', idleSeconds: 0 }), null);
});
test('disabled rules and idle users are not tracked', () => {
  const rules = createState().rules;
  assert.equal(matchRule(rules, { process: 'discord', title: '', idleSeconds: 0 }), null);
  assert.equal(matchRule(rules, { process: 'chrome', title: 'YouTube', idleSeconds: 300 }), null);
});
test('every requested threshold triggers exactly once, across different apps', () => {
  const state = createState(), fired = [];
  for (let i = 0; i < 7201; i++) fired.push(...record(state, i % 2 ? 'instagram' : 'tiktok', 1000));
  assert.deepEqual(fired.map(s => s.minutes), [30, 60, 90, 120]);
  assert.deepEqual(today(state).fired, ['walk', 'rappel', 'judge', 'magic']);
  assert.equal(today(state).total, 7201000);
  assert.equal(today(state).byApp.instagram + today(state).byApp.tiktok, 7201000);
});
test('crossing a threshold does not fire one millisecond early', () => {
  const state = createState(); today(state).total = 1798999;
  assert.equal(record(state, 'youtube', 1000).length, 0);
  assert.equal(record(state, 'youtube', 1)[0].id, 'walk');
});
test('pause, invalid rules, long gaps and invalid durations never add time', () => {
  const state = createState(); state.paused = true; record(state, 'youtube', 1000); state.paused = false;
  for (const ms of [NaN, Infinity, -1, 0, 2501, 9000000]) record(state, 'youtube', ms);
  record(state, 'missing', 1000); record(state, 'discord', 1000);
  assert.equal(today(state).total, 0);
});
test('midnight starts a separate day and rearms thresholds', () => {
  const state = createState(), day1 = new Date(2026, 9, 13, 23, 59), day2 = new Date(2026, 9, 14, 0, 1);
  today(state, day1).total = 1799000;
  assert.equal(record(state, 'youtube', 1000, day1)[0].id, 'walk');
  assert.equal(today(state, day2).total, 0);
  assert.deepEqual(today(state, day2).fired, []);
  assert.equal(dayKey(day1), '2026-10-13');
});
test('valid save restores totals and thresholds without triggering them again', () => {
  const state = createState(); today(state).total = 1799000; today(state).byApp.youtube = 1799000; record(state, 'youtube', 1000);
  const loaded = restoreState(JSON.parse(JSON.stringify(state)));
  assert.equal(today(loaded).total, 1800000);
  assert.equal(record(loaded, 'youtube', 1000).length, 0);
});
test('damaged saves discard invalid values and preserve safe statistics', () => {
  const key = dayKey();
  const result = restoreState({ version: 1, days: { [key]: { byApp: { youtube: 2000, bad: -20, huge: 1e20 }, fired: ['walk', 'invalid', 'walk'], interventions: -1 } } });
  assert.equal(today(result).total, 2000); assert.deepEqual(today(result).fired, ['walk']); assert.equal(today(result).interventions, 0);
  assert.deepEqual(restoreState(null), createState());
});
test('history is bounded to 30 days', () => {
  const state = createState();
  for (let i = 1; i <= 40; i++) record(state, 'youtube', 1000, new Date(2026, 8, i));
  assert.equal(Object.keys(state.days).length, 30);
});
test('custom rules validate names and detection criteria', () => {
  assert.equal(validRule({ id: 'custom', name: 'Pinterest', enabled: true, processes: [], keywords: ['pinterest'] }), true);
  assert.equal(validRule({ id: 'custom', name: '', enabled: true, processes: [], keywords: [''] }), false);
});
test('waiting and spell durations preserve requested full lengths', () => {
  assert.equal(STAGES.find(s => s.id === 'judge').duration, 122000);
  assert.equal(STAGES.find(s => s.id === 'magic').duration, 602000);
});
