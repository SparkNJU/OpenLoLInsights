import os
import asyncio
import pytest
import contextlib
from httpx import AsyncClient, ASGITransport

os.environ['AI_API_KEY'] = 'test_key'

from src.server import app


@contextlib.asynccontextmanager
async def _client():
    # httpx>=0.28 removed AsyncClient(app=...). We also need to trigger FastAPI startup.
    await app.router.startup()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url='http://test') as ac:
        yield ac
    await app.router.shutdown()

@pytest.mark.asyncio
async def test_health():
    async with _client() as ac:
        r = await ac.get('/api/v1/ai/health')
        assert r.status_code == 200
        assert r.json() == {'status': 'ok'}

@pytest.mark.asyncio
async def test_tools():
    async with _client() as ac:
        r = await ac.get('/api/v1/ai/tools')
        assert r.status_code == 200
        assert 'tools' in r.json()

@pytest.mark.asyncio
async def test_query_simple():
    payload = {
        'agent': 'base',
        'query': '亚索是谁',
    }
    headers = {'X-AI-API-Key': 'test_key'}
    async with _client() as ac:
        r = await ac.post('/api/v1/ai/chat/query', json=payload, headers=headers)
        assert r.status_code == 200
        j = r.json()
        assert 'answer' in j
