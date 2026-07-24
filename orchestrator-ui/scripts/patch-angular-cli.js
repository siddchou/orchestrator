const fs = require('fs');
const path = require('path');

const cliPath = path.join(__dirname, '..', 'node_modules', '@angular', 'cli', 'src', 'utilities', 'node-version.js');

if (!fs.existsSync(cliPath)) {
  console.log('@angular/cli node-version.js not found (Angular 21 or different structure) — skipping patch');
  process.exit(0);
}

const content = fs.readFileSync(cliPath, 'utf8');

// Relax Node 24 requirement from 24.15.0 to 24.13.0 to support v24.14.1
if (content.includes('24.15.0') && !content.includes('24.13.0')) {
  fs.writeFileSync(cliPath, content.replace('24.15.0', '24.13.0'));
  console.log('Patched @angular/cli to accept Node 24.13+');
} else {
  console.log('@angular/cli already patched or no patch needed');
}
