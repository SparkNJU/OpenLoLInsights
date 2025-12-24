import json
from typing import Dict, List, Optional

from langchain.schema import HumanMessage, SystemMessage

from src.llms.qwen_llm import get_qwen_llm
from src.agents.sql_agent import SQLAgent
from src.tools.custom_tools.serper_tool import serper_search
from src.tools.custom_tools.rag_tool import rag_search


DB_SCHEMA_DESC = (
    "数据库目前只有 Teams(id, name, short_name, region) 和 Players(id, name) 两张表，"
    "用于战队和选手的结构化信息。不包含英雄背景故事、技能描述或版本改动。"
)


class Orchestrator:
    """Coordinator for choosing DB vs web search and synthesizing answers."""

    def __init__(self, llm=None):
        self.llm = llm if llm else get_qwen_llm()
        self.model_name = getattr(self.llm, "model_name", getattr(self.llm, "model", "qwen-plus"))
        # SQL Agent 用于 NL2SQL + 执行
        try:
            self.sql_agent = SQLAgent(llm=self.llm)
        except Exception as e:
            self.sql_agent = None
            self.sql_error = str(e)

    @staticmethod
    def _is_db_question(query: str) -> bool:
        q = (query or "").lower()
        return any(k in q for k in ["team", "teams", "战队", "队伍", "选手", "player", "队名", "名单", "数量", "多少个", "list", "count"])

    @staticmethod
    def _is_lore_question(query: str) -> bool:
        q = query or ""
        return any(k in q for k in ["背景", "故事", "技能", "英雄", "版本", "攻略", "出装", "lore", "story"])

    def _plan_tasks(self, query: str, mode: str) -> List[Dict]:
        # 根据问题类型调整调用顺序
        is_db = self._is_db_question(query)
        is_lore = self._is_lore_question(query)

        tasks: List[Dict] = []
        if is_db:
            tasks.append({"name": "sql_query", "tool": "sql_agent", "input": query})
            # 数据库可能不覆盖全部，需要网页兜底
            tasks.append({"name": "search_agent", "tool": "serper", "input": query})
        if is_lore and not is_db:
            tasks.append({"name": "retrieve_domain", "tool": "rag", "input": query})
            tasks.append({"name": "search_agent", "tool": "serper", "input": query})
        if not tasks:
            # 默认全链路
            tasks.extend([
                {"name": "retrieve_domain", "tool": "rag", "input": query},
                {"name": "search_agent", "tool": "serper", "input": query},
                {"name": "sql_query", "tool": "sql_agent", "input": query},
            ])

        tasks.append({"name": "synthesis", "tool": "llm", "input": query})
        return tasks

    def _run_tool(self, tool: str, tool_input: str) -> str:
        if tool == "rag":
            return rag_search(tool_input)
        if tool == "serper":
            return serper_search(tool_input)
        if tool == "sql_agent":
            if not self.sql_agent:
                return f"sql_agent unavailable: {getattr(self, 'sql_error', 'unknown error')}"
            try:
                resp = self.sql_agent.run(tool_input)
                return resp.get("output") if isinstance(resp, dict) else str(resp)
            except Exception as e:
                return f"sql_agent error: {e}"
        return "unsupported tool"

    def _synthesize(self, query: str, mode: str, tool_outputs: Dict[str, str]) -> str:
        system = (
            "你是英雄联盟赛事分析助手。"
            f"{DB_SCHEMA_DESC}"
            " 如果问题与战队/选手等结构化数据相关，优先使用 SQL 结果；如果 SQL 缺失或提示 unknown，"
            "用搜索结果补充。关于英雄背景、版本、攻略等非结构化信息，只能用 RAG/Web 搜索，不要臆造数据库字段。"
            " 回复时标明主要信息来源（SQL / Web / RAG）。"
        )
        if mode == "report":
            system += " 生成要点式报告，附简短出处。"
        parts = []
        for k, v in tool_outputs.items():
            parts.append(f"[{k}] {v}")
        context = "\n".join(parts) if parts else "(无工具结果)"
        messages = [
            SystemMessage(content=system),
            HumanMessage(content=f"用户问题: {query}\n工具结果:\n{context}\n请给出回答。"),
        ]
        resp = self.llm.invoke(messages)
        return resp.content if hasattr(resp, "content") else str(resp)

    def run(self, query: str, mode: str = "simple", context: Optional[dict] = None, report_config: Optional[dict] = None):
        steps_log = []
        tool_outputs = {}
        tasks = self._plan_tasks(query, mode)
        for task in tasks:
            name = task["name"]
            tool = task["tool"]
            if tool == "llm":
                answer = self._synthesize(query, mode, tool_outputs)
                steps_log.append({"step": name, "tool": tool, "output": answer})
                return {"answer": answer, "steps": steps_log, "tool_outputs": tool_outputs, "model": self.model_name}
            output = self._run_tool(tool, task["input"])
            tool_outputs[name] = output
            steps_log.append({"step": name, "tool": tool, "input": task["input"], "output": output})
        # fallback if no synthesis executed
        answer = self._synthesize(query, mode, tool_outputs)
        steps_log.append({"step": "synthesis", "tool": "llm", "output": answer})
        return {"answer": answer, "steps": steps_log, "tool_outputs": tool_outputs, "model": self.model_name}