import os
import asyncio
import pytest
from httpx import AsyncClient

os.environ['AI_API_KEY'] = 'test_key'

from src.server import app

@pytest.mark.asyncio
async def test_health():
    async with AsyncClient(app=app, base_url='http://test') as ac:
        r = await ac.get('/api/v1/ai/health')
        assert r.status_code == 200
        assert r.json() == {'status': 'ok'}

@pytest.mark.asyncio
async def test_tools():
    async with AsyncClient(app=app, base_url='http://test') as ac:
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
    async with AsyncClient(app=app, base_url='http://test') as ac:
        r = await ac.post('/api/v1/ai/chat/query', json=payload, headers=headers)
        assert r.status_code == 200
        j = r.json()
        assert 'answer' in j
