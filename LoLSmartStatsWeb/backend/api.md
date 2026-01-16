# LoLSmartStatsWeb Backend API 文档

> Base URL：`http://localhost:8080`  \
> API Prefix：`/api/v1`

---

## 0. 全局约定

### 0.1 鉴权

- 除 `/api/v1/auth/*` 外，其它接口默认需要携带 JWT。
- 请求头：

```
Authorization: Bearer <accessToken>
```

### 0.2 Content-Type

- 普通 JSON：`application/json`
- SSE：`text/event-stream`
- 文件下载：`application/octet-stream`

### 0.3 统一响应结构（除 SSE/文件下载外）

成功：
```json
{
  "ok": true,
  "data": {},
  "traceId": "t_xxx"
}
```

失败：
```json
{
  "ok": false,
  "error": {
    "code": "INVALID_ARGUMENT",
    "message": "xxx",
    "details": {}
  },
  "traceId": "t_xxx"
}
```

### 0.4 常用错误码（建议前端统一处理）

| code | 含义 | 常见 HTTP |
|---|---|---|
| INVALID_ARGUMENT | 参数缺失/格式错误 | 400 |
| UNAUTHORIZED | 未登录/Token 无效或过期 | 401 |
| FORBIDDEN | 无权限 | 403 |
| NOT_FOUND | 资源不存在 | 404 |
| AI_SERVICE_ERROR | 上游 AI 服务错误 | 502/500 |
| INTERNAL_ERROR | 未知内部错误 | 500 |

### 0.5 时间格式约定（重要）

- `/api/v1/chat/sessions/list` 的 `from/to`：后端用 `Instant.parse()`，必须传 **ISO-8601**，例如：
  - `2026-01-15T00:00:00Z`
- 其它业务里如有 `dateRange.from/to`（例如 match 搜索），建议统一使用 `YYYY-MM-DD` 或 ISO-8601 字符串（数据库可能是 varchar，按字符串比较/容错解析）。

---

## 1. Auth 模块（4）

### 1.1 注册

- **POST** `/api/v1/auth/register`
- **描述**：使用邮箱+密码注册，返回用户信息与 token。

**Request**
```json
{
  "email": "test001@example.com",
  "password": "123456",
  "nickname": "test001"
}
```

**Response**
```json
{
  "ok": true,
  "data": {
    "user": {
      "userId": "u_xxx",
      "email": "test001@example.com",
      "nickname": "test001",
      "avatar": null
    },
    "tokens": {
      "accessToken": "jwt...",
      "refreshToken": "jwt..."
    }
  },
  "traceId": "t_xxx"
}
```

---

### 1.2 登录

- **POST** `/api/v1/auth/login`
- **描述**：邮箱+密码登录。

**Request**
```json
{
  "email": "test001@example.com",
  "password": "123456"
}
```

**Response**（同注册）
```json
{
  "ok": true,
  "data": {
    "user": {
      "userId": "u_xxx",
      "email": "test001@example.com",
      "nickname": "test001",
      "avatar": null
    },
    "tokens": {
      "accessToken": "jwt...",
      "refreshToken": "jwt..."
    }
  },
  "traceId": "t_xxx"
}
```

---

### 1.3 刷新 Token

- **POST** `/api/v1/auth/refresh`
- **描述**：使用 refreshToken 换取新的 token（access/refresh）。

**Request**
```json
{
  "refreshToken": "jwt..."
}
```

**Response**
```json
{
  "ok": true,
  "data": {
    "accessToken": "jwt...",
    "refreshToken": "jwt..."
  },
  "traceId": "t_xxx"
}
```

---

### 1.4 登出

- **POST** `/api/v1/auth/logout`
- **描述**：使 refreshToken 失效。

**Request**
```json
{
  "refreshToken": "jwt..."
}
```

**Response**
```json
{
  "ok": true,
  "data": { "ok": true },
  "traceId": "t_xxx"
}
```

---

## 2. User 模块（1）

### 2.1 获取当前登录用户

- **GET** `/api/v1/users/me`
- **鉴权**：需要 `Authorization: Bearer <accessToken>`
- **描述**：返回当前登录用户信息。

**Response**
```json
{
  "ok": true,
  "data": {
    "userId": "u_xxx",
    "email": "test001@example.com",
    "nickname": "test001",
    "avatar": null
  },
  "traceId": "t_xxx"
}
```

---

## 3. Chat 模块（9）

> 说明：Chat 模块对接 AI Agent，上游地址配置：`app.ai.base-url`；上游鉴权头：`X-AI-API-Key`（后端会自动带上）。

### 3.1 创建会话

- **POST** `/api/v1/chat/sessions`
- **鉴权**：需要
- **描述**：创建会话并落库，绑定当前用户。

**Request（可选；也可不传 body J）**
```json
{
  "title": "我的第一段分析"
}
```

**Response（后端返回 Map；字段以实际 service 为准，前端按常用字段接）**
```json
{
  "ok": true,
  "data": {
    "sessionId": "s_xxx",
    "title": "我的第一段分析",
    "createdAt": "2026-01-16T10:00:00Z"
  },
  "traceId": "t_xxx"
}
```

---

### 3.2 会话列表（分页+过滤）

- **POST** `/api/v1/chat/sessions/list`
- **鉴权**：需要
- **描述**：分页查询会话；支持 `status/from/to` 过滤；`from/to` 必须 ISO-8601。

**Request**
```json
{
  "page": 1,
  "pageSize": 20,
  "status": "active",
  "from": "2026-01-01T00:00:00Z",
  "to": "2026-12-31T00:00:00Z"
}
```

**Response（示例）**
```json
{
  "ok": true,
  "data": {
    "items": [
      {
        "sessionId": "s_xxx",
        "title": "我的第一段分析",
        "status": "active",
        "createdAt": "2026-01-16T10:00:00Z",
        "updatedAt": "2026-01-16T10:05:00Z"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  },
  "traceId": "t_xxx"
}
```

---

### 3.3 流式问答（SSE）

- **POST** `/api/v1/chat/stream`
- **鉴权**：需要
- **返回**：`text/event-stream`
- **描述**：SSE over POST，逐步返回模型输出。

**Request**
```json
{
  "sessionId": "s_xxx",
  "message": "请分析一下这场比赛的关键胜负点",
  "mode": "data+analysis",
  "context": {
    "matchId": 58999
  }
}
```

**SSE 事件（前端需按 eventName 区分）**
- `meta`：元信息
- `token`：增量文本
- `data`：结构化数据（表格/图表等）
- `done`：结束
- `error`：错误

示例：
```
event: meta
data: {"traceId":"t_xxx","sessionId":"s_xxx"}

event: token
data: {"delta":"本场关键在于..."}

event: done
data: {"ok":true}
```

---

### 3.4 非流式问答（一次性返回）

- **POST** `/api/v1/chat/query`
- **鉴权**：需要
- **描述**：一次性返回完整结果（便于调试/兜底）。

**Request**
```json
{
  "sessionId": "s_xxx",
  "message": "总结这场比赛的 MVP 与原因",
  "mode": "analysis",
  "context": {
    "matchId": 58999
  }
}
```

**Response（示例）**
```json
{
  "ok": true,
  "data": {
    "answer": "...",
    "data": [],
    "sessionId": "s_xxx"
  },
  "traceId": "t_xxx"
}
```

---

### 3.5 历史消息（POST）

- **POST** `/api/v1/chat/history`
- **鉴权**：需要
- **描述**：分页查询会话历史消息。

**Request**
```json
{
  "sessionId": "s_xxx",
  "page": 1,
  "pageSize": 50
}
```

**Response（示例）**
```json
{
  "ok": true,
  "data": {
    "items": [
      {
        "role": "user",
        "content": "请分析一下...",
        "mode": "analysis",
        "createdAt": "2026-01-16T10:00:10Z"
      },
      {
        "role": "assistant",
        "content": "好的，下面从...",
        "mode": "analysis",
        "createdAt": "2026-01-16T10:00:20Z",
        "reportFileId": "file_xxx"
      }
    ],
    "page": 1,
    "pageSize": 50,
    "total": 2
  },
  "traceId": "t_xxx"
}
```

---

### 3.6 历史消息（GET，调试用）

- **GET** `/api/v1/chat/history?sessionId=s_xxx&page=1&pageSize=20`
- **鉴权**：需要
- **描述**：GET 版本用于避免某些客户端 Content-Type 误配。

**Response**：同 POST `/chat/history`。

---

### 3.7 下载报告文件

- **GET** `/api/v1/chat/files/{fileId}`
- **Query**：`sessionId` 可选
- **鉴权**：可带（后端会把 `Authorization` 透传给上游 AI），同时后端会带 `X-AI-API-Key`。
- **返回**：文件流 `application/octet-stream`，并设置 `Content-Disposition: attachment`。

**示例**
```
GET /api/v1/chat/files/file_xxx?sessionId=s_xxx
```

**前端建议**
- 直接 `window.open(url)` 或用 `<a href>` 下载。
- 若接口返回 JSON 错误（如 NOT_FOUND），前端提示“文件不存在/上游生成失败”。

---

## 4. Data / Match / Player（6）

### 4.1 获取筛选项候选值

- **POST** `/api/v1/data/options`
- **描述**：返回筛选项候选值（赛事/阶段/队伍/选手/位置/英雄等）。

**Request**
```json
{
  "scope": {
    "tournamentName": "Worlds 2024",
    "stage": "Finals",
    "dateRange": { "from": "2026-01-01", "to": "2026-12-31" }
  },
  "need": ["tournaments", "stages", "teams", "players", "positions", "champions"]
}
```

**Response（示例）**
```json
{
  "ok": true,
  "data": {
    "tournaments": ["Worlds 2024"],
    "stages": ["Finals"],
    "teams": [{ "id": 7, "name": "T1", "shortName": "T1", "region": "LCK" }],
    "players": [{ "id": 90, "name": "Faker" }],
    "positions": ["TOP", "JUNGLE", "MID", "ADC", "SUPPORT"],
    "champions": [{ "name": "九尾妖狐", "nameEn": "the Nine-Tailed Fox" }]
  },
  "traceId": "t_xxx"
}
```

---

### 4.2 比赛列表搜索（分页）

- **POST** `/api/v1/matches/search`
- **鉴权**：需要
- **描述**：按赛事/阶段/队伍/日期等筛选比赛列表。

**Request**
```json
{
  "page": 1,
  "pageSize": 20,
  "filter": {
    "tournamentName": "Worlds 2024",
    "stage": "Finals",
    "teamIds": [7, 11],
    "dateRange": { "from": "2026-01-01", "to": "2026-12-31" }
  },
  "sort": { "field": "matchDate", "order": "desc" }
}
```

**Response（示例）**
```json
{
  "ok": true,
  "data": {
    "items": [
      {
        "matchId": 58999,
        "matchDate": "2026-01-15",
        "tournamentName": "Worlds 2024",
        "stage": "Finals",
        "team1": { "id": 7, "name": "T1", "shortName": "T1" },
        "team2": { "id": 11, "name": "BLG", "shortName": "BLG" },
        "winnerTeamId": 7,
        "gamesCount": 5
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 123
  },
  "traceId": "t_xxx"
}
```

---

### 4.3 比赛详情

- **POST** `/api/v1/matches/detail`
- **鉴权**：需要
- **描述**：返回比赛详情（对局 games + 每局参赛者 stats）。

**Request**
```json
{
  "matchId": 58999
}
```

**Response（示例，字段会随数据表变化）**
```json
{
  "ok": true,
  "data": {
    "match": {
      "id": 58999,
      "matchDate": "2026-01-15",
      "tournamentName": "Worlds 2024",
      "stage": "Finals",
      "team1Id": 7,
      "team2Id": 11,
      "winnerTeamId": 7
    },
    "teams": {
      "team1": { "id": 7, "name": "T1", "shortName": "T1", "region": "LCK" },
      "team2": { "id": 11, "name": "BLG", "shortName": "BLG", "region": "LPL" }
    },
    "games": [
      {
        "gameId": 18591,
        "gameNumber": 1,
        "duration": 2100,
        "blueTeamId": 7,
        "redTeamId": 11,
        "winnerTeamId": 7,
        "participants": [
          {
            "playerId": 90,
            "playerName": "Faker",
            "teamId": 7,
            "position": "MID",
            "championName": "九尾妖狐",
            "championNameEn": "the Nine-Tailed Fox",
            "stats": {
              "kills": 5,
              "deaths": 2,
              "assists": 8
            }
          }
        ]
      }
    ]
  },
  "traceId": "t_xxx"
}
```

---

### 4.4 选手搜索（分页）

- **POST** `/api/v1/players/search`
- **鉴权**：需要
- **描述**：按关键字搜索选手。

**Request**
```json
{
  "q": "Faker",
  "page": 1,
  "pageSize": 20
}
```

**Response（示例）**
```json
{
  "ok": true,
  "data": {
    "items": [{ "id": 90, "name": "Faker" }],
    "page": 1,
    "pageSize": 20,
    "total": 1
  },
  "traceId": "t_xxx"
}
```

---

## 5. 前端联调建议

1. **Token 与刷新**：建议前端实现统一拦截器：401 时调用 `/auth/refresh` 换新 token 后重试原请求。
2. **SSE**：`/chat/stream` 为 `POST + text/event-stream`，与浏览器原生 `EventSource(GET)` 不完全一致；建议前端用支持 POST SSE 的库，或用 fetch + ReadableStream 解析。
3. **会话列表时间格式**：`from/to` 必须 ISO-8601，否则后端直接返回 INVALID_ARGUMENT。
4. **文件下载**：直接访问 `/chat/files/{fileId}`，服务端透传上游文件流；若上游 404，返回 NOT_FOUND（JSON）。
