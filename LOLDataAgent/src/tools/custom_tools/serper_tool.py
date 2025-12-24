from langchain.tools import tool
import httpx
import os
import json

SERPER_BASE = os.getenv('SERPER_BASE_URL', 'https://google.serper.dev')
SERPER_KEY = os.getenv('SERPER_API_KEY')

@tool
def serper_search(query: str) -> str:
    """
    使用 Serper (Google search) API 简单搜索并返回摘要文本。
    """
    if not SERPER_KEY:
        return 'SERPER_API_KEY not configured'

    url = f"{SERPER_BASE}/search"
    headers = {'X-API-KEY': SERPER_KEY}
    try:
        with httpx.Client(timeout=10.0) as client:
            r = client.get(url, params={'q': query}, headers=headers)
            r.raise_for_status()
            j = r.json()
            # Try to extract useful fields, adapt to Serper responses
            if 'answer' in j and j['answer']:
                return j['answer']
            if 'organic' in j and isinstance(j['organic'], list):
                snippets = [o.get('snippet') for o in j['organic'] if o.get('snippet')]
                return '\n'.join(snippets[:3]) if snippets else json.dumps(j)
            # fallback to full json
            return json.dumps(j)
    except Exception as e:
        return f'serper error: {e}'