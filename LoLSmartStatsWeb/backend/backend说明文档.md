# backend说明文档

# **backend目录：**

```
backend
└── src
    └── main
        ├── java
        │   └── com.example.backend
        │       ├── BackendApplication.java
        │       │
        │       ├── config
        │       │   ├── AppConfig.java
        │       │   ├── CorsConfig.java
        │       │   ├── SecurityConfig.java
        │       │   ├── JwtAuthenticationFilter.java
        │       │   ├── WebMvcConfig.java
        │       │   └── WebClientConfig.java
        │       │
        │       ├── controller
        │       │   ├── AuthController.java
        │       │   ├── UserController.java
        │       │   ├── ChatController.java
        │       │   ├── DataController.java
        │       │   └── MetricsController.java
        │       │
        │       ├── service
        │       │   ├── auth
        │       │   │   ├── AuthService.java
        │       │   │   ├── TokenService.java
        │       │   │   └── UserService.java
        │       │   │
        │       │   ├── chat
        │       │   │   ├── ChatService.java
        │       │   │   ├── ChatHistoryService.java
        │       │   │   └── SseRelayService.java
        │       │   │
        │       │   ├── data
        │       │   │   ├── OptionsService.java
        │       │   │   ├── MatchService.java
        │       │   │   └── PlayerService.java
        │       │   │
        │       │   └── metrics
        │       │       ├── MetricsService.java
        │       │       ├── MetricQueryRouter.java
        │       │       └── strategy
        │       │           ├── PickBanMetricStrategy.java
        │       │           ├── TeamWinRateMetricStrategy.java
        │       │           └── GoldDiff15MetricStrategy.java
        │       │
        │       ├── client
        │       │   └── agent
        │       │       ├── AgentClient.java
        │       │       ├── AgentSseHandler.java
        │       │       └── AgentAuthInterceptor.java
        │       │
        │       ├── repository
        │       │   ├── UserRepository.java
        │       │   ├── RefreshTokenRepository.java
        │       │   ├── ChatSessionRepository.java
        │       │   ├── ChatMessageRepository.java
        │       │   └── MatchRepository.java
        │       │
        │       ├── entity
        │       │   ├── User.java
        │       │   ├── RefreshToken.java
        │       │   ├── ChatSession.java
        │       │   ├── ChatMessage.java
        │       │   └── Match.java
        │       │
        │       ├── dto
        │       │   ├── request
        │       │   │   ├── LoginRequest.java
        │       │   │   ├── RegisterRequest.java
        │       │   │   ├── RefreshTokenRequest.java
        │       │   │   ├── ChatStreamRequest.java
        │       │   │   ├── MetricsQueryRequest.java
        │       │   │   └── MatchSearchRequest.java
        │       │   │
        │       │   └── response
        │       │       ├── TokenResponse.java
        │       │       ├── UserMeResponse.java
        │       │       ├── ChatHistoryResponse.java
        │       │       └── MetricsQueryResponse.java
        │       │
        │       ├── vo
        │       │   ├── ApiResponse.java
        │       │   ├── ApiError.java
        │       │   └── SseEvent.java
        │       │
        │       ├── enums
        │       │   ├── RoleEnum.java
        │       │   ├── MetricType.java
        │       │   └── ChatMode.java
        │       │
        │       ├── exception
        │       │   ├── BizException.java
        │       │   └── GlobalExceptionHandler.java
        │       │
        │       └── util
        │           ├── JwtUtil.java
        │           ├── JsonUtil.java
        │           ├── TraceIdUtil.java
        │           └── PageUtil.java
        │
        └── resources
            ├── application.properties
            ├── mysql_dump_batched.sql
            └── logback-spring.xml   （可选）
```



# 数据库设计说明（MySQL）

## 1. 数据库概览

- **数据库名**：`lol_smart_stats`
- **数据库类型**：MySQL
- **主要用途**：
  - 英雄联盟职业比赛数据存储
  - 用户认证与会话管理
  - 为数据分析（Metrics）和 AI Agent 提供结构化数据支持

------

## 2. 表结构总览

| 表名                | 说明                              |
| ------------------- | --------------------------------- |
| `users`             | 系统用户表（登录 / 鉴权）         |
| `refresh_tokens`    | Refresh Token 管理                |
| `Matches`           | 比赛级别信息（BO 系列）           |
| `Games`             | 单局比赛（Game 1 / Game 2 …）     |
| `Teams`             | 战队信息                          |
| `Players`           | 选手信息                          |
| `player_game_stats` | 选手单局详细技术统计（完整版）    |
| `PlayerGameStats`   | 选手单局技术统计（简化/冗余版本） |

> ⚠️ 注意：
>  `player_game_stats` 与 `PlayerGameStats` **字段高度重复**，后续建议统一使用其中一个（见文末建议）。

------

## 3. 用户与鉴权相关表（Auth 模块）

### 3.1 `users` —— 用户表

**用途**：
 存储平台注册用户信息，用于 JWT 鉴权、Chat、个性化功能。

| 字段            | 类型          | 说明                           |
| --------------- | ------------- | ------------------------------ |
| `id`            | varchar(64)   | 用户唯一 ID（主键，推荐 UUID） |
| `email`         | varchar(255)  | 用户邮箱（唯一）               |
| `password_hash` | varchar(255)  | 密码哈希                       |
| `nickname`      | varchar(64)   | 昵称                           |
| `avatar`        | varchar(1024) | 头像 URL                       |
| `created_at`    | datetime(6)   | 创建时间                       |

**说明**：

- 不存明文密码
- `id` 与 refresh_tokens.user_id 关联

------

### 3.2 `refresh_tokens` —— Refresh Token 表

**用途**：
 支持 Access Token 过期后的安全刷新，支持登出 / Token 失效。

| 字段         | 类型         | 说明                  |
| ------------ | ------------ | --------------------- |
| `token`      | varchar(128) | Refresh Token（主键） |
| `user_id`    | varchar(64)  | 所属用户 ID           |
| `created_at` | datetime(6)  | 创建时间              |
| `expires_at` | datetime(6)  | 过期时间              |
| `revoked`    | bit(1)       | 是否已吊销            |

**说明**：

- 一个用户可有多个 refresh token（多设备登录）
- 登出时将 `revoked = 1`

------

## 4. 比赛结构相关表（核心数据模型）

### 4.1 `Matches` —— 比赛（系列赛）表

**用途**：
 表示一场完整比赛（如 BO3 / BO5），由多个 `Games` 组成。

| 字段              | 类型         | 说明                    |
| ----------------- | ------------ | ----------------------- |
| `id`              | int          | 比赛 ID（主键）         |
| `match_date`      | varchar(255) | 比赛日期                |
| `tournament_name` | varchar(255) | 赛事名称                |
| `stage`           | varchar(255) | 阶段（小组赛 / 淘汰赛） |
| `team1_id`        | int          | 战队 1                  |
| `team2_id`        | int          | 战队 2                  |
| `winner_id`       | int          | 获胜战队                |

------

### 4.2 `Games` —— 单局比赛表

**用途**：
 表示 Match 中的单局（Game 1 / Game 2 …）。

| 字段           | 类型 | 说明            |
| -------------- | ---- | --------------- |
| `id`           | int  | Game ID（主键） |
| `match_id`     | int  | 所属 Match      |
| `game_number`  | int  | 第几局          |
| `duration`     | int  | 游戏时长（秒）  |
| `blue_team_id` | int  | 蓝色方          |
| `red_team_id`  | int  | 红色方          |
| `winner_id`    | int  | 胜方战队        |

------

## 5. 战队与选手基础表

### 5.1 `Teams` —— 战队表

| 字段         | 类型         | 说明                       |
| ------------ | ------------ | -------------------------- |
| `id`         | int          | 战队 ID                    |
| `name`       | varchar(255) | 战队全名                   |
| `short_name` | varchar(255) | 简称                       |
| `region`     | varchar(255) | 赛区（LPL / LCK / LEC 等） |

------

### 5.2 `Players` —— 选手表

| 字段   | 类型         | 说明     |
| ------ | ------------ | -------- |
| `id`   | int          | 选手 ID  |
| `name` | varchar(255) | 选手名称 |

------

## 6. 选手比赛数据表（Metrics 核心）

### 6.1 `player_game_stats` —— 选手单局详细数据（推荐主表）

**用途**：
 用于数据分析、Metrics 查询、AI 分析的**核心事实表**。

| 分类 | 字段                                                         |
| ---- | ------------------------------------------------------------ |
| 关联 | `game_id`, `player_id`, `team_id`, `position`                |
| 英雄 | `champion_name`, `champion_name_en`                          |
| 战斗 | `kills`, `deaths`, `assists`, `kda`                          |
| 经济 | `gold_earned`, `minions_killed`                              |
| 输出 | `total_damage_dealt`, `damage_dealt_to_champions`, `damage_dealt_percentage` |
| 承伤 | `total_damage_taken`, `damage_taken_percentage`              |
| 参与 | `kill_participation`                                         |
| 其他 | `player_level`, `is_mvp`                                     |

------

### 6.2 `PlayerGameStats` —— 冗余/历史表（⚠️ 注意）

字段与 `player_game_stats` **几乎完全一致**。

👉 **建议在文档中说明：**

> 当前系统中存在两张结构相同的选手统计表，
>  后端与 Metrics 统一以 `player_game_stats` 作为主数据源，
>  `PlayerGameStats` 后续可合并或废弃。

------

## 7. 表关系总结（用于画 ER 图）

```
users 1 --- n refresh_tokens

Matches 1 --- n Games
Games   1 --- n player_game_stats

Teams   1 --- n Games
Teams   1 --- n player_game_stats

Players 1 --- n player_game_stats
```

------

## 8. 与后端模块的对应关系

| 后端模块     | 涉及表                         |
| ------------ | ------------------------------ |
| Auth         | users, refresh_tokens          |
| Data API     | Matches, Games, Teams, Players |
| Metrics      | player_game_stats              |
| Chat / Agent | 只读以上数据                   |

