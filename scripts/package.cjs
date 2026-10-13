const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '..');
const out = path.join(root, 'dist', 'Cutizer');
// Resolve Electron first so a fresh install downloads its binary if necessary.
const electronDist = path.dirname(require('electron'));
fs.mkdirSync(out, { recursive: true });
fs.cpSync(electronDist, out, { recursive: true });
const resources = path.join(out, 'resources', 'app');
fs.mkdirSync(resources, { recursive: true });
fs.cpSync(path.join(root, 'src'), path.join(resources, 'src'), { recursive: true });
fs.copyFileSync(path.join(root, 'package.json'), path.join(resources, 'package.json'));
fs.renameSync(path.join(out, 'electron.exe'), path.join(out, 'Cutizer.exe'));
fs.copyFileSync(path.join(root, 'README.md'), path.join(out, 'LIRE-MOI.md'));
console.log(`Application prête : ${path.join(out, 'Cutizer.exe')}`);
