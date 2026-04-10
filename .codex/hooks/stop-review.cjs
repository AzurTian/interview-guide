'use strict';

const fs = require('fs');
const path = require('path');
const {
  commandExists,
  getTurnPatchedFiles,
  persistSessionSummary,
  repoRoot,
  runCommand,
  toPosixPath,
  unique,
} = require('./lib/common.cjs');

const MAX_STDIN = 1024 * 1024;
const PROTECTED_BASENAMES = new Set([
  '.eslintrc',
  '.eslintrc.js',
  '.eslintrc.cjs',
  '.eslintrc.json',
  '.eslintrc.yml',
  '.eslintrc.yaml',
  'eslint.config.js',
  'eslint.config.mjs',
  'eslint.config.cjs',
  'eslint.config.ts',
  '.prettierrc',
  '.prettierrc.js',
  '.prettierrc.cjs',
  '.prettierrc.json',
  '.prettierrc.yml',
  '.prettierrc.yaml',
  'prettier.config.js',
  'prettier.config.cjs',
  'prettier.config.mjs',
  'biome.json',
  'biome.jsonc',
  '.ruff.toml',
  'ruff.toml',
]);

const ADHOC_DOC_NAMES = /^(NOTES|TODO|SCRATCH|TEMP|DRAFT|BRAINSTORM|SPIKE|DEBUG|WIP)\.(md|txt)$/;
const STRUCTURED_DOC_DIRS = /(^|\/)(docs|\.codex|\.github|commands|skills|benchmarks|templates|memory)\//;
const DESIGN_SIGNALS = [
  { pattern: /\bget started\b/i, label: '"Get Started" CTA copy' },
  { pattern: /\blearn more\b/i, label: '"Learn more" CTA copy' },
  { pattern: /\bgrid-cols-(3|4)\b/, label: 'uniform multi-card grid' },
  { pattern: /\bbg-gradient-to-[trbl]/, label: 'stock gradient utility usage' },
  { pattern: /\btext-center\b/, label: 'centered default layout cue' },
  { pattern: /\bfont-(sans|inter)\b/i, label: 'default font utility' },
];

function readInput() {
  return new Promise((resolve) => {
    let raw = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => {
      if (raw.length < MAX_STDIN) {
        raw += chunk.slice(0, MAX_STDIN - raw.length);
      }
    });
    process.stdin.on('end', () => {
      try {
        resolve(JSON.parse(raw || '{}'));
      } catch {
        resolve({});
      }
    });
  });
}

function readFileContent(filePath) {
  try {
    return fs.readFileSync(filePath, 'utf8');
  } catch {
    return '';
  }
}

function isFrontendCodeFile(filePath) {
  const posixPath = toPosixPath(path.relative(repoRoot, filePath));
  return posixPath.startsWith('frontend/') && /\.(ts|tsx|js|jsx)$/.test(posixPath);
}

function isFrontendDesignFile(filePath) {
  const posixPath = toPosixPath(path.relative(repoRoot, filePath));
  return posixPath.startsWith('frontend/') && /\.(tsx|jsx|css|scss|html)$/.test(posixPath);
}

function findConsoleIssues(files) {
  const issues = [];
  for (const filePath of files) {
    const relativePath = toPosixPath(path.relative(repoRoot, filePath));
    if (/(\.test\.|\.spec\.|\/__tests__\/|\/__mocks__\/|\.config\.)/.test(relativePath)) {
      continue;
    }
    const lines = readFileContent(filePath).split(/\r?\n/);
    lines.forEach((line, index) => {
      if (/console\.log/.test(line)) {
        issues.push(`${relativePath}:${index + 1} contains console.log.`);
      }
    });
  }
  return issues;
}

function detectDesignWarnings(files) {
  const warnings = [];
  for (const filePath of files) {
    const content = readFileContent(filePath);
    const matchedSignals = DESIGN_SIGNALS.filter((signal) => signal.pattern.test(content)).map((signal) => signal.label);
    if (matchedSignals.length > 0) {
      warnings.push(`Design review for ${toPosixPath(path.relative(repoRoot, filePath))}: ${matchedSignals.join(', ')}.`);
    }
  }
  return warnings;
}

function detectDocWarnings(files) {
  const warnings = [];
  for (const filePath of files) {
    const relativePath = toPosixPath(path.relative(repoRoot, filePath));
    const baseName = path.basename(relativePath);
    if (!ADHOC_DOC_NAMES.test(baseName)) {
      continue;
    }
    if (STRUCTURED_DOC_DIRS.test(relativePath)) {
      continue;
    }
    warnings.push(`Ad-hoc doc filename detected: ${relativePath}. Prefer a structured path such as docs/ or .codex/.`);
  }
  return warnings;
}

function resolveTypecheckCommand(frontendRoot) {
  const localTsc = path.join(frontendRoot, 'node_modules', 'typescript', 'bin', 'tsc');
  if (fs.existsSync(localTsc)) {
    return {
      command: process.execPath,
      args: [localTsc, '--noEmit', '-p', 'tsconfig.json', '--pretty', 'false'],
      cwd: frontendRoot,
    };
  }

  const corepackCommand = process.platform === 'win32' ? 'corepack.cmd' : 'corepack';
  if (commandExists(corepackCommand)) {
    return {
      command: corepackCommand,
      args: ['pnpm', 'exec', 'tsc', '--noEmit', '-p', 'tsconfig.json', '--pretty', 'false'],
      cwd: frontendRoot,
    };
  }

  const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
  if (commandExists(npmCommand)) {
    return {
      command: npmCommand,
      args: ['exec', '--', 'tsc', '--noEmit', '-p', 'tsconfig.json', '--pretty', 'false'],
      cwd: frontendRoot,
    };
  }

  return null;
}

function filterTypecheckLines(output, touchedFiles, frontendRoot) {
  const lines = output.split(/\r?\n/).filter(Boolean);
  const candidates = touchedFiles.flatMap((filePath) => {
    const relativeToRepo = toPosixPath(path.relative(repoRoot, filePath));
    const relativeToFrontend = toPosixPath(path.relative(frontendRoot, filePath));
    const absolutePath = toPosixPath(filePath);
    return [relativeToRepo, relativeToFrontend, absolutePath];
  });

  const matchingLines = [];
  for (const line of lines) {
    if (candidates.some((candidate) => candidate && toPosixPath(line).includes(candidate))) {
      matchingLines.push(line);
    }
  }
  return matchingLines.slice(0, 16);
}

function runFrontendTypecheck(touchedFiles) {
  const frontendRoot = path.join(repoRoot, 'frontend');
  const command = resolveTypecheckCommand(frontendRoot);
  if (!command) {
    return {
      status: 'skipped',
      message: 'Frontend typecheck skipped because no local TypeScript runtime was found.',
    };
  }

  const result = runCommand(command.command, command.args, {
    cwd: command.cwd,
    timeout: 120000,
  });

  if (result.error) {
    return {
      status: 'skipped',
      message: `Frontend typecheck skipped: ${result.error.message}`,
    };
  }

  if (result.status === 0) {
    return { status: 'ok', message: '' };
  }

  const relevantLines = filterTypecheckLines(`${result.stdout}\n${result.stderr}`, touchedFiles, frontendRoot);
  if (relevantLines.length === 0) {
    return {
      status: 'unrelated-failure',
      message: 'Frontend typecheck failed, but the reported errors did not map to files changed in this turn.',
    };
  }

  return {
    status: 'relevant-failure',
    message: relevantLines.join('\n'),
  };
}

function countCompletedTurns(transcriptPath) {
  if (!transcriptPath || !fs.existsSync(transcriptPath)) {
    return 0;
  }

  const content = fs.readFileSync(transcriptPath, 'utf8');
  let count = 0;
  for (const line of content.split(/\r?\n/)) {
    if (!line.trim()) {
      continue;
    }
    try {
      const entry = JSON.parse(line);
      if (entry.type === 'event_msg' && entry.payload?.type === 'task_complete') {
        count += 1;
      }
    } catch {
      // Ignore malformed lines.
    }
  }
  return count;
}

function buildContinuePayload(messages) {
  const payload = { continue: true };
  if (messages.length > 0) {
    payload.systemMessage = messages.join('\n\n');
  }
  return payload;
}

async function main() {
  const input = await readInput();
  const transcriptPath = String(input.transcript_path || '');
  const turnId = String(input.turn_id || '');
  const stopHookActive = Boolean(input.stop_hook_active);
  const sessionId = String(input.session_id || '');

  persistSessionSummary(transcriptPath, sessionId);

  const turnFiles = unique(getTurnPatchedFiles(transcriptPath, turnId)).filter((filePath) => fs.existsSync(filePath) || PROTECTED_BASENAMES.has(path.basename(filePath)));
  const blockingIssues = [];
  const warningMessages = [];

  const protectedFiles = turnFiles
    .filter((filePath) => PROTECTED_BASENAMES.has(path.basename(filePath)))
    .map((filePath) => toPosixPath(path.relative(repoRoot, filePath)));
  if (protectedFiles.length > 0) {
    blockingIssues.push(
      `Protected config file changed in this turn: ${protectedFiles.join(', ')}. Fix code instead of weakening formatter or linter rules unless the user explicitly requested a config change.`
    );
  }

  const frontendCodeFiles = turnFiles.filter(isFrontendCodeFile);
  const consoleIssues = findConsoleIssues(frontendCodeFiles);
  if (consoleIssues.length > 0) {
    blockingIssues.push(`Remove console.log before finishing the turn:\n${consoleIssues.slice(0, 10).join('\n')}`);
  }

  if (frontendCodeFiles.length > 0) {
    const typecheck = runFrontendTypecheck(frontendCodeFiles);
    if (typecheck.status === 'relevant-failure') {
      blockingIssues.push(`Frontend TypeScript check failed for files changed in this turn:\n${typecheck.message}`);
    } else if (typecheck.status === 'skipped' || typecheck.status === 'unrelated-failure') {
      warningMessages.push(typecheck.message);
    }
  }

  const designWarnings = detectDesignWarnings(turnFiles.filter(isFrontendDesignFile));
  warningMessages.push(...designWarnings);
  warningMessages.push(...detectDocWarnings(turnFiles));

  const completedTurns = countCompletedTurns(transcriptPath);
  if (completedTurns > 0 && completedTurns % 15 === 0) {
    warningMessages.push(`This session has completed ${completedTurns} turns. Consider /compact if the context is getting stale.`);
  }

  if (blockingIssues.length > 0 && !stopHookActive) {
    process.stdout.write(JSON.stringify({
      decision: 'block',
      reason: blockingIssues.join('\n\n'),
      systemMessage: [...blockingIssues, ...warningMessages].join('\n\n'),
    }));
    return;
  }

  process.stdout.write(JSON.stringify(buildContinuePayload([
    ...blockingIssues,
    ...warningMessages,
  ])));
}

main().catch((error) => {
  process.stdout.write(JSON.stringify(buildContinuePayload([
    `stop-review hook error: ${error.message}`,
  ])));
});
