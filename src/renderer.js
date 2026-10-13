const api = window.cutizer;
let currentState, currentPage = 'home', toastTimer, speechTimer, renderedRules = '';
const $ = id => document.getElementById(id);
const esc = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
function time(ms) { const minutes = Math.floor(ms / 60000); return minutes >= 60 ? `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}` : minutes === 0 && ms > 0 ? '< 1 min' : `${minutes} min`; }
function toast(message) { clearTimeout(toastTimer); $('toast').textContent = message; $('toast').hidden = false; toastTimer = setTimeout(() => $('toast').hidden = true, 3500); }
function page(name) { currentPage = name; document.querySelectorAll('.page').forEach(p => p.hidden = p.id !== `page-${name}`); document.querySelectorAll('[data-page]').forEach(b => { b.classList.toggle('selected', b.dataset.page === name); b.setAttribute('aria-current', b.dataset.page === name ? 'page' : 'false'); }); if (currentState) render(currentState); window.scrollTo(0, 0); }
document.querySelectorAll('[data-page]').forEach(b => b.addEventListener('click', () => page(b.dataset.page)));
document.querySelectorAll('[data-go]').forEach(b => b.addEventListener('click', () => page(b.dataset.go)));
document.querySelector('.brand').addEventListener('click', e => { e.preventDefault(); page('home'); });
function render(state) {
  currentState = state;
  $('date-label').textContent = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
  $('pause-button').classList.toggle('paused', state.paused);
  $('pause-button').setAttribute('aria-label', state.paused ? 'Reprendre le suivi' : 'Mettre le suivi en pause');
  $('status-label').textContent = state.paused ? 'Miso fait une pause' : state.tracking === 'ready' ? 'Miso veille sur toi' : state.tracking === 'starting' ? 'Miso se réveille' : 'Suivi indisponible';
  document.querySelector('.pause-icon').textContent = state.paused ? '▷' : 'Ⅱ';
  const error = state.storageError || (state.tracking === 'error' ? 'Le suivi Windows ne répond pas. Tu peux toujours essayer les animations.' : state.tracking === 'unsupported' ? 'Le suivi automatique de ce prototype nécessite Windows.' : '');
  $('error-banner').hidden = !error; $('error-banner').querySelector('span').textContent = error;
  $('retry-button').hidden = !!state.storageError || state.tracking === 'unsupported';
  $('home-time').textContent = time(state.today.total);
  const next = state.stages.find(s => !state.today.fired.includes(s.id));
  $('home-next').textContent = next ? `${next.minutes === 30 ? 'La première balade' : 'Le prochain rappel'} à ${next.minutes} min` : 'Tous les rappels du jour sont passés';
  $('home-progress').style.width = `${Math.min(100, state.today.total / ((next?.minutes || 120) * 60000) * 100)}%`;
  if (currentPage === 'stats') renderStats(state);
  if (currentPage === 'apps') renderApps(state);
}
function renderStats(state) {
  $('stats-total').textContent = time(state.today.total);
  $('stats-interventions').textContent = state.today.interventions;
  $('stats-active').textContent = state.paused ? 'Le suivi est en pause' : state.active?.name || 'Une petite respiration';
  $('stats-active-detail').textContent = state.active ? 'Temps en cours de comptabilisation' : state.tracking === 'ready' ? 'Aucun réseau détecté' : 'Le suivi attend une connexion';
  const days = Array.from({ length: 7 }, (_, i) => { const date = new Date(); date.setDate(date.getDate() - 6 + i); const key = `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`; return { label: new Intl.DateTimeFormat('fr-FR', { weekday: 'short' }).format(date).replace('.', ''), total: state.days[key]?.total || 0 }; });
  const maximum = Math.max(30 * 60000, ...days.map(d => d.total));
  $('week-chart').innerHTML = days.map((day, i) => `<div class="day-column ${i === 6 ? 'current' : ''}" aria-label="${esc(day.label)} : ${esc(time(day.total))}"><span class="bar-value">${Math.floor(day.total / 60000)}</span><div class="bar" style="height:${Math.max(3, day.total / maximum * 105)}px"></div><span>${i === 6 ? 'Auj.' : esc(day.label)}</span></div>`).join('');
  const entries = Object.entries(state.today.byApp).filter(([, value]) => value > 0).sort((a, b) => b[1] - a[1]);
  $('app-breakdown').innerHTML = entries.length ? entries.map(([id, value]) => { const rule = state.rules.find(r => r.id === id); return `<div class="breakdown-row"><span class="breakdown-name">${esc(rule?.name || id)}</span><div class="progress-track"><div style="width:${value / state.today.total * 100}%"></div></div><span class="breakdown-time">${esc(time(value))}</span></div>`; }).join('') : '<div class="empty-state">Une page toute neuve. 🌱<br>Ton temps apparaîtra ici dès que tu utiliseras une application suivie.</div>';
}
function renderApps(state) {
  const key = JSON.stringify(state.rules); if (key === renderedRules) return; renderedRules = key;
  $('app-list').innerHTML = state.rules.map(rule => `<div class="app-row"><span class="app-icon" style="--app-color:${/^#[0-9a-f]{6}$/i.test(rule.color) ? rule.color : '#7e9676'}">${esc(rule.symbol || '◌')}</span><div class="app-meta"><strong>${esc(rule.name)}</strong><small>${esc([rule.keywords.length ? `Onglet : ${rule.keywords.join(', ')}` : '', rule.processes.length ? `App : ${rule.processes.join(', ')}` : ''].filter(Boolean).join(' · '))}</small></div><button class="switch" role="switch" aria-checked="${rule.enabled}" aria-label="Suivre ${esc(rule.name)}" data-rule="${esc(rule.id)}"></button></div>`).join('');
  document.querySelectorAll('[data-rule]').forEach(button => button.addEventListener('click', async () => { button.disabled = true; try { const rules = currentState.rules.map(rule => rule.id === button.dataset.rule ? { ...rule, enabled: !rule.enabled } : rule); render(await api.saveRules(rules)); } catch { toast('Impossible d’enregistrer ce changement.'); button.disabled = false; } }));
}
$('pause-button').addEventListener('click', async () => { try { render(await api.pause()); } catch { toast('Le suivi ne répond pas.'); } });
$('retry-button').addEventListener('click', () => api.retry());
$('rule-form').addEventListener('submit', async event => {
  event.preventDefault();
  const name = $('rule-name').value.trim(), keywords = $('rule-keywords').value.split(',').map(s => s.trim()).filter(Boolean), processes = $('rule-process').value.trim() ? [$('rule-process').value.trim()] : [];
  if (!name || (!keywords.length && !processes.length)) { $('form-message').textContent = 'Ajoute un nom et au moins un mot-clé ou un processus.'; return; }
  if (keywords.length > 20 || keywords.some(k => k.length > 100)) { $('form-message').textContent = 'Maximum 20 mots-clés de 100 caractères.'; return; }
  try { render(await api.saveRules([...currentState.rules, { id: `custom-${crypto.randomUUID()}`, name, keywords, processes, enabled: true, symbol: '◌', color: '#7e9676' }])); $('rule-form').reset(); $('form-message').textContent = ''; document.querySelector('.custom-rule').open = false; toast(`${name} rejoint les applications suivies.`); } catch { $('form-message').textContent = 'Impossible d’ajouter cette application (40 maximum).'; }
});
document.querySelectorAll('[data-preview]').forEach(button => button.addEventListener('click', async () => { try { await api.preview(button.dataset.preview, $('short-preview').checked); } catch { toast('Impossible de lancer l’animation.'); } }));
const phrases = ['Ça fait du bien, un petit câlin ♡', 'Et si on regardait un peu les nuages ?', 'Je suis là. À ton rythme.', 'Tu as pensé à boire un peu d’eau ?', 'Encore un câlin ? Je dis oui.'];
let pets = 0;
$('pet-button').addEventListener('click', () => { const button = $('pet-button'); button.classList.remove('pet'); void button.offsetWidth; button.classList.add('pet'); $('miso-speech').textContent = phrases[pets++ % phrases.length]; clearTimeout(speechTimer); speechTimer = setTimeout(() => { $('miso-speech').textContent = 'Je veille sur toi. Tu profites ♡'; button.classList.remove('pet'); }, 5000); const heart = document.createElement('span'); heart.className = 'heart-pop'; heart.textContent = '♡'; document.querySelector('.companion-scene').append(heart); setTimeout(() => heart.remove(), 1300); });
document.addEventListener('keydown', event => { if (event.key === 'Escape') api.hide(); });
api.onState(render);
api.getState().then(render).catch(() => toast('Impossible de joindre Miso. Relance l’application.'));
