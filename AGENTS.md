# 仓库指南

## 项目结构与模块组织
`app/` 是 Spring Boot 后端，核心代码位于 `app/src/main/java/interview/guide`，按 `common`、`infrastructure` 和 `modules/{resume,interview,knowledgebase}` 分层。运行时资源放在 `app/src/main/resources`，包括 `application.yml`、提示词模板、脚本和字体。后端测试位于 `app/src/test/java`，测试样例文件位于 `app/src/test/resources/test-files`。`frontend/` 是 Vite + React 前端，主要目录包括 `src/api`、`src/components`、`src/pages`、`src/types` 和 `src/utils`。容器与初始化脚本位于 `docker/` 和 `docker-compose.yml`。

## 构建、测试与开发命令
在仓库根目录运行 `.\gradlew.bat bootRun` 启动后端，默认监听 `8080`。运行 `.\gradlew.bat test` 执行 JUnit 5 测试。前端首次进入 `frontend/` 后执行 `pnpm install`，开发模式使用 `pnpm dev`，构建产物使用 `pnpm build`。需要联调基础设施时，在根目录执行 `docker compose up -d --build`，可同时启动 PostgreSQL、Redis、MinIO、后端和前端。

## 编码与命名规范
Java 使用 4 空格缩进、全小写包名、PascalCase 类名，例如 `ResumeUploadService`。TypeScript/TSX 延续现有风格，使用 2 空格缩进、分号、单引号，组件与页面文件采用 PascalCase，例如 `KnowledgeBaseUploadPage.tsx`，Hook 使用 `useX.ts` 命名。Tailwind 类名直接写在 JSX 中，避免无意义的样式拆分。前端预期遵循 `frontend/eslint.config.js` 中的规则，虽然当前 `package.json` 尚未暴露 lint 脚本。

## 项目技能要求
只要需要新增、删除或修改任何代码、测试、脚本，或会影响运行行为的配置，都必须先使用仓库技能 `code-standard`，技能文件位于 `.codex/skills/code-standard/SKILL.md`。执行代码变更前先读取与本次改动相关的 reference 文件，并在完成后按技能中的验证要求执行检查，在最终说明中明确列出使用了哪些 reference 以及运行了哪些验证命令。

当前仓库内新增或维护任何技能时，技能名都不能以仓库名开头；对本仓库而言，禁止使用 `interview-guide-` 作为技能名前缀。技能名应保持简洁、通用、可复用，例如 `code-standard`、`frontend-design`，不要使用 `interview-guide-code-standard` 这类仓库名前缀命名。

## 文件编码要求
仓库内所有文本文件的输入、输出和保存统一使用 **UTF-8** 编码，不使用 GBK、ANSI 或其他本地编码。新增或修改代码、配置、脚本、Markdown、SQL 与前端资源文件时，都应确认编辑器和终端输出为 UTF-8，避免中文注释、提示词模板和配置文件出现乱码。

## 测试要求
后端测试基于 JUnit 5 和 Spring Boot Test。单元测试使用 `*Test.java` 命名，并与被测代码保持一致的包路径；依赖 Redis 等外部服务的测试使用 `*IntegrationTest.java` 命名，必要时可保留 `@Disabled`。前端目前没有独立测试框架，前端改动至少应确保 `pnpm build` 通过。

## 提交与合并请求规范
提交信息延续现有风格，优先使用 `feat:`、`fix:` 等前缀，主题行保持简短、直接、使用祈使语气。提交应尽量聚焦，避免把后端、前端和基础设施改动混在同一次提交中。Pull Request 需要说明问题背景、解决方案、配置或数据结构变更，并在涉及界面调整时附上截图。

## 配置与安全
从 `.env.example` 派生本地环境变量，不要提交真实密钥，尤其是 `AI_BAILIAN_API_KEY`。如果修改数据库、Redis、对象存储或端口配置，请同时检查 `docker-compose.yml` 与 `app/src/main/resources/application.yml`，确保本地运行和容器部署保持一致。
