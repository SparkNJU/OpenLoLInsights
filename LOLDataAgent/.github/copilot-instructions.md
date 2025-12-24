# Copilot 使用指引 — LOLDataAgent 🚀

目的：为 AI 编码代理（Copilot / Agent）提供立即可用的项目知识，使其能在该代码库中高效完成常见任务（修复 bug、添加工具、扩展 Agent、调试运行等）。

## 一览（大图景）
- 项目职责划分：
  - `src/agents/`：Agent 层（`BaseAgent`, `SQLAgent`） — 封装 LLM、工具、数据库查询和流程。
  - `src/llms/`：LLM 封装（`qwen_llm.py`, `deepseek_llm.py`）— 通过 `get_*_llm()` 获取配置好的 Chat LLM。
  - `src/tools/custom_tools/`：自定义工具（例如 `rag_tool.py`），通过 `src/tools/tool_registry.py` 注册提供给 Agent 使用。
  - `src/memory/`：会话记忆封装（`buffer_memory.py`）。
  - `config/settings.py`：环境/密钥和 `DATABASE_URL` 生成逻辑（使用 `.env`）。
  - `main.py`：示例演示脚本，展示 Base Agent（RAG）与 SQL Agent 的典型调用。

## 关键运行/调试流程 🔧
- 环境：Python 3.8+；依赖见 `requirements.txt`（包含 `langchain`, `langchain-community`, `langchain-openai`, `pymysql`, `sqlalchemy`, `python-dotenv`）。
- 快速运行：
  1. 创建虚拟环境并安装依赖： `pip install -r requirements.txt`。
  2. 复制并编辑配置： `config/.env.example -> config/.env`，填入 `QWEN_API_KEY`, `DEEPSEEK_API_KEY`, 数据库配置等。
  3. 确保 MySQL 已启动且 `lol_data` 等库/表已创建（SQL Agent 假设存在 `heroes` 等表）。
  4. 运行： `python main.py`（若缺少 API key 或 DB，会跳过或报错，`main.py` 已做基本容错提示）。
- 调试技巧：
  - Agent 的创建均启用了 `verbose=True` 来显示执行细节；在出现问题时保持或增大日志打印。
  - 在自定义工具中（如 `rag_tool.py`）使用 `print()` 来观察被调用与入参。

## 项目特定约定 / 模式 💡
- LLM 获取：总是通过 `src/llms/*_llm.py` 提供的 `get_*_llm()` 函数获得实例；这些函数会在缺少 API KEY 时抛出 `ValueError`（因此 `main.py` 中先做了检测）。
- 工具（Tools）：
  - 使用 `@tool` 装饰器（`langchain.tools.tool`）定义工具，函数签名接受 `str` 并返回 `str`。
  - 将工具添加到 `src/tools/tool_registry.py::get_all_tools()` 中，Agent 会通过该入口加载所有工具。
  - 示例：`src/tools/custom_tools/rag_tool.py` 当前返回模拟数据；真实实现应接入向量 DB（Chroma/FAISS）并返回检索结果。
- SQL Agent：
  - `src/agents/sql_agent.py` 使用 `SQLDatabase.from_uri(settings.DATABASE_URL)` 并以 `create_sql_agent(..., agent_type='openai-tools')` 创建 agent。
  - `config/settings.py` 负责拼接 `DATABASE_URL`，请确保 `pymysql`/`sqlalchemy` 版本兼容。
- Memory：
  - 使用 `src/memory/buffer_memory.py::get_buffer_memory()` 返回 `ConversationBufferMemory`。
  - 注意：`BaseAgent` 中 `AgentExecutor` 的 `memory` 参数被注释（项目目前选择外部或简单 memory 管理），在集成更复杂记忆时需检查 LangChain 兼容性。
- Prompt / Agent 模版：
  - `BaseAgent` 使用 `ChatPromptTemplate.from_messages([... , MessagesPlaceholder('agent_scratchpad')])` 来组织 system/user/agent 会话流；如需改动默认行为，请修改该模板。

## 典型开发任务示例（可直接执行的修改点）
- 添加新工具：
  1. 在 `src/tools/custom_tools/` 新建工具文件，使用 `@tool` 装饰并返回 `str`。
  2. 在 `src/tools/tool_registry.py` 的 `get_all_tools()` 中引入并添加到返回列表。
- 将 `rag_tool` 升级为真实 RAG：
  - 替换模拟数据逻辑为向量 DB 查询（推荐使用 Chroma/FAISS），返回最相关的文档片段。
  - 保持函数签名 `def rag_search(query: str) -> str`，并在工具内适当增加结构化输出或摘要。
- 添加新 Agent：
  - 在 `src/agents/` 新建类，遵循 `BaseAgent` / `SQLAgent` 模式：通过 `get_*_llm()` 获取 LLM，注册工具、memory，并实现 `run(self, query: str)` 调用 `agent_executor.invoke({"input": query})`。

## 注意事项 / 限制 ⚠️
- 当前示例中部分功能是模拟或演示（例如 `rag_tool` 的 mock 数据、`main.py` 的示例查询）。提交 PR 前应用真实数据源并添加相应单元/集成测试。
- 对外键/数据库变更：`SQLAgent` 假设可读数据库结构；对数据库做改动前请提供 migration 或示例 SQL 文件以便测试。

## 调试示例命令（快速验证）
- 基本： `python main.py` （会演示 Base/SQL Agent 的基本交互）
- 本地 DB 验证：用命令行或 GUI 检查 `lol_data` 下是否存在 `heroes` 表并包含行数，以验证 SQL Agent 查询返回。

---

需要我把以上内容合并到仓库根目录下的 `.github/copilot-instructions.md`（或更新已存在文件）吗？或者是否有你想补充的特殊规则或外部服务信息（例如：内部向量库地址、特定测试数据样例等）？ 🙋‍♂️