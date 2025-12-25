# 英雄联盟赛事AI服务（大模型端）API设计文档
## 文档说明
本文档定义SpringBoot后端（以下简称“后端”）与Python大模型Agent端（以下简称“AI端”）的通信接口规范，核心适配“简单问答/深度调查报告”两种模式，覆盖SSE流式交互、文件传输、数据库查询结果接入等核心能力，与后端API设计保持协议对齐、可扩展的原则。

## 一、全局约定
### 1.1 Base URL
AI端服务基础路径：`/api/v1/ai`

### 1.2 鉴权规则
- 后端调用AI端接口时，需在请求头携带双鉴权字段（兼顾安全与易用性）：
  ```http
  X-AI-API-Key: <固定API密钥>  # AI端预配置，用于基础鉴权
  Authorization: Bearer <后端JWT的accessToken>  # 复用用户鉴权，便于AI端关联用户上下文
  ```
- API密钥配置：AI端启动时通过环境变量`AI_API_KEY`配置，后端需与AI端保持一致。

### 1.3 统一错误结构（非流式接口）
- 遵循HTTP状态码 + JSON错误体，与后端错误结构对齐：
  ```json
  {
    "error": {
      "code": "AI_SERVICE_ERROR", // 错误码枚举见1.5
      "message": "大模型调用超时，请重试",
      "details": {
        "traceId": "t_001", // 链路追踪ID，与流式接口一致
        "retryable": true // 是否可重试
      }
    }
  }
  ```

### 1.4 分页约定（仅辅助接口）
- 与后端完全对齐，请求/响应结构如下：
  请求：`{ "page": 1, "pageSize": 20 }`
  响应：`{ "items": [], "page": 1, "pageSize": 20, "total": 0 }`

### 1.5 错误码枚举
| 错误码                | 说明                     | 可重试 |
|-----------------------|--------------------------|--------|
| INVALID_ARGUMENT      | 请求参数格式/值非法      | 否     |
| AI_MODEL_TIMEOUT      | 大模型接口调用超时       | 是     |
| DB_RESULT_PARSE_ERROR | 数据库查询结果解析失败   | 否     |
| FILE_STORAGE_ERROR    | 文件上传/下载失败        | 是     |
| SESSION_NOT_FOUND     | 会话ID不存在             | 否     |
| RATE_LIMIT_EXCEEDED   | 接口调用频率超限         | 是     |

## 二、核心接口设计
### 2.1 SSE流式交互接口（核心）
#### 2.1.1 接口信息
- 路径：`POST /api/v1/ai/chat/stream`
- 响应类型：`text/event-stream; charset=utf-8`
- 功能：接收后端传递的用户查询、数据库预查询结果、会话上下文，按“简单问答/深度报告”模式返回流式响应，支持文本增量、结构化数据、报告片段、图表数据等输出。

#### 2.1.2 请求体（JSON）
| 字段         | 类型   | 必选 | 说明                                                                 |
|--------------|--------|------|----------------------------------------------------------------------|
| sessionId    | string | 是   | 会话ID，与后端`/api/v1/chat/stream`的sessionId一致                  |
| traceId      | string | 是   | 链路追踪ID，后端生成并透传，用于全链路问题排查                       |
| message      | string | 是   | 用户原始查询语句（如“2024世界赛决赛T1 vs BLG的经济差分析”）          |
| mode         | string | 是   | 交互模式：<br/>- `simple`：简单问答（快速返回核心结论+基础数据）<br/>- `report`：深度调查报告（分章节生成+文件导出） |
| context      | object | 是   | 后端透传的筛选上下文，与后端`/api/v1/chat/stream`的context一致       |
| dbQueryResult| array  | 否   | 后端预执行的数据库查询结果（AI端可直接复用，避免重复查询）<br/>格式：`[{ "sql": "SELECT ...", "data": [], "columns": [] }]` |
| reportConfig | object | 否   | 深度报告专属配置（mode=report时必传）：<br/>`{ "format": "pdf/excel/markdown", "sections": ["summary", "data_analysis", "chart", "conclusion"] }` |

请求体示例：
```json
{
  "sessionId": "s_abc123",
  "traceId": "t_001",
  "message": "2024世界赛决赛T1 vs BLG的经济差分析，生成深度报告",
  "mode": "report",
  "context": {
    "tournamentId": "2024-worlds",
    "dateRange": { "from": "2024-10-01", "to": "2024-12-01" },
    "teamIds": ["T1", "BLG"]
  },
  "dbQueryResult": [
    {
      "sql": "SELECT game_no, gold_diff_15 FROM match_stats WHERE match_id=12345",
      "data": [[1, 1200], [2, -300], [3, 800], [4, -500], [5, 1500]],
      "columns": ["game_no", "gold_diff_15"]
    }
  ],
  "reportConfig": {
    "format": "pdf",
    "sections": ["summary", "data_analysis", "chart", "conclusion"]
  }
}
```

#### 2.1.3 SSE响应事件规范
AI端输出的SSE事件与后端前端交互的事件对齐，新增“报告专属事件”，所有`data`字段均为JSON字符串：

| 事件类型   | 说明                                                                 | 数据格式示例                                                                 |
|------------|----------------------------------------------------------------------|------------------------------------------------------------------------------|
| meta       | 本次请求元信息（首次输出）                                           | `{"traceId":"t_001","sessionId":"s_abc123","model":"deepseek","mode":"report","startedAt":"2024-11-05T10:00:00Z"}` |
| token      | 文本增量输出（simple/report模式通用）                                | `{"delta":"2024世界赛决赛T1以3:2击败BLG，前15分钟经济差是关键胜负手："}`     |
| data       | 结构化数据（图表/匹配ID/统计数据，simple/report模式通用）             | `{"type":"chart","chartId":"gold_diff_15","title":"前15分钟经济差分布","series":[{"name":"T1","points":[{"x":"G1","y":1200},{"x":"G2","y":-300}]}]}` |
| report     | 深度报告专属片段（mode=report时输出）                                | `{"section":"summary","content":"### 2024世界赛决赛经济差分析总结\nT1在决胜局前15分钟建立1500经济优势，最终拿下冠军。","progress":20}` |
| file_meta  | 深度报告文件元信息（报告生成完成后输出）                             | `{"fileId":"f_789","fileName":"2024世界赛决赛经济差分析报告.pdf","fileType":"pdf","size":102400}` |
| done       | 流式响应结束（包含最终状态）                                         | `{"ok":true,"sessionId":"s_abc123","traceId":"t_001","durationMs":5000}`      |
| error      | 流式过程中发生错误（中断当前响应）                                   | `{"code":"AI_MODEL_TIMEOUT","message":"大模型调用超时","traceId":"t_001","retryable":true}` |

SSE响应示例：
```
event: meta
data: {"traceId":"t_001","sessionId":"s_abc123","model":"deepseek","mode":"report","startedAt":"2024-11-05T10:00:00Z"}

event: token
data: {"delta":"2024世界赛决赛T1 vs BLG的经济差分析如下："}

event: data
data: {"type":"chart","chartId":"gold_diff_15","title":"前15分钟经济差分布","series":[{"name":"T1","points":[{"x":"G1","y":1200},{"x":"G2","y":-300},{"x":"G3","y":800},{"x":"G4","y":-500},{"x":"G5","y":1500}]}]}

event: report
data: {"section":"summary","content":"### 核心结论\nT1在决胜局（G5）前15分钟通过小龙团建立1500经济优势，成为夺冠关键","progress":30}

event: report
data: {"section":"data_analysis","content":"### 分场次经济差分析\nG1：T1领先1200经济，依靠卡牌带线扩大优势；\nG2：BLG领先300经济，通过下路压制翻盘；","progress":60}

event: file_meta
data: {"fileId":"f_789","fileName":"2024世界赛决赛经济差分析报告.pdf","fileType":"pdf","size":102400}

event: done
data: {"ok":true,"sessionId":"s_abc123","traceId":"t_001","durationMs":8000}
```

#### 2.1.4 实现要点（AI端）
- HTTP 层：推荐使用 FastAPI + `sse-starlette`（EventSourceResponse）或直接使用 `StreamingResponse` 构造 SSE。务必设置响应头：`Content-Type: text/event-stream; charset=utf-8`, `Cache-Control: no-cache`, `Connection: keep-alive`。
- 长连接保持：定期发送 `event: keepalive`（例如每 15-30s）以避免中间代理（如 nginx/ALB）关闭空闲连接；收到 `request.is_disconnected()` 后及时释放资源。
- 模型调用与阻塞：LLM 调用通常是阻塞/耗时的，使用 `starlette.concurrency.run_in_threadpool` 或 `asyncio.to_thread` 将阻塞工作移出主事件循环；或者在后台任务里逐步产生事件并写入到 SSE 流。
- 分片与速率控制：按 token/chunk 输出（例如 200-500 字/片），避免一次性发送过大数据，配合 `await asyncio.sleep()` 控制节奏以减轻后端/前端压力。
- 恢复与断线重连：支持 `Last-Event-ID`（客户端在重连时会发送），并在 `meta` 或 `token` 中包含可恢复的事件ID（如 `eventId` 字段）。若无法恢复，需告知客户端重新开启一个新 session。
- 鉴权与安全：在请求头校验 `X-AI-API-Key` 与 `Authorization`（Bearer token），并在流初始 `meta` 事件中回传 `traceId`/`sessionId` 以便链路追踪与审计。
- 文件产出策略：在流末或 `file_meta` 事件中返回 `fileId` 和签名下载 URL（如 S3 presigned URL），避免通过 SSE 发送二进制文件。
- 资源回收：在发生异常/断连/`done` 后，立刻释放模型会话、临时文件、数据库游标等资源，避免内存泄漏。

示例（FastAPI + sse_starlette，简化版）：

```python
from fastapi import FastAPI, Request, Header, HTTPException
from sse_starlette.sse import EventSourceResponse
import json, asyncio, os
from starlette.concurrency import run_in_threadpool

app = FastAPI()

async def event_generator(request: Request, payload: dict):
    traceId = payload.get("traceId")
    # meta
    yield f"event: meta\ndata: {json.dumps({'traceId': traceId,'sessionId': payload.get('sessionId')})}\n\n"

    # 模拟流式 token（实际应从 LLM token 流中获取）
    for token in ["这是"," 一个"," 流式"," 输出"]:
        if await request.is_disconnected():
            break
        yield f"event: token\ndata: {json.dumps({'delta': token})}\n\n"
        await asyncio.sleep(0.05)

    # done
    yield f"event: done\ndata: {json.dumps({'ok': True,'traceId': traceId})}\n\n"

@app.post("/api/v1/ai/chat/stream")
async def stream(request: Request, payload: dict, x_ai_api_key: str = Header(None)):
    # 简单鉴权
    if x_ai_api_key != os.getenv('AI_API_KEY'):
        raise HTTPException(status_code=401, detail="Unauthorized")

    return EventSourceResponse(event_generator(request, payload))
```

#### 2.1.5 Spring Boot（后端）订阅 SSE 的推荐方式
- WebFlux（推荐）示例：使用 `WebClient` 订阅并消费 `ServerSentEvent`，可直接在反应式链中处理事件并转发给前端或落地日志：

```java
WebClient webClient = WebClient.create();
Flux<ServerSentEvent<String>> flux = webClient.post()
  .uri("http://agent:8000/api/v1/ai/chat/stream")
  .header("X-AI-API-Key", aiKey)
  .contentType(MediaType.APPLICATION_JSON)
  .bodyValue(payload)
  .retrieve()
  .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

flux.subscribe(sse -> {
  String event = sse.event();
  String data = sse.data();
  // 解析 JSON 并按 event 处理
});
```

- MVC（非反应式）示例：后端可使用 `SseEmitter` 将来自 Agent 的流转发给前端，或在 Controller 中异步读取 `WebClient` 返回流并通过 `SseEmitter` 转发。

#### 2.1.6 运行时与运维要点
- 超时与限流：为 SSE 连接设置合理超时时间与并发连接上限（例如每实例 200-500 个连接），并在网关层做速率限制。
- 健康检查：`/api/v1/ai/health` 返回 `ok` 以及当前活跃连接数、队列长度（可选）。
- 日志与链路追踪：在每个事件中携带 `traceId`，并把模型调用耗时、token 数量、error 码上报到监控系统（Prometheus/Grafana）。
- 自动重连策略：客户端以指数退避重连，若服务端支持断点续传，则使用 `Last-Event-ID` 恢复进度；否则重建新的会话（并带上原始 `traceId` 用于关联）。

### 2.2 非流式交互接口（调试/回归用）
#### 2.2.1 接口信息
- 路径：`POST /api/v1/ai/chat/query`
- 响应类型：`application/json; charset=utf-8`
- 功能：非流式返回完整回答/报告元信息，用于后端调试、自动化测试、离线回归。

#### 2.2.2 请求体
与2.1.2的SSE流式接口请求体完全一致。

#### 2.2.3 响应体
```json
{
  "sessionId": "s_abc123",
  "traceId": "t_001",
  "mode": "report",
  "answer": "2024世界赛决赛T1以3:2击败BLG，前15分钟经济差是关键胜负手...（完整文本）",
  "data": [
    {
      "type": "chart",
      "chartId": "gold_diff_15",
      "title": "前15分钟经济差分布",
      "series": [{"name":"T1","points":[{"x":"G1","y":1200}]}]
    }
  ],
  "reportMeta": { // mode=report时返回
    "fileId": "f_789",
    "fileName": "2024世界赛决赛经济差分析报告.pdf",
    "fileType": "pdf",
    "size": 102400
  },
  "durationMs": 8000,
  "model": "deepseek"
}
```

### 2.3 文件传输接口
#### 2.3.1 获取深度报告文件（后端下载）
- 路径：`GET /api/v1/ai/files/{fileId}`
- 响应类型：根据文件类型动态返回（如`application/pdf`、`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`）
- 请求头：携带鉴权字段（X-AI-API-Key + Authorization）
- 响应：
  - 成功：文件二进制流 + 响应头`Content-Disposition: attachment; filename="2024世界赛决赛经济差分析报告.pdf"`
  - 失败：返回1.3的统一错误结构

#### 2.3.2 上传辅助文件（可选，如自定义模板）
- 路径：`POST /api/v1/ai/files/upload`
- 请求类型：`multipart/form-data`
- 功能：后端上传报告模板、自定义配置文件等，AI端用于生成个性化深度报告
- 请求参数：
  | 字段       | 类型   | 必选 | 说明                     |
  |------------|--------|------|--------------------------|
  | file       | file   | 是   | 上传文件（支持pdf/excel/md） |
  | fileType   | string | 是   | 文件类型：`template/report_config` |
  | sessionId  | string | 否   | 关联会话ID（可选）       |
- 响应体：
  ```json
  {
    "fileId": "f_100",
    "fileName": "report_template.pdf",
    "fileType": "template",
    "size": 51200,
    "uploadedAt": "2024-11-05T10:10:00Z"
  }
  ```

### 2.4 辅助接口
#### 2.4.1 会话上下文查询
- 路径：`POST /api/v1/ai/chat/history`
- 功能：AI端返回指定会话的历史交互记录（含用户查询、AI响应、数据库查询结果）
- 请求体：
  ```json
  {
    "sessionId": "s_abc123",
    "page": 1,
    "pageSize": 50
  }
  ```
- 响应体：
  ```json
  {
    "items": [
      {
        "role": "user",
        "content": "2024世界赛决赛经济差分析",
        "ts": "2024-11-05T10:00:00Z",
        "context": { "tournamentId": "2024-worlds" }
      },
      {
        "role": "assistant",
        "content": "完整回答文本...",
        "ts": "2024-11-05T10:00:08Z",
        "mode": "report",
        "dataRefs": ["chart:gold_diff_15"],
        "fileRefs": ["f_789"]
      }
    ],
    "page": 1,
    "pageSize": 50,
    "total": 2
  }
  ```

#### 2.4.2 数据库查询结果解析（可选）
- 路径：`POST /api/v1/ai/db/parse`
- 功能：后端单独提交数据库查询结果，AI端解析为结构化数据（供前端图表渲染）
- 请求体：
  ```json
  {
    "traceId": "t_002",
    "dbQueryResult": [
      {
        "sql": "SELECT champion, pick_rate FROM pickban_stats WHERE tournamentId='2024-worlds'",
        "data": [["Orianna", 0.42], ["Zed", 0.35]],
        "columns": ["champion", "pick_rate"]
      }
    ],
    "parseType": "chart", // 解析类型：chart/table/report
    "chartConfig": { "type": "bar", "xField": "champion", "yField": "pick_rate" } // parseType=chart时必传
  }
  ```
- 响应体：
  ```json
  {
    "traceId": "t_002",
    "parsedData": {
      "type": "chart",
      "chartId": "champion_pick_rate",
      "title": "2024世界赛英雄选取率",
      "series": [{"name":"pick_rate","points":[{"x":"Orianna","y":0.42},{"x":"Zed","y":0.35}]}]
    }
  }
  ```

## 三、接口扩展规则
### 3.1 事件类型扩展
新增SSE事件类型时，需遵循`event: [类型]`命名规则，且`data`字段必须为JSON格式，新增类型需在文档中补充枚举。

### 3.2 模式扩展
新增交互模式（如`comparison`选手对比、`forecast`赛事预测）时，仅需扩展`mode`字段枚举，并在SSE响应中新增对应模式的专属事件（如`comparison_data`）。

### 3.3 数据格式扩展
结构化数据（`data`事件）新增类型时，需保持`type`字段唯一，且包含`id`（如`chartId`/`tableId`）便于前端关联。

## 四、部署与联调建议
1. **本地联调**：AI端启动时绑定`0.0.0.0:8000`，后端配置AI端地址为`http://localhost:8000/api/v1/ai`；
2. **流式调试**：使用`curl`测试SSE接口：
   ```bash
   curl -X POST http://localhost:8000/api/v1/ai/chat/stream \
   -H "X-AI-API-Key: your_api_key" \
   -H "Authorization: Bearer your_jwt_token" \
   -H "Content-Type: application/json" \
   -d '{"sessionId":"s_abc123","traceId":"t_001","message":"2024世界赛决赛谁赢了？","mode":"simple","context":{"tournamentId":"2024-worlds"}}'
   ```
3. **文件传输测试**：优先测试`pdf`/`markdown`格式的深度报告生成，验证文件ID与下载接口的一致性；
4. **错误处理联调**：模拟大模型超时、数据库结果解析失败等场景，验证后端能否正确接收AI端的`error`事件/错误结构。

## 五、兼容性说明
1. AI端接口与后端API版本对齐（均为`v1`），版本升级时需在Base URL中体现（如`/api/v2/ai`）；
2. SSE事件格式兼容后端前端交互的事件规范，后端无需额外转换即可转发给前端；
3. 数据库查询结果格式兼容后端`/api/v1/metrics/query`的响应结构，降低数据适配成本。