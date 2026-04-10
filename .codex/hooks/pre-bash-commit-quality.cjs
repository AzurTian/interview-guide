'use strict';

const fs = require('fs');
const path = require('path');
const {
  repoRoot,
  runCommand,
  toPosixPath,
} = require('./lib/common.cjs');

const MAX_STDIN = 1024 * 1024;
const SECRET_PATTERNS = [
  { name: 'OpenAI API key', pattern: /sk-[A-Za-z0-9]{20,}/ },
  { name: 'GitHub token', pattern: /\bgh[pousr]_[A-Za-z0-9_]{20,}\b/ },
  { name: 'AWS access key', pattern: /\bAKIA[A-Z0-9]{16}\b/ },
  { name: 'Generic API key assignment', pattern: /api[_-]?key\s*[:=]\s*['"][^'"]+['"]/i },
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

function getStagedFiles() {
  const result = runCommand('git', ['diff', '--cached', '--name-only', '--diff-filter=ACMR'], {
    cwd: repoRoot,
    timeout: 10000,
  });
  if (result.status !== 0) {
    return [];
  }
  return result.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}

function getStagedFileContent(filePath) {
  const result = runCommand('git', ['show', `:${filePath}`], {
    cwd: repoRoot,
    timeout: 10000,
  });
  return result.status === 0 ? result.stdout : '';
}

function validateCommitMessage(command) {
  const match = command.match(/(?:-m|--message)(?:=|\s+)(["'])(.*?)\1|(?:-m|--message)(?:=|\s+)(\S.*)$/);
  if (!match) {
    return [];
  }

  const message = (match[2] || match[3] || '').trim();
  const issues = [];
  const conventionalCommit = /^(feat|fix|docs|style|refactor|test|chore|build|ci|perf|revert)(\(.+\))?:\s+.+/;

  if (!conventionalCommit.test(message)) {
    issues.push('Commit message should use Conventional Commit format, for example `feat(resume): add upload validation`.');
  }
  if (message.length > 72) {
    issues.push(`Commit message is ${message.length} characters long. Keep the subject under 72 characters.`);
  }
  if (message.endsWith('.')) {
    issues.push('Commit message subject should not end with a period.');
  }

  return issues;
}

function findContentIssues(filePath, content) {
  const issues = [];
  const lines = String(content || '').split(/\r?\n/);

  lines.forEach((line, index) => {
    const lineNumber = index + 1;
    if (/console\.log/.test(line) && !/^\s*(\/\/|\*)/.test(line)) {
      issues.push(`${filePath}:${lineNumber} contains console.log.`);
    }
    if (/\bdebugger\b/.test(line) && !/^\s*\/\//.test(line)) {
      issues.push(`${filePath}:${lineNumber} contains debugger.`);
    }
    if (/TODO|FIXME/.test(line) && !/#\d+/.test(line)) {
      issues.push(`${filePath}:${lineNumber} has TODO/FIXME without an issue reference.`);
    }
    for (const pattern of SECRET_PATTERNS) {
      if (pattern.pattern.test(line)) {
        issues.push(`${filePath}:${lineNumber} may expose ${pattern.name}.`);
      }
    }
  });

  return issues;
}

function resolveFrontendEslintCommand(frontendRoot) {
  const eslintJs = path.join(frontendRoot, 'node_modules', 'eslint', 'bin', 'eslint.js');
  if (fs.existsSync(eslintJs)) {
    return {
      command: process.execPath,
      args: [eslintJs, '--format', 'compact'],
      cwd: frontendRoot,
    };
  }
  return null;
}

function runFrontendLint(stagedFiles) {
  const frontendRoot = path.join(repoRoot, 'frontend');
  const jsFiles = stagedFiles
    .map((filePath) => toPosixPath(filePath))
    .filter((filePath) => filePath.startsWith('frontend/'))
    .filter((filePath) => /\.(ts|tsx|js|jsx)$/.test(filePath))
    .map((filePath) => filePath.slice('frontend/'.length));

  if (jsFiles.length === 0) {
    return null;
  }

  const command = resolveFrontendEslintCommand(frontendRoot);
  if (!command) {
    return null;
  }

  const result = runCommand(command.command, [...command.args, ...jsFiles], {
    cwd: command.cwd,
    timeout: 45000,
  });

  return result.status === 0 ? null : (result.stdout || result.stderr || 'ESLint reported errors.');
}

function buildBlockPayload(issues) {
  const reason = issues.slice(0, 10).join('\n');
  return {
    decision: 'block',
    reason,
    systemMessage: `Commit blocked by project hook:\n${reason}`,
  };
}

async function main() {
  const input = await readInput();
  const command = String(input.tool_input?.command || '');

  if (!/\bgit\s+commit\b/.test(command) || /\b--amend\b/.test(command)) {
    return;
  }

  const issues = [];
  const stagedFiles = getStagedFiles();

  if (stagedFiles.length === 0) {
    process.stdout.write(JSON.stringify(buildBlockPayload([
      'No staged files found. Stage your changes with `git add` before committing.',
    ])));
    return;
  }

  issues.push(...validateCommitMessage(command));

  for (const filePath of stagedFiles) {
    if (!/\.(ts|tsx|js|jsx|java|md|txt|yml|yaml|json|properties|sql)$/.test(filePath)) {
      continue;
    }
    issues.push(...findContentIssues(filePath, getStagedFileContent(filePath)));
  }

  const lintOutput = runFrontendLint(stagedFiles);
  if (lintOutput) {
    issues.push(`Frontend ESLint failed:\n${lintOutput.split(/\r?\n/).slice(0, 12).join('\n')}`);
  }

  if (issues.length > 0) {
    process.stdout.write(JSON.stringify(buildBlockPayload(issues)));
  }
}

main().catch((error) => {
  process.stdout.write(JSON.stringify({
    decision: 'block',
    reason: `pre-bash-commit-quality failed: ${error.message}`,
    systemMessage: `Commit blocked because the hook crashed: ${error.message}`,
  }));
});
