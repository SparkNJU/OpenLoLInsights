# backend说明文档

> 项目：LoLSmartStatsWeb / backend（Spring Boot + Spring Security + JPA）
>
> - 服务端口：`8080`
> - API 前缀：`/api/v1`
> - 数据库：MySQL（schema：`lol_smart_stats`）

---

## 1. 目录结构

```
backend
├── pom.xml
├── api.md                      # 接口对接文档
├── backend说明文档.md          # 本文档
├── src
│   └── main
│       ├── java
│       │   └── com.example.backend
│       │       ├── BackendApplication.java
│       │       ├── config
│       │       │   ├── AppConfig.java
│       │       │   ├── CorsConfig.java
│       │       │   ├── SecurityConfig.java
│       │       │   └── TraceIdFilter.java
│       │       ├── controller
│       │       │   ├── AuthController.java
│       │       │   ├── UserController.java
│       │       │   ├── ChatController.java
│       │       │   ├── ChatFileController.java
│       │       │   ├── DataController.java
│       │       │   ├── MatchController.java
│       │       │   └── PlayerController.java
│       │       ├── dto
│       │       │   ├── request
│       │       │   └── response
│       │       ├── entity
│       │       ├── enums
│       │       ├── exception
│       │       ├── repository
│       │       ├── service
│       │       │   ├── auth
│       │       │   ├── chat
│       │       │   └── data
│       │       ├── util
│       │       └── vo
│       └── resources
│           ├── application.properties
│           └── mysql_dump_batched.sql
└── target
    └── backend-0.0.1-SNAPSHOT.jar
```

---

## 2. 系统模块说明

### 2.1 Auth & User（登录/鉴权）

- JWT：AccessToken + RefreshToken
- 主要接口：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/refresh`
  - `POST /api/v1/auth/logout`
  - `GET  /api/v1/users/me`

### 2.2 Chat（AI 对话 + 会话/历史落库）

- 主要接口：
  - `POST /api/v1/chat/sessions`：创建会话
  - `POST /api/v1/chat/sessions/list`：会话列表
  - `POST /api/v1/chat/stream`：SSE 流式输出
  - `POST /api/v1/chat/query`：非流式
  - `POST /api/v1/chat/history`：历史（POST）
  - `GET  /api/v1/chat/history`：历史（GET，调试）
  - `GET  /api/v1/chat/files/{fileId}`：下载报告文件（转发 AI 生成文件）

> 说明：`/chat/stream` 是 **POST + text/event-stream**，前端需要用 fetch/stream 方式解析（而不是原生 EventSource 的 GET）。

### 2.3 Data（筛选项候选值）

- `POST /api/v1/data/options`

### 2.4 Match / Player（比赛与选手查询）

- `POST /api/v1/matches/search`
- `POST /api/v1/matches/detail`
- `POST /api/v1/players/search`

---

## 3. 数据库设计说明（MySQL）

### 3.1 数据库概览

- **数据库名**：`lol_smart_stats`
- **表总数**：10

```
chat_messages
chat_sessions
Games
Matches
player_game_stats
PlayerGameStats
Players
refresh_tokens
Teams
users
```

### 3.2 表结构总览与用途

| 表名 | 用途 | 模块 |
|---|---|---|
| `users` | 用户信息（登录/鉴权） | Auth/User |
| `refresh_tokens` | RefreshToken 管理（刷新、登出） | Auth |
| `chat_sessions` | 对话会话（session） | Chat |
| `chat_messages` | 对话消息（history） | Chat |
| `Matches` | 系列赛（BO3/BO5）级别信息 | Data/Match |
| `Games` | 单局对局（Game1/2/3...） | Match |
| `Teams` | 战队信息 | Data/Match |
| `Players` | 选手信息 | Player/Match |
| `PlayerGameStats` | **选手单局统计（当前实际有数据的主表）** | Match/Data |
| `player_game_stats` | 与上表结构重复，当前为空 | - |

> ⚠️ 重要现状：
> - `PlayerGameStats`（首字母大写）表中有数据；
> - `player_game_stats`（全小写）表当前为空。
>
> 因此：后端与接口文档/查询逻辑应以 **`PlayerGameStats`** 作为主数据源。

---

## 4. 核心数据表字段

### 4.1 `Matches`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int (PK) | 比赛 ID |
| `match_date` | varchar(255) | 比赛日期（字符串） |
| `tournament_name` | varchar(255) | 赛事名称 |
| `stage` | varchar(255) | 阶段 |
| `team1_id` | int | 战队 1 |
| `team2_id` | int | 战队 2 |
| `winner_id` | int | 获胜战队 |

> 备注：`match_date` 为 varchar，若做范围筛选/排序建议统一格式（如 `YYYY-MM-DD` 或 ISO-8601）。

### 4.2 `Games`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int (PK) | Game ID |
| `match_id` | int | 所属 Match |
| `game_number` | int | 第几局 |
| `duration` | int | 时长（秒） |
| `blue_team_id` | int | 蓝色方战队 |
| `red_team_id` | int | 红色方战队 |
| `winner_id` | int | 胜方战队 |

### 4.3 `Teams`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int (PK) | 战队 ID |
| `name` | varchar(255) | 战队全名 |
| `short_name` | varchar(255) | 简称 |
| `region` | varchar(255) | 赛区 |

### 4.4 `Players`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int (PK) | 选手 ID |
| `name` | varchar(255) | 选手名 |

### 4.5 `PlayerGameStats`（当前主数据表）

| 字段 | 类型 |
|---|---|
| `id` | int (PK) |
| `game_id` | int |
| `player_id` | int |
| `team_id` | int |
| `position` | varchar(255) |
| `champion_name` | varchar(255) |
| `champion_name_en` | varchar(255) |
| `player_level` | int |
| `kills` | int |
| `deaths` | int |
| `assists` | int |
| `kda` | double |
| `kill_participation` | double |
| `total_damage_dealt` | int |
| `damage_dealt_to_champions` | int |
| `damage_dealt_percentage` | double |
| `total_damage_taken` | int |
| `damage_taken_percentage` | double |
| `gold_earned` | int |
| `minions_killed` | int |
| `is_mvp` | varchar(255) |

---

## 5. 表关系总结（用于 ER 图）

```
users 1 --- n refresh_tokens

Matches 1 --- n Games
Games   1 --- n PlayerGameStats

Teams   1 --- n Games
Teams   1 --- n PlayerGameStats

Players 1 --- n PlayerGameStats

chat_sessions 1 --- n chat_messages
users        1 --- n chat_sessions
```

---

## 6. 与后端模块 / 接口的对应关系

| 模块 | 涉及表 | 相关接口 |
|---|---|---|
| Auth | `users`, `refresh_tokens` | `/auth/register` `/auth/login` `/auth/refresh` `/auth/logout` |
| User | `users` | `/users/me` |
| Chat | `chat_sessions`, `chat_messages` | `/chat/*`（含 stream/query/history/sessions/files） |
| Match/Data | `Matches`, `Games`, `Teams`, `Players`, `PlayerGameStats` | `/data/options` `/matches/search` `/matches/detail` `/players/search` |

---

## 7. 备注与建议

1. **双表问题**：`PlayerGameStats` 与 `player_game_stats` 结构重复且后者为空；当前建议统一以 **`PlayerGameStats`** 为主数据源（接口/查询/AI 分析均一致）。
2. **日期字段类型**：`Matches.match_date` 为 varchar；如果后续需要稳定排序与范围过滤，建议统一数据格式或改为 date/datetime。
