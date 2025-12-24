# LOLDataAgent

LOLDataAgent 是一个基于大语言模型（LLM）的英雄联盟（League of Legends）数据智能助手。它利用 LangChain 框架，结合 SQL 数据库和 RAG（检索增强生成）技术，能够回答关于英雄背景故事、游戏数据统计等问题。

## 📁 项目结构

```
LOLDataAgent/
├── config/                 # 配置文件目录
│   ├── .env.example       # 环境变量示例文件
│   └── settings.py        # 配置加载脚本
├── src/                    # 源代码目录
│   ├── agents/            # Agent 实现
│   │   ├── base_agent.py  # 基础对话 Agent
│   │   └── sql_agent.py   # SQL 数据查询 Agent
│   ├── llms/              # LLM 模型封装
## 🚀 快速开始（服务模式）

1) 创建并激活虚拟环境并安装依赖

```bash
# Windows (PowerShell)
python -m venv .venv
./.venv/Scripts/Activate.ps1
python -m pip install -U pip setuptools wheel
python -m pip install -r requirements.txt
```

2) 配置环境变量

```bash
cd config
copy .env.example .env   # Linux/macOS 用 cp
```

填写至少：
```
QWEN_API_KEY=your_qwen_key           # 兼容模式：dashscope aliyun
SERPER_API_KEY=your_serper_key       # Web 搜索
DB_USER=... / DB_PASSWORD=...        # MySQL 账号
DB_HOST=localhost
DB_PORT=3306
DB_NAME=lol_data
```

3) 启动 FastAPI

```bash
uvicorn src.server:app --host 0.0.0.0 --port 8000 --reload
```

4) 本地自测

- 浏览器访问主页测试页：http://localhost:8000/
- 健康检查：http://localhost:8000/api/v1/ai/health
- 工具列表：http://localhost:8000/api/v1/ai/tools
- 流式 SSE（Orchestrator）：
	```bash
	curl -N -X POST http://localhost:8000/api/v1/ai/chat/stream \
		-H "Content-Type: application/json" \
		-d '{"mode":"simple","query":"亚索的背景故事是什么？"}'
	```
- 非流式 JSON：
	```bash
	curl -X POST http://localhost:8000/api/v1/ai/chat/query \
		-H "Content-Type: application/json" \
		-d '{"mode":"report","query":"2024 世界赛决赛经济差分析"}'
	```
- 下载报告（report 模式生成的 fileId）：
	```bash
	curl -O http://localhost:8000/api/v1/ai/files/<fileId>
	```

5) 可选：演示脚本

```bash
python main.py
```
用于 CLI 体验 BaseAgent / SQLAgent（需配置 API Key 和数据库）。

## 🌐 路由速览

- `GET /api/v1/ai/health` 健康检查
- `GET /api/v1/ai/tools` 工具名称列表
- `POST /api/v1/ai/chat/stream` SSE 流式问答（事件：meta/data/token/file_meta/done）
- `POST /api/v1/ai/chat/query` 非流式问答
- `GET /api/v1/ai/files/{fileId}` 下载报告文件
- `GET /` 内置测试页面（简单前端调试）
	-d '{"agent":"orchestrator","mode":"report","query":"2024 世界赛决赛经济差分析"}'
```

**下载生成的报告文件**
```bash
curl -O http://localhost:8000/api/v1/ai/files/<fileId>
```

### 5) 可选：直接运行演示脚本

```bash
python main.py
```
将演示 BaseAgent / SQLAgent 的基础调用（需要对应的 API Key 和数据库）。

## ⚠️ 注意事项

- 运行 SQL Agent 前，请确保本地 MySQL 服务已启动，并且 `lol_data` 数据库已创建且包含相应的数据表。
- 如果缺少 API Key，相应的 Agent 测试将被跳过。
