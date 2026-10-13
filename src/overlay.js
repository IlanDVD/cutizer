const actor = document.getElementById('actor'), effect = document.getElementById('effect');
window.cutizer.onOverlay(data => {
  effect.className = data.id;
  effect.style.setProperty('--duration', `${data.duration}ms`);
  document.getElementById('sprite').src = ['judge', 'rappel'].includes(data.id) ? 'assets/miso-judge.png' : 'assets/miso-happy.png';
  document.getElementById('bubble').textContent = { walk: 'Je ne fais que passer…', rappel: 'Encore un petit scroll ?', judge: 'Moi ? J’ai tout mon temps.', magic: 'Un peu de magie. Beaucoup de grand air.' }[data.id];
  if (data.id === 'magic') {
    const colors = ['#c5d6b2', '#d6cee7', '#ece0b4', '#b9cfbd', '#e3c8be', '#d3dfbf'];
    const symbols = ['✦', '✧', '❋', '✿', '♡'];
    // An opaque veil and overlapping spells obscure text without intercepting clicks.
    for (let i = 0; i < 72; i++) {
      const spell = document.createElement('span'); spell.className = 'spell';
      const x = (i % 12) * 9 - 4, y = Math.floor(i / 12) * 21 - 7;
      spell.style.cssText = `left:${x}%;top:${y}%;--x:${x}vw;--y:${y}vh;--size:${13 + (i % 4) * 3}vw;--radius:${i % 2 ? '42% 58% 65% 35%' : '50%'};--color:${colors[i % colors.length]};--delay:${(i % 12) * .08}s;--float-duration:${4 + i % 4}s`;
      spell.textContent = symbols[i % symbols.length]; document.getElementById('spells').append(spell);
    }
  }
  // 90 min: 1 s entrance + 120 s waiting + 1 s exit. Magic: 600 s + 2 s fade.
  setTimeout(() => { effect.style.transitionDuration = data.id === 'magic' ? '2s' : '1s'; effect.classList.add('leaving'); }, Math.max(0, data.duration - (data.id === 'magic' ? 2000 : 1000)));
});
