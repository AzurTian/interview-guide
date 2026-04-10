'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const repoRoot = path.resolve(__dirname, '..', '..', '..');
const stateDir = path.join(repoRoot, '.codex', 'state');
const sessionsDir = path.join(stateDir, 'sessions');

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function readUtf8(filePath) {
  try {
    return fs.readFileSync(filePath, 'utf8');
  } catch {
    return '';
  }
}

function writeUtf8(filePath, content) {
  ensureDir(path.dirname(filePath));
  fs.writeFileSync(filePath, content, 'utf8');
}

function sanitizeSessionId(value) {
  return String(value || '')
    .replace(/[^a-zA-Z0-9_-]/g, '_')
    .slice(0, 80);
}

function getLastSessionPath() {
  return path.join(stateDir, 'last-session.md');
}

function getSessionFilePath(sessionId) {
  const safeId = sanitizeSessionId(sessionId) || 'default';
  return path.join(sessionsDir, `${safeId}.md`);
}

function normalizeFilePath(filePath) {
  if (!filePath) {
    return '';
  }
  const normalized = filePath.replace(/^"+|"+$/g, '');
  return path.isAbsolute(normalized)
    ? path.normalize(normalized)
    : path.normalize(path.join(repoRoot, normalized));
}

function parseJsonLines(filePath) {
  const content = readUtf8(filePath);
  if (!content) {
    return [];
  }

  const entries = [];
  for (const line of content.split(/\r?\n/)) {
    if (!line.trim()) {
      continue;
    }
    try {
      entries.push(JSON.parse(line));
    } catch {
      // Ignore malformed lines so hook execution stays non-blocking.
    }
  }
  return entries;
}

function extractPatchedFilesFromPatch(patchText) {
  const files = new Set();
  const patch = String(patchText || '');
  const filePattern = /^\*\*\* (?:Add|Update|Delete) File: (.+)$/gm;
  const movePattern = /^\*\*\* Move to: (.+)$/gm;

  for (const pattern of [filePattern, movePattern]) {
    let match;
    while ((match = pattern.exec(patch)) !== null) {
      const resolvedPath = normalizeFilePath(match[1].trim());
      if (resolvedPath) {
        files.add(resolvedPath);
      }
    }
  }

  return [...files];
}

function getTurnPatchedFiles(transcriptPath, turnId) {
  const files = new Set();
  const targetTurnId = String(turnId || '');

  if (!transcriptPath || !targetTurnId) {
    return [];
  }

  const entries = parseJsonLines(transcriptPath);
  for (const entry of entries) {
    const payload = entry && typeof entry === 'object' ? entry.payload || {} : {};
    const payloadTurnId = String(payload.turn_id || '');
    if (payloadTurnId !== targetTurnId) {
      continue;
    }

    if (payload.type === 'custom_tool_call' && payload.name === 'apply_patch') {
      for (const filePath of extractPatchedFilesFromPatch(payload.input)) {
        files.add(filePath);
      }
    }
  }

  return [...files];
}

function getSessionSummary(transcriptPath) {
  const entries = parseJsonLines(transcriptPath);
  const userMessages = [];
  const tools = new Set();
  const patchedFiles = new Set();
  let sessionId = '';
  let completedTurns = 0;

  for (const entry of entries) {
    const payload = entry && typeof entry === 'object' ? entry.payload || {} : {};

    if (entry.type === 'session_meta' && payload.id && !sessionId) {
      sessionId = String(payload.id);
    }

    if (entry.type === 'event_msg' && payload.type === 'task_complete') {
      completedTurns += 1;
    }

    if (entry.type === 'response_item') {
      if (payload.type === 'function_call' && payload.name) {
        tools.add(String(payload.name));
      }
      if (payload.type === 'custom_tool_call' && payload.name) {
        tools.add(String(payload.name));
        if (payload.name === 'apply_patch') {
          for (const filePath of extractPatchedFilesFromPatch(payload.input)) {
            patchedFiles.add(filePath);
          }
        }
      }
    }

    if (entry.type === 'user') {
      const text = String(entry.text || entry.content || '').trim();
      if (text) {
        userMessages.push(text);
      }
      continue;
    }

    if (entry.type === 'event_msg' && payload.type === 'user_message') {
      const text = String(payload.text || payload.message || '').trim();
      if (text) {
        userMessages.push(text);
      }
      continue;
    }

  }

  return {
    sessionId,
    completedTurns,
    userMessages: userMessages.slice(-5),
    tools: [...tools].slice(0, 20),
    patchedFiles: [...patchedFiles].slice(0, 40),
  };
}

function formatSummaryMarkdown(summary) {
  const now = new Date().toISOString();
  const lines = [
    '# Codex Session Summary',
    '',
    `- Updated: ${now}`,
    `- Project: ${path.basename(repoRoot)}`,
    `- Repo Root: ${repoRoot}`,
  ];

  if (summary.sessionId) {
    lines.push(`- Session ID: ${summary.sessionId}`);
  }
  lines.push(`- Completed Turns: ${summary.completedTurns}`);
  lines.push('');
  lines.push('## Recent User Requests');

  if (summary.userMessages.length === 0) {
    lines.push('- None captured yet.');
  } else {
    for (const message of summary.userMessages) {
      lines.push(`- ${message.replace(/\s+/g, ' ').slice(0, 280)}`);
    }
  }

  lines.push('');
  lines.push('## Tools Used');
  if (summary.tools.length === 0) {
    lines.push('- None captured yet.');
  } else {
    for (const toolName of summary.tools) {
      lines.push(`- ${toolName}`);
    }
  }

  lines.push('');
  lines.push('## Files Touched');
  if (summary.patchedFiles.length === 0) {
    lines.push('- None captured yet.');
  } else {
    for (const filePath of summary.patchedFiles) {
      lines.push(`- ${path.relative(repoRoot, filePath).replace(/\\/g, '/')}`);
    }
  }

  lines.push('');
  lines.push('## Next Session Notes');
  lines.push('- Continue from the latest touched files or rerun verification if the workspace changed.');

  return `${lines.join('\n')}\n`;
}

function persistSessionSummary(transcriptPath, sessionId) {
  if (!transcriptPath || !fs.existsSync(transcriptPath)) {
    return null;
  }

  const summary = getSessionSummary(transcriptPath);
  const effectiveSessionId =
    sanitizeSessionId(sessionId)
    || sanitizeSessionId(summary.sessionId)
    || crypto.createHash('sha1').update(transcriptPath).digest('hex').slice(0, 12);
  const markdown = formatSummaryMarkdown(summary);

  writeUtf8(getSessionFilePath(effectiveSessionId), markdown);
  writeUtf8(getLastSessionPath(), markdown);

  return {
    ...summary,
    sessionId: effectiveSessionId,
    markdown,
  };
}

function commandExists(command) {
  const result = spawnSync(command, ['--version'], {
    encoding: 'utf8',
    stdio: 'ignore',
    timeout: 5000,
  });
  return !result.error;
}

function runCommand(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd || repoRoot,
    encoding: 'utf8',
    stdio: ['pipe', 'pipe', 'pipe'],
    timeout: options.timeout || 30000,
  });

  return {
    status: typeof result.status === 'number' ? result.status : 1,
    stdout: result.stdout || '',
    stderr: result.stderr || '',
    error: result.error || null,
  };
}

function toPosixPath(filePath) {
  return String(filePath || '').replace(/\\/g, '/');
}

function unique(items) {
  return [...new Set(items.filter(Boolean))];
}

module.exports = {
  commandExists,
  ensureDir,
  extractPatchedFilesFromPatch,
  formatSummaryMarkdown,
  getLastSessionPath,
  getSessionFilePath,
  getSessionSummary,
  getTurnPatchedFiles,
  normalizeFilePath,
  parseJsonLines,
  persistSessionSummary,
  readUtf8,
  repoRoot,
  runCommand,
  sanitizeSessionId,
  sessionsDir,
  stateDir,
  toPosixPath,
  unique,
  writeUtf8,
};
