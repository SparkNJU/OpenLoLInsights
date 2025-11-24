from langchain_openai import ChatOpenAI
from config.settings import settings

def get_qwen_llm(temperature=0.7, model_name="qwen-plus"):
    """
    获取通义千问 LLM 实例
    """
    if not settings.QWEN_API_KEY:
        raise ValueError("QWEN_API_KEY not found in environment variables")
        
    return ChatOpenAI(
        api_key=settings.QWEN_API_KEY,
        base_url=settings.QWEN_API_BASE,
        model=model_name,
        temperature=temperature,
    )
