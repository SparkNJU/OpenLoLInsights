from src.tools.custom_tools.rag_tool import rag_search
from src.tools.custom_tools.db_tool import db_query
from src.tools.custom_tools.serper_tool import serper_search

def get_all_tools():
    """
    获取所有注册的工具列表
    """
    return [
        # rag_search,
        db_query,
        serper_search,
    ]
