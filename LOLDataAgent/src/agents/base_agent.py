from langchain.agents import AgentExecutor, create_openai_tools_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from src.llms.qwen_llm import get_qwen_llm
from src.memory.buffer_memory import get_buffer_memory
from src.tools.tool_registry import get_all_tools

class BaseAgent:
    def __init__(self, llm=None, tools=None):
        self.llm = llm if llm else get_qwen_llm()
        self.tools = tools if tools else get_all_tools()
        self.memory = get_buffer_memory()
        self.agent_executor = self._create_agent()

    def _create_agent(self):
        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", "你是一个乐于助人的 AI 助手，专注于英雄联盟数据分析。"),
                ("user", "{input}"),
                MessagesPlaceholder(variable_name="agent_scratchpad"),
            ]
        )
        
        agent = create_openai_tools_agent(self.llm, self.tools, prompt)
        
        return AgentExecutor(
            agent=agent, 
            tools=self.tools, 
            verbose=True,
            # memory=self.memory # 注意：AgentExecutor 的 memory 处理比较复杂，这里简化处理，直接在外部管理或使用简单的 memory
        )

    def run(self, query: str):
        return self.agent_executor.invoke({"input": query})
