# 英雄联盟赛事AI服务（大模型端）API设计文档（以实际实现为准）

## 文档说明
本文档以当前 FastAPI 服务端实现为唯一依据（对应 src/server.py）。目标是让 SpringBoot 后端按本文档即可与 AI 端完成联调；未在代码中出现的接口/字段/事件已从文档移除。

## 一、全局约定

### 1.1 Base URL
AI 端服务基础路径：`/api/v1/ai`

### 1.2 鉴权与请求头（当前实现）
当前实现未启用鉴权：接口签名中存在 `X-AI-API-Key` / `Authorization` 头参数，但服务端不会校验，任何调用方均可访问。

建议统一携带（便于未来启用鉴权时无缝切换）：
```http
Content-Type: application/json
X-AI-API-Key: <可选，当前不校验>
Authorization: Bearer <可选，当前不校验>
```

### 1.3 错误返回（当前实现）
当前实现未封装统一错误 JSON 结构，也未实现错误码枚举；遵循 FastAPI 默认行为：

- 参数校验失败：HTTP 422，返回 Pydantic 校验错误结构
- 文件不存在：HTTP 404，返回 `{ "detail": "file not found" }`

## 二、已实现接口

### 2.1 健康检查

#### 2.1.1 接口信息
- 路径：`GET /api/v1/ai/health`
- 响应类型：`application/json`

#### 2.1.2 响应体
```json
{ "status": "ok" }
```

### 2.2 工具列表

#### 2.2.1 接口信息
- 路径：`GET /api/v1/ai/tools`
- 响应类型：`application/json`
- 说明：返回当前服务端注册的工具函数名列表（来自工具注册表）。

#### 2.2.2 响应体
```json
{
  "tools": ["rag_search", "db_query", "serper_search"]
}
```

补充：当前 Orchestrator 会按固定阶段调用工具（详见 2.3 SSE/2.4 非流式返回的 steps），典型为：
`serper_search`（Web 搜索）→ LLM 生成 NL2SQL（严格 JSON 格式）→ `db_query`（执行 SELECT）→ LLM 汇总输出。

### 2.3 SSE 流式对话（核心）

#### 2.3.1 接口信息
- 路径：`POST /api/v1/ai/chat/stream`
- 响应类型：`text/event-stream; charset=utf-8`
- 功能：执行一次 Agent 推理并以 SSE 事件流形式返回。服务端会先产出步骤（steps），再按固定分片大小输出文本增量（token）。

#### 2.3.2 请求体（JSON）
请求体使用 RunRequest（Pydantic 模型）。其中仅 `query` 必填，其它字段均为可选：

| 字段         | 类型            | 必选 | 说明 |
|--------------|-----------------|------|------|
| agent        | string          | 否   | 预留字段，默认 `base`（当前服务端不据此分流） |
| query        | string          | 是   | 用户输入问题 |
| mode         | string          | 否   | `simple` / `report`，默认 `simple` |
| sessionId    | string          | 否   | 会话 ID，仅透传回 `meta`/响应体（若提供） |
| traceId      | string          | 否   | 链路追踪 ID，仅透传回 `meta`/`done`（若提供） |
| context      | object          | 否   | 业务上下文对象，原样传给 Orchestrator |
| reportConfig | object          | 否   | 报告配置对象，原样传给 Orchestrator |

请求示例：
```json
{
  "sessionId": "s_abc123",
  "traceId": "t_001",
  "mode": "simple",
  "query": "亚索的背景故事是什么？",
  "context": { "lang": "zh-CN" }
}
```

#### 2.3.3 SSE 事件规范（当前实现）
服务端会依次输出以下事件（均满足 SSE 格式：`event: ...` + `data: ...` + 空行）。

注意：当前实现不会输出 `keepalive` / `error` / `report` 事件；`data` 事件仅用于输出步骤信息。

| 事件类型  | 说明 | `data` JSON 结构 |
|-----------|------|------------------|
| meta      | 开始元信息（首个事件） | `{ "traceId": string\|null, "sessionId": string\|null, "model": string, "mode": "simple"\|"report", "startedAt": string }` |
| data      | 步骤信息（每个 step 一条） | `{ "type": "step", "detail": <step对象> }` |
| token     | 文本增量（按固定字符分片） | `{ "delta": string }` |
| file_meta | 仅在 `mode=report` 时输出，表示报告文件已落盘 | `{ "fileId": string, "fileName": string, "fileType": "markdown", "size": number }` |
| done      | 结束事件 | `{ "ok": true, "traceId": string\|null }` |

SSE 示例（`mode=report`）：
```
event: meta
data: {"traceId":"t_001","sessionId":"s_abc123","model":"qwen-plus","mode":"report","startedAt":"2025-12-26T00:00:00Z"}

event: data
data: {"type":"step","detail": {"step":"search","tool":"serper_search","input":"...","output":"..."}}

event: data
data: {"type":"step","detail": {"step":"nl2sql","tool":"llm","input":"...","output": {"sql":"SELECT ..."}}}

event: data
data: {"type":"step","detail": {"step":"db_query","tool":"db_query","input":"SELECT ...","output":"{...}"}}

event: token
data: {"delta":"（报告正文第1段...）"}

event: file_meta
data: {"fileId":"report_1760000000.md","fileName":"report_1760000000.md","fileType":"markdown","size":12345}

event: done
data: {"ok":true,"traceId":"t_001"}
```

#### 2.3.4 分片规则（当前实现）
`token` 事件不是严格的 LLM token 流，而是服务端对最终 `answer` 进行字符串分片输出：

- 分片大小：每片最多 200 字符
- 分片节奏：每片间隔约 20ms

### 2.4 非流式对话（调试/回归用）

#### 2.4.1 接口信息
- 路径：`POST /api/v1/ai/chat/query`
- 响应类型：`application/json; charset=utf-8`
- 功能：一次性返回完整回答与步骤数组。

#### 2.4.2 请求体
与 2.3.2 相同（RunRequest）。

#### 2.4.3 响应体（当前实现）
```json
{
  "sessionId": "s_abc123",
  "traceId": "t_001",
  "mode": "report",
  "answer": "...",
  "durationMs": 0,
  "model": "qwen-plus",
  "steps": [
    {"tool": "rag_search", "input": "...", "output": "..."}
  ],
  "reportMeta": {
    "fileId": "report_1760000000.md",
    "fileName": "report_1760000000.md",
    "fileType": "markdown",
    "size": 12345
  }
}
```

说明：
- `durationMs` 当前恒为 0（服务端未统计耗时）
- `reportMeta` 仅在 `mode=report` 时存在

### 2.5 报告文件下载

#### 2.5.1 接口信息
- 路径：`GET /api/v1/ai/files/{fileId}`
- 响应类型：`application/octet-stream`
- 功能：下载由 `mode=report` 生成并落盘的 markdown 文件。

#### 2.5.2 行为说明
- 文件存储目录：`data/reports/`
- `fileId` 即文件名（例如 `report_1760000000.md`）
- 文件不存在：HTTP 404，响应 `{ "detail": "file not found" }`

### 2.6 内置测试页

#### 2.6.1 接口信息
- 路径：`GET /`
- 响应类型：`text/html`
- 功能：提供一个简单的前端页面用于手工测试 `stream` 与 `query` 两个接口。

## 三、联调建议（以当前实现为准）

### 3.1 curl 调试示例

SSE：
```bash
curl -N -X POST http://localhost:8000/api/v1/ai/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s_abc123","traceId":"t_001","mode":"simple","query":"2024世界赛决赛谁赢了？"}'
```

非流式：
```bash
curl -X POST http://localhost:8000/api/v1/ai/chat/query \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s_abc123","traceId":"t_001","mode":"report","query":"生成一份赛事经济差分析报告"}'
```

#### 2.3.5 数据库 Schema（当前实现依赖）
服务端的 NL2SQL 与 DB 查询阶段依赖以下结构化表（用于赛事/对局/选手数据统计）：

- `Teams(id, name, short_name, region)`
- `Players(id, name)`
- `Matches(id, match_date, tournament_name, stage, team1_id, team2_id, winner_id)`
- `Games(id, match_id, game_number, duration, blue_team_id, red_team_id, winner_id)`
- `PlayerGameStats(id, game_id, player_id, team_id, position, champion_name, champion_name_en, player_level, kills, deaths, assists, kda, kill_participation, total_damage_dealt, damage_dealt_to_champions, damage_dealt_percentage, total_damage_taken, damage_taken_percentage, gold_earned, minions_killed, is_mvp)`

常用外键/关联：
- `Matches.team1_id/team2_id/winner_id -> Teams.id`
- `Games.match_id -> Matches.id`；`Games.blue_team_id/red_team_id/winner_id -> Teams.id`
- `PlayerGameStats.game_id -> Games.id`；`player_id -> Players.id`；`team_id -> Teams.id`