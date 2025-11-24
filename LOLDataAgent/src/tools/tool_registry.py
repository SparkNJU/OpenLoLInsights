from src.tools.custom_tools.rag_tool import rag_search

def get_all_tools():
    """
    获取所有注册的工具列表
    """
    return [
        rag_search,
        # 在这里添加更多工具
    ]
