from langchain_openai import ChatOpenAI
from config.settings import settings

def get_deepseek_llm(temperature=0.7, model_name="deepseek-chat"):
    """
    获取 DeepSeek LLM 实例
    """
    if not settings.DEEPSEEK_API_KEY:
        raise ValueError("DEEPSEEK_API_KEY not found in environment variables")
        
    return ChatOpenAI(
        api_key=settings.DEEPSEEK_API_KEY,
        base_url=settings.DEEPSEEK_API_BASE,
        model=model_name,
        temperature=temperature,
    )
