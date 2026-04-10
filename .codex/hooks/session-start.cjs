'use strict';

const fs = require('fs');
const path = require('path');
const {
  getLastSessionPath,
  readUtf8,
  repoRoot,
} = require('./lib/common.cjs');

function buildAdditionalContext() {
  const contextParts = [];
  const lastSessionPath = getLastSessionPath();
  const lastSession = readUtf8(lastSessionPath).trim();

  if (lastSession) {
    contextParts.push(`Previous session summary:\n${lastSession}`);
  }

  const projectMarkers = [];
  if (fs.existsSync(path.join(repoRoot, 'app', 'build.gradle'))) {
    projectMarkers.push('Spring Boot backend');
  }
  if (fs.existsSync(path.join(repoRoot, 'frontend', 'package.json'))) {
    projectMarkers.push('Vite + React frontend');
  }
  if (projectMarkers.length > 0) {
    contextParts.push(`Project type: ${projectMarkers.join(' + ')}`);
  }

  contextParts.push('Project-level Codex hooks are installed for session memory, stop-time review, and git-commit quality checks.');

  return contextParts.join('\n\n');
}

function main() {
  const additionalContext = buildAdditionalContext();
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: 'SessionStart',
      additionalContext,
    },
  }));
}

main();
