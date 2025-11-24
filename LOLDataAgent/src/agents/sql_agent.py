from langchain_community.utilities import SQLDatabase
from langchain_community.agent_toolkits import create_sql_agent
from config.settings import settings
from src.llms.qwen_llm import get_qwen_llm

class SQLAgent:
    def __init__(self, llm=None):
        self.llm = llm if llm else get_qwen_llm()
        self.db = SQLDatabase.from_uri(settings.DATABASE_URL)
        self.agent_executor = self._create_agent()

    def _create_agent(self):
        return create_sql_agent(
            llm=self.llm,
            db=self.db,
            agent_type="openai-tools",
            verbose=True
        )

    def run(self, query: str):
        return self.agent_executor.invoke({"input": query})
