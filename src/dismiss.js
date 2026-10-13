document.getElementById('hide').addEventListener('click', () => window.cutizer.hide());
window.cutizer.onOverlay(data => {
  const start = performance.now();
  function label() { const seconds = Math.max(0, Math.ceil((data.duration - (performance.now() - start)) / 1000)); document.getElementById('overlay-label').textContent = `${data.preview ? 'Essai' : 'Petite pause'} · ${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2,'0')}`; }
  label(); setInterval(label, 250);
});
