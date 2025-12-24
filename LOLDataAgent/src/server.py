from fastapi import FastAPI, Request, Header, HTTPException
from fastapi.responses import JSONResponse, FileResponse, HTMLResponse
from sse_starlette.sse import EventSourceResponse
import asyncio, os, json, time
from pydantic import BaseModel
from typing import Optional
from src.agents.base_agent import BaseAgent
from src.agents.sql_agent import SQLAgent
from src.agents.orchestrator import Orchestrator
from src.tools.tool_registry import get_all_tools
from config import settings

app = FastAPI(title="LOLDataAgent API")

# singletons
base_agent = None
sql_agent = None
orchestrator = None

# ensure data directories
os.makedirs(os.path.join(os.getcwd(), 'data', 'reports'), exist_ok=True)

class RunRequest(BaseModel):
    agent: str = "base"
    query: str
    mode: Optional[str] = "simple"
    sessionId: Optional[str] = None
    traceId: Optional[str] = None
    context: Optional[dict] = None
    reportConfig: Optional[dict] = None


@app.on_event("startup")
async def startup_event():
    global base_agent, sql_agent, orchestrator
    base_agent = BaseAgent()
    sql_agent = SQLAgent()
    orchestrator = Orchestrator()


@app.get("/api/v1/ai/health")
async def health():
    return {"status": "ok"}

@app.get("/")
async def index():
        html = """<!doctype html>
<html>
<head>
    <meta charset='utf-8' />
    <title>LOLDataAgent Tester</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 24px; }
        .row { margin-bottom: 12px; }
        textarea { width: 100%; height: 120px; }
        pre { background: #f6f6f6; padding: 12px; border: 1px solid #ddd; overflow: auto; }
        button { margin-right: 8px; padding: 6px 10px; }
        .flex { display: flex; gap: 12px; flex-wrap: wrap; }
        .col { flex: 1 1 320px; }
    </style>
</head>
<body>
    <h2>LOLDataAgent Test Page</h2>
    <div class='row'>
        <a href='/api/v1/ai/health' target='_blank'>Health</a> |
        <a href='/api/v1/ai/tools' target='_blank'>Tools</a>
    </div>
    <div class='flex'>
        <div class='col'>
            <div class='row'>Mode:
                <select id='mode'>
                    <option value='simple'>simple</option>
                    <option value='report'>report</option>
                </select>
            </div>
            <div class='row'>Query:</div>
            <div class='row'><textarea id='query'>亚索的背景故事是什么？</textarea></div>
            <div class='row'>
                <button id='btnStream'>Stream (SSE)</button>
                <button id='btnQuery'>Non-stream (JSON)</button>
                <button id='btnClear'>Clear Output</button>
            </div>
        </div>
        <div class='col'>
            <div class='row'>Stream Output (tokens/steps/file_meta):</div>
            <pre id='streamOut'></pre>
        </div>
        <div class='col'>
            <div class='row'>Non-stream Response:</div>
            <pre id='jsonOut'></pre>
        </div>
    </div>

<script>
    function clearOut() {
        document.getElementById('streamOut').textContent = '';
        document.getElementById('jsonOut').textContent = '';
    }

    async function runStream() {
        clearOut();
        const mode = document.getElementById('mode').value;
        const query = document.getElementById('query').value;
        const body = { mode, query };
        const resp = await fetch('/api/v1/ai/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const reader = resp.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const events = buffer.split("\\n\\n");
            buffer = events.pop();
            for (const ev of events) {
                const lines = ev.split("\\n");
                let event = 'message';
                let data = '';
                for (const line of lines) {
                    if (line.startsWith('event:')) event = line.slice(6).trim();
                    if (line.startsWith('data:')) data += line.slice(5).trim();
                }
                appendStream(event, data);
            }
        }
    }

    async function runQuery() {
        const mode = document.getElementById('mode').value;
        const query = document.getElementById('query').value;
        const body = { mode, query };
        const resp = await fetch('/api/v1/ai/chat/query', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const json = await resp.json();
        document.getElementById('jsonOut').textContent = JSON.stringify(json, null, 2);
    }

    function appendStream(event, data) {
        const out = document.getElementById('streamOut');
        out.textContent += `[${event}] ${data}\\n`;
    }

    document.getElementById('btnStream').addEventListener('click', runStream);
    document.getElementById('btnQuery').addEventListener('click', runQuery);
    document.getElementById('btnClear').addEventListener('click', clearOut);
</script>

</body>
</html>
"""
        return HTMLResponse(content=html)


@app.get("/api/v1/ai/tools")
async def tools():
    tools = get_all_tools()
    return {"tools": [t.__name__ for t in tools]}


async def simple_stream_generator(request: Request, payload: dict):
    # start meta
    meta = {
        "traceId": payload.get('traceId'),
        "sessionId": payload.get('sessionId'),
        "model": getattr(orchestrator, 'model_name', 'qwen-plus'),
        "mode": payload.get('mode', 'simple'),
        "startedAt": time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
    }
    print(f"[SSE][meta] {meta}")
    yield f"event: meta\ndata: {json.dumps(meta)}\n\n"

    query = payload.get('query')
    mode = payload.get('mode', 'simple')
    def run_agent():
        return orchestrator.run(query=query, mode=mode, context=payload.get('context'), report_config=payload.get('reportConfig'))

    resp = await asyncio.to_thread(run_agent)

    steps = resp.get('steps', [])
    answer = resp.get('answer', '')
    for step in steps:
        evt = {"type": "step", "detail": step}
        print(f"[SSE][step] {evt}")
        yield f"event: data\ndata: {json.dumps(evt, ensure_ascii=False)}\n\n"
    chunk_size = 200
    for i in range(0, len(answer), chunk_size):
        if await request.is_disconnected():
            break
        delta = answer[i:i+chunk_size]
        print(f"[SSE][token] {delta}")
        yield f"event: token\ndata: {json.dumps({'delta': delta}, ensure_ascii=False)}\n\n"
        await asyncio.sleep(0.02)
    if mode == 'report':
        fname = f"report_{int(time.time())}.md"
        path = os.path.join('data', 'reports', fname)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(answer)
        file_meta = {
            'fileId': fname,
            'fileName': fname,
            'fileType': 'markdown',
            'size': os.path.getsize(path)
        }
        print(f"[SSE][file_meta] {file_meta}")
        yield f"event: file_meta\ndata: {json.dumps(file_meta)}\n\n"

    done_evt = {"ok": True, "traceId": payload.get('traceId')}
    print(f"[SSE][done] {done_evt}")
    yield f"event: done\ndata: {json.dumps(done_evt)}\n\n"


@app.post("/api/v1/ai/chat/stream")
async def stream(request: Request, payload: RunRequest, x_ai_api_key: str = Header(None), authorization: str = Header(None)):
    # Auth disabled per request: accept all callers
    return EventSourceResponse(simple_stream_generator(request, payload.dict()))


@app.post("/api/v1/ai/chat/query")
async def query(req: RunRequest, x_ai_api_key: str = Header(None)):
    # Auth disabled per request: accept all callers

    resp = await asyncio.to_thread(orchestrator.run, req.query, req.mode, req.context, req.reportConfig)
    answer = resp.get('answer')
    steps = resp.get('steps')
    result = {
        'sessionId': req.sessionId,
        'traceId': req.traceId,
        'mode': req.mode,
        'answer': answer,
        'durationMs': 0,
        'model': resp.get('model') or getattr(orchestrator, 'model_name', 'qwen-plus'),
        'steps': steps
    }

    # if report mode, create file
    if req.mode == 'report':
        fname = f"report_{int(time.time())}.md"
        path = os.path.join('data', 'reports', fname)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(answer)
        result['reportMeta'] = {
            'fileId': fname,
            'fileName': fname,
            'fileType': 'markdown',
            'size': os.path.getsize(path)
        }

    return JSONResponse(result)


@app.get("/api/v1/ai/files/{fileId}")
async def get_file(fileId: str, x_ai_api_key: str = Header(None)):
    # Auth disabled per request: accept all callers

    path = os.path.join('data', 'reports', fileId)
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail='file not found')
    return FileResponse(path, media_type='application/octet-stream', filename=fileId)
