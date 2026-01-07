import json
import re
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

from langchain.schema import HumanMessage, SystemMessage

from src.llms.qwen_llm import get_qwen_llm
from src.llms.deepseek_llm import get_deepseek_llm
from src.tools.custom_tools.db_tool import db_query
from src.tools.custom_tools.serper_tool import serper_search


DB_SCHEMA_DESC = (
    "当前 MySQL 数据库包含 5 个核心表（大小写敏感）：\n\n"
    
    "1. `Teams` (战队表)\n"
    "   - 字段: id (PK), name (全称), short_name (简称, 如 'GEN', 'BLG', 'T1')\n"
    "   - 查询技巧: 总是优先使用 short_name 进行匹配。\n\n"
    
    "2. `Players` (选手表)\n"
    "   - 字段: id (PK), name (游戏ID, 唯一, 如 'Faker', 'Bin')\n\n"
    
    "3. `Matches` (大场信息表)\n"
    "   - 字段: id (PK), match_date, team1_id, team2_id, winner_id, tournament_name, stage\n"
    "   - 【重要】tournament_name (赛事名) 结构为 'YY/赛区/赛事'。\n"
    "   - 【2025年赛制映射字典 (必须严格遵守)】:\n"
    "       (1) 全球赛事:\n"
    "           - '2025年全球先锋赛' / 'First Stand' -> LIKE '25/全球先锋赛%'\n"
    "           - '2025年MSI' -> LIKE '25/m%'\n"
    "           - '2025年S赛' / '世界赛' -> LIKE '25/s%'\n"
    "       (2) LCK 赛区 (2025):\n"
    "           - '2025 LCK杯' -> LIKE '25/lck/lck杯%'\n"
    "           - '2025 LCK春季赛' / '第一赛段' -> LIKE '25/lck/第一赛段%'\n"
    "           - '2025 LCK夏季赛' / '第二赛段' -> LIKE '25/lck/第二赛段%'\n"
    "       (3) LPL 赛区 (2025 改为三赛段制):\n"
    "           - '2025 LPL春季赛' / '第一赛段' -> LIKE '25/lpl/第一赛段%'\n"
    "           - '2025 LPL第二赛段' -> LIKE '25/lpl/第二赛段%'\n"
    "           - '2025 LPL夏季赛' / '第三赛段' -> LIKE '25/lpl/第三赛段%'\n"
    "       (4) 历史赛事 (2024及以前):\n"
    "           - 春季赛 -> LIKE '%/春季赛%'\n"
    "           - 夏季赛 -> LIKE '%/夏季赛%'\n"
    "           - S赛 -> LIKE '%/s%' (注意: 格式为 'YY/s')\n\n"
    "   - 【S赛(全球总决赛) 赛段特殊处理 (必须阅读)】:\n"
    "       * 数据现状: S赛的 stage 字段命名不统一。\n"
    "         - 绝大多数年份 (14-15, 17-23, 25): 八强/半决/决赛 统称为 '淘汰'。\n"
    "         - 特殊年份 (16, 24): 包含 '半决', '总决', '淘汰-四强' 等细分。\n"
    "       * **查询策略 (Golden Rules)**:\n"
    "         1. **查特定对阵 (H2H)**: 如 '23年S赛 T1打JDG'，**严禁过滤 stage**！只过滤 tournament_name 和 teams 即可锁定比赛。\n"
    "         2. **查泛淘汰赛**: 如 '23年S赛淘汰赛数据'，使用 `AND (stage LIKE '%淘汰%' OR stage LIKE '%半决%' OR stage LIKE '%总决%' OR stage LIKE '%四强%')` 以兼容所有年份。\n"
    "         3. **查小组赛/瑞士轮**: 可以安全使用 `LIKE '%小组%'` 或 `LIKE '%瑞士%'`。\n\n"
    
    "4. `Games` (小局信息表 - **红蓝方计算核心**)\n"
    "   - 字段: id (PK), match_id, game_number, winner_id\n"
    "   - 【关键字段】: blue_team_id (蓝方战队ID), red_team_id (红方战队ID)\n"
    "   - 注意: 计算红蓝方胜率必须使用此表的 blue_team_id 和 red_team_id，严禁使用 Matches 表的 team1/2。\n\n"
    
    "5. `PlayerGameStats` (选手单局表现表)\n"
    "   - 关联: game_id, player_id, team_id\n"
    "   - 统计字段: kills, deaths, assists, total_damage_to_champions (伤害), gold_earned (经济)\n\n"
    
    "【SQL 逻辑与默认规则 (必须遵守)】\n"
    "1. **默认范围原则**: \n"
    "   - 若用户只提年份(如'25年')没提赛事，则默认该年所有赛事 (LIKE '25/%')。\n"
    "   - 若用户指定赛事但没提阶段(如'25年LPL第三赛段')，则不筛选 stage 字段，包含该赛事下季后赛/常规赛等所有记录。\n"
    "2. **队伍间对阵 (Head-to-Head)**: \n"
    "   - 必须使用双 JOIN Teams (T_A, T_B)。\n"
    "   - WHERE ((M.team1_id=T_A.id AND M.team2_id=T_B.id) OR (M.team1_id=T_B.id AND M.team2_id=T_A.id))\n"
    "3. **战队详细胜负**: \n"
    "   - 胜场: SUM(CASE WHEN winner_id=T.id THEN 1 ELSE 0 END)\n"
    "   - 败场: SUM(CASE WHEN winner_id!=T.id THEN 1 ELSE 0 END)\n"
    "   - 胜率: (胜场 * 100.0 / 总场数)\n"
    "4. **选手特定赛事数据 (4表联查)**: \n"
    "   - 路径: Players -> PlayerGameStats -> Games -> Matches\n"
    "   - 范式: FROM PlayerGameStats PGS JOIN Players P ON ... JOIN Games G ON ... JOIN Matches M ON ...\n"
    "   - KDA公式: SUM(PGS.kills + PGS.assists) / NULLIF(SUM(PGS.deaths), 0)\n"
    "5. **红蓝方胜率公式 (修正版)**: \n"
    "   - 必须关联 Games 和 Matches (Matches用于筛选赛事)。\n"
    "   - 蓝方胜: CASE WHEN G.winner_id = G.blue_team_id THEN 1 ELSE 0 END\n"
    "   - 红方胜: CASE WHEN G.winner_id = G.red_team_id THEN 1 ELSE 0 END\n"
    "   - SQL示例: SELECT SUM(CASE WHEN G.winner_id = G.blue_team_id THEN 1 ELSE 0 END) * 100.0 / COUNT(*) ...\n"
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
                self.llm = get_deepseek_llm()
            except Exception:
                # Tests/local runs may not have API keys.
                self.llm = _OfflineStubLLM()
        self.model_name = getattr(self.llm, "model_name", getattr(self.llm, "model", "deepseek"))
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
                "6) 计数类问题用 COUNT(*)。\n\n"

                "【思维链示例 (Chain of Thought)】\n\n"
                
                "Case 1: 战队对战 (指定赛事与阶段)\n"
                "用户: '25年LPL夏季赛季后赛 BLG 打 TES 的胜率'\n"
                "思考: 1.映射: 25 LPL夏季赛 -> '第三赛段'。2.范围: 季后赛 -> stage LIKE '%季后%'。3.对阵: H2H双表关联。\n"
                "SQL: SELECT SUM(CASE WHEN M.winner_id = T_A.id THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) AS win_rate FROM Matches M JOIN Teams T_A ON T_A.short_name='BLG' JOIN Teams T_B ON T_B.short_name='TES' WHERE ((M.team1_id=T_A.id AND M.team2_id=T_B.id) OR (M.team1_id=T_B.id AND M.team2_id=T_A.id)) AND M.tournament_name LIKE '%25/lpl/第三赛段%' AND M.stage LIKE '%季后%'\n\n"
                
                "Case 2: 战队综合胜负 (只指定年份，默认全赛事)\n"
                "用户: 'GenG 25年的胜负场情况'\n"
                "思考: 1.范围: 用户仅说'25年' -> LIKE '25/%' (包含春/夏/MSI/S赛)。2.指标: 胜场、败场、胜率。\n"
                "SQL: SELECT T.short_name, SUM(CASE WHEN M.winner_id = T.id THEN 1 ELSE 0 END) as wins, SUM(CASE WHEN M.winner_id != T.id THEN 1 ELSE 0 END) as losses, COUNT(*) as total_games FROM Matches M JOIN Teams T ON (M.team1_id = T.id OR M.team2_id = T.id) WHERE T.short_name = 'GEN' AND M.tournament_name LIKE '25/%'\n\n"
                
                "Case 3: 选手特定赛事数据 (4表联查)\n"
                "用户: 'Faker 25年S赛的总KDA'\n"
                "思考: 1.对象: Faker (Players)。2.赛事: 25年S赛 -> LIKE '%25/s%' (Matches)。3.路径: P->PGS->G->M。4.计算: KDA公式。\n"
                "SQL: SELECT SUM(PGS.kills) as total_kills, SUM(PGS.deaths) as total_deaths, SUM(PGS.assists) as total_assists, (SUM(PGS.kills)+SUM(PGS.assists))/NULLIF(SUM(PGS.deaths),0) as kda FROM PlayerGameStats PGS JOIN Players P ON PGS.player_id = P.id JOIN Games G ON PGS.game_id = G.id JOIN Matches M ON G.match_id = M.id WHERE P.name = 'Faker' AND M.tournament_name LIKE '%25/s%'\n"
                
                "Case 4: 红蓝方胜率 (使用Games表字段)\n"
                "用户: '25年MSI红蓝方胜率情况'\n"
                "思考: 1.筛选: Matches表筛选赛事 '25/m%'。2.关联: Join Games表。3.计算: 使用 Games.blue_team_id 和 Games.red_team_id 判断小局胜负。\n"
                "SQL: SELECT SUM(CASE WHEN G.winner_id = G.blue_team_id THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) as blue_win_rate, SUM(CASE WHEN G.winner_id = G.red_team_id THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) as red_win_rate, COUNT(*) as total_games FROM Games G JOIN Matches M ON G.match_id = M.id WHERE M.tournament_name LIKE '%25/m%'\n"

                "Case 5: S赛特定对阵 (防御性策略)\n"
                "用户: '23年S赛半决赛 T1 打 JDG 的结果'\n"
                "思考: 1.赛区: 23年S赛 -> '%23/s%'。2.陷阱: 23年S赛数据将半决赛统称为'淘汰'，没有'半决'字段。3.策略: 既然锁定了 T1 和 JDG，直接双表关联队伍即可，**丢弃 stage 过滤**以防止漏查。\n"
                "SQL: SELECT M.match_date, T_W.short_name as winner FROM Matches M JOIN Teams T_A ON T_A.short_name='T1' JOIN Teams T_B ON T_B.short_name='JDG' LEFT JOIN Teams T_W ON M.winner_id = T_W.id WHERE ((M.team1_id=T_A.id AND M.team2_id=T_B.id) OR (M.team1_id=T_B.id AND M.team2_id=T_A.id)) AND M.tournament_name LIKE '%23/s%'\n\n"
                
                "Case 6: S赛泛淘汰赛阶段统计 (兼容性写法)\n"
                "用户: 'Faker 16年S赛淘汰赛阶段的击杀数'\n"
                "思考: 16年S赛 stage 区分了'半决/总决'，不能只查'淘汰'。使用 OR 逻辑兼容所有细分名称。\n"
                "SQL: SELECT SUM(PGS.kills) FROM PlayerGameStats PGS JOIN Players P ON PGS.player_id = P.id JOIN Games G ON PGS.game_id = G.id JOIN Matches M ON G.match_id = M.id WHERE P.name = 'Faker' AND M.tournament_name LIKE '%16/s%' AND (M.stage LIKE '%淘汰%' OR M.stage LIKE '%半决%' OR M.stage LIKE '%总决%' OR M.stage LIKE '%四强%')\n"   
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