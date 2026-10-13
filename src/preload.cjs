const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('cutizer', {
  getState: () => ipcRenderer.invoke('state'),
  pause: () => ipcRenderer.invoke('pause'),
  saveRules: rules => ipcRenderer.invoke('rules', rules),
  preview: (id, short) => ipcRenderer.invoke('preview', id, short),
  hide: () => ipcRenderer.invoke('hide'),
  retry: () => ipcRenderer.invoke('retry'),
  onState: callback => { const fn = (_, data) => callback(data); ipcRenderer.on('state-update', fn); return () => ipcRenderer.removeListener('state-update', fn); },
  onOverlay: callback => { ipcRenderer.on('overlay-start', (_, data) => callback(data)); ipcRenderer.send('overlay-ready'); }
});
