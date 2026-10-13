const STAGES = [
  { id: 'walk', minutes: 30, duration: 12000, name: 'La petite balade' },
  { id: 'rappel', minutes: 60, duration: 15000, name: 'Le regard qui en dit long' },
  { id: 'judge', minutes: 90, duration: 122000, name: 'On fait une pause ?' },
  { id: 'magic', minutes: 120, duration: 602000, name: 'Le grand sort de déconnexion' }
];
const DEFAULT_RULES = [
  { id: 'instagram', name: 'Instagram', symbol: '◎', color: '#b77794', enabled: true, processes: ['instagram'], keywords: ['instagram'] },
  { id: 'tiktok', name: 'TikTok', symbol: '♪', color: '#596766', enabled: true, processes: ['tiktok'], keywords: ['tiktok'] },
  { id: 'youtube', name: 'YouTube', symbol: '▷', color: '#cf7b67', enabled: true, processes: [], keywords: ['youtube'] },
  { id: 'facebook', name: 'Facebook', symbol: 'f', color: '#7797ba', enabled: false, processes: ['facebook'], keywords: ['facebook'] },
  { id: 'reddit', name: 'Reddit', symbol: '↗', color: '#c98a62', enabled: false, processes: [], keywords: ['reddit'] },
  { id: 'discord', name: 'Discord', symbol: '◌', color: '#9286b9', enabled: false, processes: ['discord'], keywords: ['discord'] },
  { id: 'twitter', name: 'X / Twitter', symbol: '𝕏', color: '#777e7a', enabled: false, processes: [], keywords: ['twitter', ' / x', ' on x', 'sur x', 'x.com'] }
];
const BROWSERS = new Set(['chrome', 'msedge', 'firefox', 'brave', 'opera', 'vivaldi', 'arc', 'zen']);
function dayKey(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
function createState() { return { version: 1, paused: false, rules: structuredClone(DEFAULT_RULES), days: {} }; }
function validRule(rule) {
  return rule && typeof rule.id === 'string' && rule.id.length <= 80 && typeof rule.name === 'string' && rule.name.trim().length > 0 && rule.name.length <= 60 && typeof rule.enabled === 'boolean' && ['processes', 'keywords'].every(key => Array.isArray(rule[key]) && rule[key].length <= 20 && rule[key].every(v => typeof v === 'string' && v.trim().length > 0 && v.length <= 100));
}
function restoreState(raw) {
  const result = createState();
  if (!raw || raw.version !== 1) return result;
  result.paused = raw.paused === true;
  if (Array.isArray(raw.rules) && raw.rules.length <= 40 && raw.rules.every(validRule) && new Set(raw.rules.map(r => r.id)).size === raw.rules.length) result.rules = raw.rules;
  for (const [date, day] of Object.entries(raw.days || {})) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !day || typeof day.byApp !== 'object' || !day.byApp) continue;
    const byApp = Object.fromEntries(Object.entries(day.byApp).filter(([id, time]) => id.length <= 80 && Number.isFinite(time) && time >= 0 && time <= 86400000));
    result.days[date] = { byApp, total: Object.values(byApp).reduce((a, b) => a + b, 0), fired: [...new Set((Array.isArray(day.fired) ? day.fired : []).filter(id => STAGES.some(s => s.id === id)))], interventions: Number.isSafeInteger(day.interventions) && day.interventions >= 0 ? day.interventions : 0 };
  }
  return result;
}
function today(state, date = new Date()) {
  const key = dayKey(date);
  state.days[key] ||= { total: 0, byApp: {}, fired: [], interventions: 0 };
  for (const old of Object.keys(state.days).sort().slice(0, -30)) delete state.days[old];
  return state.days[key];
}
function matchRule(rules, sample) {
  if (!sample || sample.idleSeconds >= 300) return null;
  const process = String(sample.process || '').toLowerCase().replace(/\.exe$/, '');
  const title = String(sample.title || '').toLowerCase();
  return rules.find(rule => rule.enabled && (rule.processes.some(p => p.toLowerCase().replace(/\.exe$/, '') === process) || (BROWSERS.has(process) && rule.keywords.some(word => title.includes(word.toLowerCase()))))) || null;
}
function record(state, ruleId, elapsed, date = new Date()) {
  const day = today(state, date);
  if (state.paused || !ruleId || !Number.isFinite(elapsed) || elapsed <= 0 || elapsed > 2500) return [];
  if (!state.rules.some(r => r.id === ruleId && r.enabled)) return [];
  day.total += elapsed;
  Object.defineProperty(day.byApp, ruleId, { value: (Object.hasOwn(day.byApp, ruleId) ? day.byApp[ruleId] : 0) + elapsed, enumerable: true, configurable: true, writable: true });
  const crossed = STAGES.filter(s => day.total >= s.minutes * 60000 && !day.fired.includes(s.id));
  for (const stage of crossed) day.fired.push(stage.id);
  return crossed;
}
module.exports = { STAGES, DEFAULT_RULES, dayKey, createState, restoreState, validRule, today, matchRule, record };
