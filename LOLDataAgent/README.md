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
│   │   ├── deepseek_llm.py
│   │   └── qwen_llm.py
│   ├── memory/            # 记忆模块
│   └── tools/             # 工具集
│       └── custom_tools/  # 自定义工具（如 RAG）
├── main.py                 # 程序入口
└── requirements.txt        # 项目依赖
```

## 🛠️ 环境准备

- Python 3.8 或更高版本
- MySQL 数据库（用于 SQL Agent 功能）

## 🚀 快速开始

### 1. 安装依赖

建议使用虚拟环境管理项目依赖：

```bash
# 创建虚拟环境
python -m venv venv

# 激活虚拟环境 (Windows)
.\venv\Scripts\activate

# 激活虚拟环境 (Linux/macOS)
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt
```

### 2. 配置环境变量

在 `config` 目录下，将 `.env.example` 复制为 `.env`，并填入你的 API Key 和数据库配置：

```bash
cd config
cp .env.example .env
```

编辑 `config/.env` 文件：

```ini
# Qwen Configuration (通义千问)
QWEN_API_KEY=your_qwen_api_key_here
QWEN_API_BASE=https://dashscope.aliyuncs.com/compatible-mode/v1

# DeepSeek Configuration (DeepSeek)
DEEPSEEK_API_KEY=your_deepseek_api_key_here
DEEPSEEK_API_BASE=https://api.deepseek.com

# Database Configuration (MySQL)
DB_USER=root
DB_PASSWORD=your_password
DB_HOST=localhost
DB_PORT=3306
DB_NAME=lol_data
```

### 3. 运行程序

回到 `LOLDataAgent` 根目录并运行 `main.py`：

```bash
python main.py
```

程序启动后，将自动演示以下功能：
1. **Base Agent**: 使用 RAG 工具回答关于英雄背景故事的问题（例如：“亚索的背景故事是什么？”）。
2. **SQL Agent**: 连接数据库查询统计数据（例如：“查询数据库中有多少个英雄？”）。

## ⚠️ 注意事项

- 运行 SQL Agent 前，请确保本地 MySQL 服务已启动，并且 `lol_data` 数据库已创建且包含相应的数据表。
- 如果缺少 API Key，相应的 Agent 测试将被跳过。
