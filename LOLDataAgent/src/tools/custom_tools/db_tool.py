from langchain.tools import tool
# Import the settings instance explicitly to avoid the ambiguity where
# `from config import settings` returns the module object instead of the
# `settings` instance defined in `config/settings.py`.
from config.settings import settings
from sqlalchemy import create_engine, text
from sqlalchemy.pool import NullPool
import json, os
import re
from decimal import Decimal  # <--- 修改 1：导入 Decimal

DEFAULT_LIMIT = 50

# <--- 修改 2：定义自定义 JSON 编码器
class DecimalEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, Decimal):
            return float(o)  # 将 Decimal 转为 float
        return super(DecimalEncoder, self).default(o)

def _get_engine():
    # allow overriding via DATABASE_URL env var (useful in tests)
    db_url = os.getenv('DATABASE_URL') or settings.DATABASE_URL
    # On Windows, SQLite files can remain locked if connections are pooled.
    # Use NullPool for sqlite to ensure connections are not held.
    if db_url.startswith('sqlite:'):
        return create_engine(db_url, poolclass=NullPool)
    return create_engine(db_url)

def _to_json_result(result):
    cols = result.keys()
    rows = [list(r) for r in result.fetchall()]
    return {'columns': list(cols), 'rows': rows}

def _safe_select(sql_text, params=None):
    sql_text = sql_text.strip()
    if not re.match(r'^select\b', sql_text, re.I):
        return {'error': 'Only SELECT queries are allowed'}

    engine = _get_engine()
    try:
        with engine.connect() as conn:
            result = conn.execute(text(sql_text), params or {})
            return _to_json_result(result)
    except Exception as e:
        return {'error': str(e)}
    finally:
        try:
            engine.dispose()
        except Exception:
            pass

@tool
def db_query(query: str) -> str:
    """
    Schema-aware DB query tool.

    支持自然语言风格的快捷查询：
      - "count teams" -> 返回 Teams 表的数量
      - "count players" -> 返回 Players 表的数量
      - "list teams limit N" -> 列出 Teams（默认 limit=50）
      - "get team <code_or_name>" -> 根据 name/code 搜索 team
      - "find player <name> [limit N]" -> 根据 name 模糊搜索 players
      - 以 "select ..." 开头的 SQL 将被执行（仅限 SELECT）

    返回 JSON 字符串，字段 `columns` 和 `rows`。
    """
    q = (query or '').strip()
    if not q:
        return json.dumps({'error': 'empty query'})

    q_low = q.lower()

    # <--- 修改 3：所有 json.dumps 都加上 cls=DecimalEncoder

    # count teams
    if q_low == 'count teams':
        res = _safe_select('SELECT COUNT(*) AS cnt FROM Teams')
        return json.dumps(res, cls=DecimalEncoder)

    # count players
    if q_low == 'count players':
        res = _safe_select('SELECT COUNT(*) AS cnt FROM Players')
        return json.dumps(res, cls=DecimalEncoder)

    # list teams limit N
    m = re.match(r'^list teams(?: limit (\d+))?$', q_low)
    if m:
        limit = int(m.group(1)) if m.group(1) else DEFAULT_LIMIT
        res = _safe_select(f'SELECT id, name, region FROM Teams LIMIT {limit}')
        return json.dumps(res, cls=DecimalEncoder)

    # get team <code_or_name>
    m = re.match(r'^get team\s+(.+)$', q_low)
    if m:
        key = m.group(1).strip()
        # try by exact match on name or code
        sql = 'SELECT * FROM Teams WHERE name = :k OR region = :k LIMIT 10'
        res = _safe_select(sql, {'k': key})
        return json.dumps(res, cls=DecimalEncoder)

    # find player <name> [limit N]
    m = re.match(r'^find player\s+([\w\s\-\']+)(?:\s+limit\s+(\d+))?$', q, re.I)
    if m:
        name = m.group(1).strip()
        limit = int(m.group(2)) if m.group(2) else DEFAULT_LIMIT
        # Note: some DBs don't allow LIMIT param binding; use formatting safely for limit
        engine = _get_engine()
        try:
            with engine.connect() as conn:
                result = conn.execute(text('SELECT * FROM Players WHERE name LIKE :pat LIMIT %d' % limit), {'pat': f'%{name}%'})
                res = _to_json_result(result)
                return json.dumps(res, cls=DecimalEncoder)
        except Exception as e:
            return json.dumps({'error': str(e)})
        finally:
            try:
                engine.dispose()
            except Exception:
                pass

    # fallback: if starts with select, execute
    if re.match(r'^select\b', q_low):
        res = _safe_select(q)
        return json.dumps(res, cls=DecimalEncoder)

    return json.dumps({'error': 'unknown query; supported: count/list/get/find or SELECT ...'})