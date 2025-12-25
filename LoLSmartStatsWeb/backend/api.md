## 0. 全局约定

### Base URL

```
/api/v1
```

### 鉴权

- 使用 **JWT Access Token（短期） + Refresh Token（长期）**
- 需要鉴权的接口：除 `/auth/*` 外的所有业务接口（如 `/users/me`、`/chat/*`、`/data/*`、`/metrics/*`）
- 请求头：

```
Authorization: Bearer <accessToken>
```

- Access Token 过期：前端使用 Refresh Token 调用 `/auth/refresh` 刷新后重试原请求。

### Content-Type

- 普通 JSON 接口：`application/json`
- SSE 流式接口：`text/event-stream; charset=utf-8`

### 统一成功响应结构（JSON 接口）

> 除 SSE 流式接口外，后端建议统一包装为 `ApiResponse<T>`。

```
{
  "ok": true,
  "data": {},
  "traceId": "t_xxx"
}
```

字段说明：
- `ok`：是否成功
- `data`：业务数据
- `traceId`：链路追踪 ID（便于定位日志）

### 统一错误结构（除流式接口外）

> 对应 `ApiError`，由全局异常处理器统一返回。

```
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

### 错误码约定（建议）

| code | 说明 | 常见 HTTP 状态码 |
|---|---|---|
| `INVALID_ARGUMENT` | 参数校验失败/格式错误 | 400 |
| `UNAUTHORIZED` | 未登录/Token 无效 | 401 |
| `FORBIDDEN` | 无权限 | 403 |
| `NOT_FOUND` | 资源不存在 | 404 |
| `CONFLICT` | 冲突（如邮箱已注册） | 409 |
| `RATE_LIMITED` | 频率限制 | 429 |
| `INTERNAL_ERROR` | 服务器内部错误 | 500 |

### 分页约定

**Request**

```
{ "page": 1, "pageSize": 20 }
```

- `page`：从 1 开始
- `pageSize`：建议 10~100

**Response**

```
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 0
}
```

------

## 1. 用户注册 / 登录 / 用户信息（Auth）

> 模块涉及表：`users`、`refresh_tokens`。

### 1.1 注册

**POST** `/api/v1/auth/register`

**Request**

```
{
  "email": "a@b.com",
  "password": "******",
  "nickname": "xxx"
}
```

字段说明：
- `email`：唯一邮箱
- `password`：明文仅用于注册入参；服务端存储 `password_hash`
- `nickname`：昵称

**Response（200）**

```
{
  "ok": true,
  "data": {
    "user": {
      "userId": "u_123",
      "email": "a@b.com",
      "nickname": "xxx"
    },
    "tokens": {
      "accessToken": "jwt...",
      "refreshToken": "jwt..."
    }
  },
  "traceId": "t_xxx"
}
```

可能错误：
- 409 `CONFLICT`：邮箱已注册
- 400 `INVALID_ARGUMENT`：参数不合法

------

### 1.2 登录

**POST** `/api/v1/auth/login`

**Request**

```
{
  "email": "a@b.com",
  "password": "******"
}
```

**Response（200）**

> 与注册一致（`user + tokens`）。

```
{
  "ok": true,
  "data": {
    "user": {
      "userId": "u_123",
      "email": "a@b.com",
      "nickname": "xxx"
    },
    "tokens": {
      "accessToken": "jwt...",
      "refreshToken": "jwt..."
    }
  },
  "traceId": "t_xxx"
}
```

可能错误：
- 401 `UNAUTHORIZED`：邮箱或密码错误

------

### 1.3 刷新 Token

**POST** `/api/v1/auth/refresh`

**Request**

```
{ "refreshToken": "jwt..." }
```

**Response（200）**

```
{
  "ok": true,
  "data": {
    "accessToken": "jwt...",
    "refreshToken": "jwt..."
  },
  "traceId": "t_xxx"
}
```

说明：
- 服务端应校验 refresh token 是否存在、是否过期、是否 revoked
- 可采用“旋转刷新”（refresh 时下发新的 refresh token，并吊销旧 token）

可能错误：
- 401 `UNAUTHORIZED`：refresh token 无效/过期/已吊销

------

### 1.4 登出（使 Refresh Token 失效）

**POST** `/api/v1/auth/logout`

**Request**

```
{ "refreshToken": "jwt..." }
```

**Response（200）**

```
{
  "ok": true,
  "data": { "ok": true },
  "traceId": "t_xxx"
}
```

说明：
- 服务端将 refresh token 标记 `revoked = 1`

------

### 1.5 获取当前用户信息

**GET** `/api/v1/users/me`（需要 Authorization）

**Response（200）**

```
{
  "ok": true,
  "data": {
    "userId": "u_123",
    "email": "a@b.com",
    "nickname": "xxx",
    "avatar": "https://..."
  },
  "traceId": "t_xxx"
}
```

可能错误：
- 401 `UNAUTHORIZED`：未携带或携带了无效 access token

------

## 2. 大模型问答（Chat）

### 协议选择

- 使用 **SSE over POST**
- Content-Type：

```
text/event-stream; charset=utf-8
```

------

### 2.1 创建会话（可选）

**POST** `/api/v1/chat/sessions`

```
{ "title": "可选：前端传或后端自动生成" }
{
  "sessionId": "s_abc123",
  "title": "xxx",
  "createdAt": "..."
}
```

------

### 2.2 流式问答（核心）

**POST** `/api/v1/chat/stream`

```
{
  "sessionId": "s_abc123",
  "message": "2024 世界赛决赛谁赢了？关键原因是什么？",
  "mode": "data+analysis",
  "context": {
    "tournamentId": "2024-worlds",
    "dateRange": { "from": "2024-10-01", "to": "2024-12-01" },
    "teamIds": ["T1", "BLG"],
    "patch": "14.19"
  }
}
```

### SSE 事件类型

- `meta`：请求元信息
- `token`：增量文本
- `data`：结构化数据（图表 / 表格）
- `done`：结束
- `error`：错误

示例：

```
event: meta
data: {"traceId":"t_001","sessionId":"s_abc123","model":"deepseek"}

event: token
data: {"delta":"决赛由 T1 以 3:2 获胜，"}

event: data
data: {"type":"chart","chartId":"gold_diff_15", ...}

event: done
data: {"ok":true}
```

------

### 2.3 非流式问答（调试用）

**POST** `/api/v1/chat/query`

```
{
  "sessionId": "s_abc123",
  "answer": "完整回答文本...",
  "data": [],
  "traceId": "t_001"
}
```

------

### 2.4 历史消息

**POST** `/api/v1/chat/history`

```
{ "sessionId": "s_abc123", "page": 1, "pageSize": 50 }
```

------

## 3. 数据查询与展示（Data APIs）

### 3.1 筛选项候选值

**POST** `/api/v1/data/options`

```
{
  "scope": { "tournamentId": "2024-worlds" },
  "need": ["tournaments","teams","players","patches","stages"]
}
```

------

### 3.2 比赛列表（分页）

**POST** `/api/v1/matches/search`

------

### 3.3 比赛详情

**POST** `/api/v1/matches/detail`

------

### 3.4 选手搜索

**POST** `/api/v1/players/search`

------

## 4. 统计 / 图表接口（Metrics）

### 4.1 统一指标接口

**POST** `/api/v1/metrics/query`

```
{
  "metric": "pickban",
  "groupBy": ["champion"],
  "filter": { "tournamentId": "2024-worlds" },
  "limit": 50
}
```

------

# 二、【重要】我认为你这份 API 里“不合理 / 高风险”的地方（逐条说明）

下面是**我不会帮你偷偷改，但你作为后端负责人必须知道的点**。

------

## ⚠️ 问题 1：`player_game_stats` vs `PlayerGameStats` 双表问题（严重）

**问题**

- API 文档默认“指标数据”是单一来源
- 但数据库里有 **两张结构重复的表**
- API 层完全没有说明用哪一张

**风险**

- 后端 / Agent / SQL 写的人会用不同表
- 同一个指标结果可能不一致

**建议（不修改 API，只补文档）**

> 在 API 文档补一句：
>  “所有 Metrics 接口统一以 `player_game_stats` 为主数据源。”

------

## ⚠️ 问题 2：`match_date` 是 varchar，但 API 当 date 用

**问题**

-  API 里大量使用：

```
"dateRange": { "from": "...", "to": "..." }
```

- 但数据库是 `varchar(255)`

**风险**

- SQL 层需要大量字符串比较
- 排序 / 区间过滤容易出 bug

**建议**

- 文档里 **明确约定 date 格式**
- 或注明：后端会在查询层做 date parsing

------

## ⚠️ 问题 3：Chat Stream 没说明“断线/重连语义”

**问题**

- SSE 是长连接
- 文档未说明：
  - 前端断线怎么办？
  - 是否支持 resume？

**风险**

- 前端和后端各自猜实现
- 实际联调会吵架 😅

**建议**

- 在 Chat Stream 说明中补一句：

> “当前版本不支持断点续传，断线需重新提问。”

------

## ⚠️ 问题 4：`context` 是强 schema，但又是自由结构

**问题**

```
"context": {
  "tournamentId": "...",
  "teamIds": [],
  "patch": "14.19"
}
```

**风险**

- 前端、后端、Agent 三方对字段理解不一致
- Agent 很可能拿到 undefined

**建议**

- 文档中明确：

> context 是**弱约束结构，字段可选，Agent 需做容错**

------

## ⚠️ 问题 5：Metrics 返回结构过于自由（长期风险）

**问题**

```
"series": [
  { "name": "pickRate", "points": [...] }
]
```

**风险**

- 前端画图组件需要“约定式解析”
- 不同 metric 返回形态不一致会导致前端 if-else 地狱

**建议**

- 不改接口
- 但文档中给出 **推荐 series schema 规范**