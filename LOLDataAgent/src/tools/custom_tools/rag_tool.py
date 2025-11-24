from langchain.tools import tool

@tool
def rag_search(query: str) -> str:
    """
    使用 RAG (检索增强生成) 技术搜索相关文档。
    当用户询问具体的游戏攻略、英雄背景故事或者版本更新日志时，使用此工具。
    """
    # 这里应该连接向量数据库，例如 Chroma, FAISS 等
    # 为了演示，我们返回模拟数据
    print(f"[RAG Tool] Searching for: {query}")
    
    mock_knowledge_base = {
        "亚索": "亚索是一个高机动性的战士，擅长利用风的力量。他的大招是狂风绝息斩。",
        "Faker": "Faker 是英雄联盟历史上最伟大的选手之一，效力于 T1 战队。",
        "S14": "S14 赛季引入了新的地图机制和装备改动。"
    }
    
    for key, value in mock_knowledge_base.items():
        if key in query:
            return f"找到相关文档: {value}"
            
    return "未找到相关文档，请尝试其他关键词。"
