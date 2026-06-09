<div align="center">

# Interview Guide

**AI Interview Platform：简历分析、模拟面试、语音面试、面试日程与 RAG 知识库的一体化平台**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-6DB33F?logo=spring)](https://spring.io/projects/spring-ai)
[![React](https://img.shields.io/badge/React-18.3-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue?logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://github.com/pgvector/pgvector)
[![License](https://img.shields.io/badge/License-AGPL--3.0-blue)](LICENSE)

</div>

## 项目简介

Interview Guide 是一个面向求职者、HR、培训机构和面试训练场景的 AI 面试平台。项目以 Spring Boot 4.0 + Java 21 为后端核心，结合 Spring AI、多 LLM Provider、PostgreSQL + pgvector、Redis Stream、S3 兼容对象存储和 React 前端，实现从简历上传、AI 分析、文字/语音模拟面试、评估报告导出，到知识库 RAG 问答和面试日程管理的完整闭环。

平台重点关注真实业务链路，而不是单点 Demo：文件解析、异步任务、限流、结构化输出重试、运行时模型配置、语音 WebSocket、PDF 导出、向量检索、会话管理和统一异常响应均已落地。

## 功能特性

| 模块 | 能力 |
| --- | --- |
| 简历管理 | 支持 PDF、DOC、DOCX、TXT 上传解析，内容哈希去重，AI 简历评分，异步分析进度跟踪，PDF 报告导出 |
| 模拟面试 | 支持基于简历、JD 和 Skill 的智能出题，多轮追问，回答评估，历史题目去重，面试报告导出 |
| 语音面试 | 基于 WebSocket 的实时语音面试，集成 Qwen ASR/TTS，支持实时字幕、暂停恢复、手动提交和重新评估 |
| 知识库 | 文档上传、解析、分块、pgvector 向量化、RAG 查询、SSE 流式回答、会话管理和知识库统计 |
| 面试日程 | AI 解析面试邀请，日历视图，状态流转，提醒时间管理，面试信息 CRUD |
| 多模型配置 | 支持 DashScope、Kimi、DeepSeek、GLM、LM Studio 等 OpenAI 兼容 Provider，支持默认模型和语音配置切换 |
| 基础能力 | 统一响应包装、全局异常处理、Redis Lua 滑动窗口限流、Redis Stream 异步任务、MapStruct 映射、OpenAPI 文档 |

## 技术栈

**后端**

| 技术 | 说明 |
| --- | --- |
| Java 21 | 虚拟线程，现代 Java 语法 |
| Spring Boot 4.0 | Web MVC、Validation、JPA、WebSocket |
| Spring AI 2.0 | ChatClient、OpenAI 兼容模型、pgvector 向量存储 |
| PostgreSQL + pgvector | 业务数据与向量检索 |
| Redis + Redisson | 缓存、限流、Redis Stream 异步任务 |
| Apache Tika | PDF、Word、文本等文档解析 |
| AWS S3 SDK | MinIO/RustFS/S3 兼容文件存储 |
| iText 8 | PDF 报告导出 |
| MapStruct + Lombok | DTO/Entity 映射与样板代码简化 |
| SpringDoc OpenAPI | Swagger API 文档 |

**前端**

| 技术 | 说明 |
| --- | --- |
| React 18 | 页面与组件开发 |
| TypeScript 5.6 | 类型约束 |
| Vite 5 | 前端构建与开发服务器 |
| TailwindCSS 4 | 样式系统 |
| React Router 7 | 页面路由 |
| Framer Motion | 动效 |
| Recharts | 图表与评分可视化 |
| React Big Calendar | 面试日程日历 |
| React Virtuoso | RAG 聊天长列表渲染 |

## 系统架构

```text
Frontend React
  |
  | HTTP / SSE / WebSocket
  v
Spring Boot API
  |
  |-- modules/resume              简历上传、解析、分析、导出
  |-- modules/interview           文字模拟面试、出题、评估、报告
  |-- modules/voiceinterview      实时语音面试、ASR/TTS、WebSocket
  |-- modules/knowledgebase       文档向量化、RAG 查询、聊天会话
  |-- modules/interviewschedule   面试日程和邀请解析
  |-- modules/llmprovider         多模型 Provider 管理
  |
  |-- common                      限流、异常、AI 调用、异步模板、配置
  |-- infrastructure              Redis、文件存储、文档解析、PDF、Mapper
  |
  |-- PostgreSQL + pgvector       业务数据和向量数据
  |-- Redis                       缓存、限流、Stream 队列
  |-- MinIO / RustFS              S3 兼容对象存储
  |-- LLM Provider                DashScope / Kimi / DeepSeek / GLM / LM Studio
```

## 项目结构

```text
interview-guide/
├── app/                                  # Spring Boot 后端
│   ├── build.gradle
│   └── src/main/
│       ├── java/interview/guide/
│       │   ├── App.java                  # 启动类
│       │   ├── common/                   # 通用基础能力
│       │   ├── infrastructure/           # 技术基础设施
│       │   └── modules/                  # 业务模块
│       └── resources/
│           ├── application.yml           # 应用配置
│           ├── prompts/                  # AI Prompt 模板
│           ├── scripts/                  # Redis Lua 脚本
│           └── skills/                   # 面试 Skill 定义
├── frontend/                             # React + Vite 前端
│   ├── package.json
│   └── src/
│       ├── api/                          # API 请求封装
│       ├── components/                   # 组件
│       ├── hooks/                        # Hooks
│       ├── pages/                        # 页面
│       ├── types/                        # 类型定义
│       └── utils/                        # 工具函数
├── docker/
│   └── postgres/init.sql                 # pgvector 扩展初始化
├── docker-compose.dev.yml                # 本地开发依赖服务
├── docker-compose.yml                    # 完整 Docker 部署
├── .env.example                          # 环境变量示例
└── README.md
```

## 环境要求

| 依赖 | 版本建议 | 说明 |
| --- | --- | --- |
| JDK | 21+ | 后端运行和构建 |
| Node.js | 18+ | 前端开发和构建 |
| pnpm | 10+ | 前端包管理器，项目锁定 pnpm 10.26 |
| Docker | 最新稳定版 | 推荐用于启动 PostgreSQL、Redis、对象存储 |

如果不使用 Docker，需要自行准备 PostgreSQL 16 + pgvector、Redis 7 和 S3 兼容对象存储。

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/AzurTian/interview-guide.git
cd interview-guide
```

### 2. 配置环境变量

复制环境变量模板：

```bash
cp .env.example .env
```

至少需要配置一个可用的 LLM API Key。默认语音面试 ASR/TTS 也使用 DashScope：

```properties
AI_BAILIAN_API_KEY=your_dashscope_api_key
AI_MODEL=qwen3.5-flash
```

`.env.example` 中还包含 PostgreSQL、Redis、对象存储、多 Provider 和语音面试参数。后端 `bootRun` 会自动读取仓库根目录 `.env`。

### 3. 启动开发依赖

```bash
docker compose -f docker-compose.dev.yml up -d
```

开发依赖包含：

| 服务 | 地址 | 默认说明 |
| --- | --- | --- |
| PostgreSQL + pgvector | `localhost:5432` | 数据库 `interview_guide` |
| Redis | `localhost:6379` | 缓存、限流、Stream |
| RustFS | `http://localhost:9001` | S3 兼容对象存储控制台 |

注意：`docker-compose.dev.yml` 使用 RustFS，默认账号密码为 `rustfsadmin` / `rustfsadmin`。如果你从 `.env.example` 复制配置，建议将开发环境对象存储配置改为：

```properties
APP_STORAGE_ENDPOINT=http://localhost:9000
APP_STORAGE_ACCESS_KEY=rustfsadmin
APP_STORAGE_SECRET_KEY=rustfsadmin
APP_STORAGE_BUCKET=interview-guide
```

首次启动后访问 `http://localhost:9001`，创建名为 `interview-guide` 的 bucket。

### 4. 启动后端

Windows PowerShell：

```powershell
.\gradlew.bat :app:bootRun
```

macOS / Linux：

```bash
./gradlew :app:bootRun
```

后端默认访问地址：

```text
http://localhost:8080
```

Swagger 文档：

```text
http://localhost:8080/swagger-ui.html
```

### 5. 启动前端

```bash
cd frontend
corepack enable
pnpm install
pnpm dev
```

前端默认访问地址：

```text
http://localhost:5173
```

## Docker 部署

完整部署会启动 PostgreSQL、Redis、MinIO、Bucket 初始化任务、后端和前端：

```bash
cp .env.example .env
# 编辑 .env，至少填写 AI_BAILIAN_API_KEY
docker compose up -d --build
```

服务入口：

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 前端 | `http://localhost` | Nginx 托管的 React 应用 |
| 后端 API | `http://localhost:8080` | Spring Boot API |
| Swagger | `http://localhost:8080/swagger-ui.html` | OpenAPI 文档 |
| MinIO Console | `http://localhost:9001` | 默认 `minioadmin` / `minioadmin` |
| PostgreSQL | `localhost:5432` | 默认用户 `postgres` |
| Redis | `localhost:6379` | 默认无密码 |

常用命令：

```bash
docker compose ps
docker compose logs -f app
docker compose up -d --build
docker compose down
docker compose down -v
```

## 常用开发命令

后端：

```bash
.\gradlew.bat :app:bootRun
.\gradlew.bat :app:test
.\gradlew.bat :app:build
```

前端：

```bash
cd frontend
pnpm dev
pnpm build
pnpm preview
```

依赖服务：

```bash
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml down
docker compose -f docker-compose.dev.yml down -v
```

## 核心接口

完整接口以 Swagger 为准：`http://localhost:8080/swagger-ui.html`。

| 模块 | 接口前缀 |
| --- | --- |
| 简历 | `/api/resumes` |
| 文字面试 | `/api/interview` |
| 面试 Skill | `/api/interview/skills` |
| 语音面试 | `/api/voice-interview` |
| 知识库 | `/api/knowledgebase` |
| RAG 聊天 | `/api/rag-chat` |
| 面试日程 | `/api/interview-schedule` |
| LLM Provider | `/api/llm-provider` |

## 配置说明

主要配置集中在 `app/src/main/resources/application.yml` 和根目录 `.env`。

| 配置项 | 说明 |
| --- | --- |
| `AI_BAILIAN_API_KEY` | DashScope API Key，用于默认文本模型、ASR、TTS |
| `AI_MODEL` | 默认 DashScope 文本模型 |
| `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` | PostgreSQL 连接 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 连接 |
| `APP_STORAGE_ENDPOINT` | S3 兼容对象存储 endpoint |
| `APP_STORAGE_ACCESS_KEY` / `APP_STORAGE_SECRET_KEY` | 对象存储凭据 |
| `APP_STORAGE_BUCKET` | 文件 bucket |
| `APP_AI_CONFIG_ENCRYPTION_KEY` | 可选，运行时 Provider API Key 加密密钥 |
| `APP_INTERVIEW_FOLLOW_UP_COUNT` | 模拟面试追问数量 |
| `APP_INTERVIEW_EVALUATION_BATCH_SIZE` | 面试回答分批评估大小 |

运行时新增或修改的 LLM Provider 默认写入：

```text
~/.interview-guide/llm-providers.yml
~/.interview-guide/llm-providers.env
```

## 开发规范摘要

项目按功能分包，遵循 `Controller -> Service -> Repository` 分层。Controller 只负责路由和委托，Service 编排业务逻辑，Repository 使用 Spring Data JPA。

关键约束：

| 规则 | 要求 |
| --- | --- |
| 响应格式 | 统一返回 `Result<T>` |
| 异常 | 业务异常使用 `BusinessException(ErrorCode.XXX, message)` |
| DTO | 禁止直接返回 Entity 给前端 |
| 配置 | 业务配置使用 `@ConfigurationProperties`，避免 Service 中散落 `@Value` |
| 事务 | `@Transactional` 放 Service 层，事务内不调用 LLM/S3 等外部 API |
| 异步任务 | 使用 `AbstractStreamProducer` / `AbstractStreamConsumer` |
| AI 调用 | 通过 `LlmProviderRegistry.getChatClientOrDefault(provider)` 获取 ChatClient |
| 代码风格 | Java 2 空格缩进，禁止通配符导入，优先使用 record 作为不可变请求/响应对象 |

## 常见问题

### 知识库向量化失败

确认 PostgreSQL 已安装 pgvector 扩展。Docker 环境会通过 `docker/postgres/init.sql` 自动执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

开发环境建议保持：

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true
```

### 简历分析或面试评估失败

优先检查 `.env` 中的模型 API Key、模型名和网络连通性。也可以在系统设置页测试 Provider 连接。

### 简历或知识库上传失败

检查对象存储是否启动、bucket 是否存在、`APP_STORAGE_*` 配置是否与当前 Docker Compose 文件一致。

### 语音面试没有声音或无法识别

检查浏览器麦克风权限、DashScope API Key、ASR/TTS 配置和后端 WebSocket 日志。无耳机环境可能出现回声录入，建议使用耳机或手动提交模式测试。

### Windows PowerShell 日志中文乱码

在启动后端前执行：

```powershell
chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
```

## 路线图

- [x] 简历上传、解析、AI 分析和 PDF 导出
- [x] 文字模拟面试、追问、评估和报告导出
- [x] Redis Stream 异步任务与重试机制
- [x] 知识库上传、向量化和 RAG 问答
- [x] 语音面试 ASR/TTS/WebSocket 实时链路
- [x] 多 LLM Provider 管理与默认模型切换
- [x] 面试日程管理和邀请解析
- [ ] 面试与知识库深度联动
- [ ] 语音面试接入 WebRTC 降低端到端延迟
- [ ] 更完善的权限体系和多用户隔离
- [ ] 生产级迁移脚本与部署监控方案

## 贡献

欢迎提交 Issue 和 Pull Request。建议在提交前执行：

```bash
.\gradlew.bat :app:test
cd frontend
pnpm build
```

## 许可证

本项目基于 [AGPL-3.0](LICENSE) 开源。若通过网络提供服务，需要按 AGPL-3.0 要求公开修改后的源码。
