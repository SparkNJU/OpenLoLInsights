import os
import sys

# 将项目根目录添加到 python path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from src.agents.base_agent import BaseAgent
from src.agents.sql_agent import SQLAgent
from config.settings import settings

def main():
    print("欢迎使用 LolDataAgent!")
    print("--------------------------------")
    
    # 1. 演示 Base Agent (带有 RAG 工具)
    print("\n[1] 测试 Base Agent (RAG & Chat)")
    try:
        # 确保 API KEY 存在，否则会报错
        if not settings.QWEN_API_KEY:
            print("警告: 未设置 QWEN_API_KEY，跳过 Base Agent 测试。请在 config/.env 中配置。")
        else:
            base_agent = BaseAgent()
            query = "亚索的背景故事是什么？"
            print(f"用户: {query}")
            response = base_agent.run(query)
            print(f"Agent: {response['output']}")
    except Exception as e:
        print(f"Base Agent 运行出错: {e}")

    # 2. 演示 SQL Agent
    print("\n[2] 测试 SQL Agent (Database)")
    try:
        # 确保数据库配置正确
        # 注意：如果没有真实的数据库连接，这里会失败
        if settings.DB_PASSWORD == "":
             print("警告: 未设置数据库密码，跳过 SQL Agent 测试。请在 config/.env 中配置。")
        else:
            sql_agent = SQLAgent()
            query = "查询数据库中有多少个英雄？" # 假设有一个 heroes 表
            print(f"用户: {query}")
            response = sql_agent.run(query)
            print(f"Agent: {response['output']}")
    except Exception as e:
        print(f"SQL Agent 运行出错 (这是预期的，如果你没有配置真实的数据库): {e}")

if __name__ == "__main__":
    main()
