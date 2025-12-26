import json
import re
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

from langchain.schema import HumanMessage, SystemMessage

from src.llms.qwen_llm import get_qwen_llm
from src.tools.custom_tools.db_tool import db_query
from src.tools.custom_tools.serper_tool import serper_search


DB_SCHEMA_DESC = (
    "数据库包含以下表（字段名严格区分大小写，以 SQL 为准）：\n"
    "- Teams(id, name, short_name, region)\n"
    "- Players(id, name)\n"
    "- Matches(id, match_date, tournament_name, stage, team1_id, team2_id, winner_id)\n"
    "- Games(id, match_id, game_number, duration, blue_team_id, red_team_id, winner_id)\n"
    "- PlayerGameStats(id, game_id, player_id, team_id, position, champion_name, champion_name_en, "
    "player_level, kills, deaths, assists, kda, kill_participation, total_damage_dealt, "
    "damage_dealt_to_champions, damage_dealt_percentage, total_damage_taken, damage_taken_percentage, "
    "gold_earned, minions_killed, is_mvp)\n"
    "常用关联关系：\n"
    "- Matches.team1_id/team2_id/winner_id -> Teams.id\n"
    "- Games.match_id -> Matches.id；Games.blue_team_id/red_team_id/winner_id -> Teams.id\n"
    "- PlayerGameStats.game_id -> Games.id；player_id -> Players.id；team_id -> Teams.id\n"
    "注意：该库用于赛事结构化数据查询，不包含英雄背景故事、技能描述或版本改动等百科类内容。"
)


@dataclass
class _LLMResponse:
    content: str


class _OfflineStubLLM:
    """A minimal LLM stub so server/tests can run without external API keys."""

    model_name = "offline-stub"

    def invoke(self, messages: List[Any]) -> _LLMResponse:
        # Very small heuristic:
        # - If prompt asks for strict JSON with `sql`, return NO_SQL.
        # - Otherwise, return a short canned answer.
        joined = "\n".join(getattr(m, "content", str(m)) for m in (messages or []))
        if '"sql"' in joined and "JSON" in joined:
            return _LLMResponse(content='{"sql":"NO_SQL"}')
        return _LLMResponse(content="(离线模式) 当前未配置 LLM API key，无法进行联网总结或 NL2SQL。")


def _truncate(text: str, max_len: int = 2500) -> str:
    t = (text or "").strip()
    if len(t) <= max_len:
        return t
    return t[:max_len] + "…(truncated)"


def _extract_json_object(text: str) -> Optional[dict]:
    """Extract the first JSON object from text; return parsed dict or None."""
    if not text:
        return None
    s = text.strip()
    start = s.find("{")
    end = s.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return None
    candidate = s[start : end + 1]
    try:
        obj = json.loads(candidate)
        return obj if isinstance(obj, dict) else None
    except Exception:
        return None


def _normalize_sql(sql: str) -> str:
    s = (sql or "").strip()
    s = re.sub(r"\s+", " ", s)
    if s.endswith(";"):
        s = s[:-1].strip()
    return s


def _validate_select_sql(sql: str) -> Tuple[bool, str]:
    """Very small safety gate: allow only single SELECT without comments or other statements."""
    s = _normalize_sql(sql)
    if not s:
        return False, "empty sql"

    if not re.match(r"^select\b", s, re.I):
        return False, "only SELECT is allowed"

    # Block obvious multi-statement / comments / DDL/DML keywords.
    if any(tok in s.lower() for tok in ["--", "/*", "*/", ";"]):
        return False, "comments/multi-statements are not allowed"

    banned = re.compile(r"\b(insert|update|delete|drop|alter|create|truncate|replace|grant|revoke)\b", re.I)
    if banned.search(s):
        return False, "non-SELECT keywords detected"

    return True, s


class Orchestrator:
    """Coordinator for: Search -> NL2SQL -> DB Query -> Synthesis."""

    def __init__(self, llm=None):
        if llm is not None:
            self.llm = llm
        else:
            try:
                self.llm = get_qwen_llm()
            except Exception:
                # Tests/local runs may not have API keys.
                self.llm = _OfflineStubLLM()
        self.model_name = getattr(self.llm, "model_name", getattr(self.llm, "model", "qwen-plus"))

    def _search(self, query: str) -> str:
        # Use StructuredTool.invoke to avoid LangChain __call__ deprecation warnings
        try:
            return serper_search.invoke({"query": query})
        except Exception:
            return serper_search(query)

    def _nl2sql(self, query: str, search_context: str) -> Dict[str, Any]:
        """Use LLM to convert (query + web context) -> strict JSON: {"sql":"..."}."""
        system = (
            "你是一个 NL2SQL 生成器。\n"
            "你必须【只输出一行 JSON】且 JSON 必须能被 json.loads 解析。\n"
            "严禁输出 Markdown、解释文字、代码块、或多行内容。\n\n"
            "输出格式（严格）：{\"sql\": \"...\"}\n"
            "规则：\n"
            "1) 如果用户问题不需要数据库，或数据库无法回答，输出 {\"sql\":\"NO_SQL\"}\n"
            "2) 只能输出单条 SELECT 语句；禁止 INSERT/UPDATE/DELETE/DDL；禁止注释；禁止分号；\n"
            f"3) 只能使用以下表/字段：{DB_SCHEMA_DESC}\n"
            "4) 默认查询返回行数不超过 50（必要时加 LIMIT 50）。\n"
            "5) 模糊匹配用 LIKE（例如 name LIKE '%xxx%'）。\n"
            "6) 计数类问题用 COUNT(*)。\n"
        )
        web_ctx = _truncate(search_context, 1800)
        user = (
            f"用户问题: {query}\n"
            f"搜索到的背景信息(可能不完整):\n{web_ctx}\n\n"
            "请按上述规则输出 JSON。"
        )
        resp = self.llm.invoke([SystemMessage(content=system), HumanMessage(content=user)])
        raw = resp.content if hasattr(resp, "content") else str(resp)

        obj = _extract_json_object(raw)
        if not obj or "sql" not in obj:
            return {"sql": "NO_SQL", "raw": raw, "error": "invalid_json"}

        sql = obj.get("sql")
        if not isinstance(sql, str):
            return {"sql": "NO_SQL", "raw": raw, "error": "sql_not_string"}

        sql = sql.strip()
        return {"sql": sql, "raw": raw}

    def _db_query(self, sql: str) -> str:
        # db_query tool already enforces SELECT-only, but use .invoke to avoid deprecation
        try:
            return db_query.invoke({"query": sql})
        except Exception:
            return db_query(sql)

    def _synthesize(self, query: str, mode: str, tool_outputs: Dict[str, str]) -> str:
        system = (
            "你是英雄联盟赛事分析助手。\n"
            f"{DB_SCHEMA_DESC}\n"
            "你会收到：Web 搜索摘要、NL2SQL 生成的 SQL、以及数据库查询结果(JSON)。\n"
            "要求：\n"
            "- 结构化数据优先以 DB 查询结果为准；Web 仅做补充解释。\n"
            "- DB 里没有的字段不要臆造（例如英雄技能/背景等）。\n"
            "- 回复要标明主要来源：DB / Web。\n"
        )
        if mode == "report":
            system += (
                "输出 Markdown 报告，包含：\n"
                "1) 结论(要点)\n"
                "2) 证据与来源（分别写 DB/Web）\n"
                "3) 使用的 SQL（如果有）\n"
            )
        else:
            system += "输出简短回答（3-8 句），优先给结论。"

        parts = []
        for k, v in tool_outputs.items():
            parts.append(f"[{k}] {v}")
        context = "\n".join(parts) if parts else "(无工具结果)"
        messages = [
            SystemMessage(content=system),
            HumanMessage(content=f"用户问题: {query}\n\n阶段输出:\n{context}\n\n请给出回答。"),
        ]
        resp = self.llm.invoke(messages)
        return resp.content if hasattr(resp, "content") else str(resp)

    def run(self, query: str, mode: str = "simple", context: Optional[dict] = None, report_config: Optional[dict] = None):
        steps_log: List[Dict[str, Any]] = []
        tool_outputs: Dict[str, str] = {}

        # 1) Search stage (always first)
        search_out = self._search(query)
        tool_outputs["search"] = search_out
        steps_log.append({"step": "search", "tool": "serper_search", "input": query, "output": search_out})

        # 2) NL2SQL stage (always executed; may output NO_SQL)
        nl2sql = self._nl2sql(query, search_out)
        sql = (nl2sql.get("sql") or "").strip()
        tool_outputs["nl2sql"] = nl2sql.get("raw") if nl2sql.get("raw") else json.dumps({"sql": sql}, ensure_ascii=False)
        steps_log.append({"step": "nl2sql", "tool": "llm", "input": query, "output": nl2sql})

        # 3) DB query stage (only if valid SELECT)
        db_out = None
        sql_for_db = ""
        if sql and sql != "NO_SQL":
            ok, normalized_or_err = _validate_select_sql(sql)
            if ok:
                sql_for_db = normalized_or_err
                db_out = self._db_query(sql_for_db)
            else:
                db_out = json.dumps({"error": f"invalid sql: {normalized_or_err}", "sql": sql}, ensure_ascii=False)
        if db_out is not None:
            tool_outputs["db_query"] = db_out
            steps_log.append({"step": "db_query", "tool": "db_query", "input": sql_for_db or sql, "output": db_out})
        else:
            tool_outputs["db_query"] = json.dumps({"skipped": True, "reason": "NO_SQL"}, ensure_ascii=False)
            steps_log.append({"step": "db_query", "tool": "db_query", "input": sql, "output": tool_outputs["db_query"]})

        # 4) Report/Simple synthesis stage
        answer = self._synthesize(query, mode, tool_outputs)
        steps_log.append({"step": "synthesis", "tool": "llm", "output": answer})
        return {"answer": answer, "steps": steps_log, "tool_outputs": tool_outputs, "model": self.model_name}