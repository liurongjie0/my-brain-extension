# SRE Oncall Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Web demo for an SRE oncall Q&A agent focused on API 5xx and latency incidents, with SOP search, mock read-only telemetry tools, structured diagnosis cards, and reliability-focused degradation behavior.

**Architecture:** Create a new isolated `sre-oncall-agent/` Python subproject. The backend is a FastAPI app with a deterministic `SREAgentOrchestrator` that parses questions, calls mock adapters, searches local knowledge docs, and returns a structured diagnosis response. The frontend is a small static chat UI served by FastAPI so the first version is easy to run and test without external services.

**Tech Stack:** Python 3.10+, FastAPI, Pydantic, pytest, httpx TestClient, vanilla HTML/CSS/JavaScript.

---

## Source Spec

Read this before implementation:

- `docs/superpowers/specs/2026-06-20-sre-oncall-agent-design.md`

## Scope Check

This plan implements one coherent MVP:

- Web chat entrypoint.
- API 5xx / latency SRE scenario.
- Local SOP knowledge base.
- Mock alert, metric, log, and service catalog tools.
- Structured diagnosis response.
- Reliability behavior for missing context and failed tools.

It intentionally does not implement real monitoring integrations, write actions, incident workbench, auth, or production deployment.

## File Structure

Create this new subproject:

```text
sre-oncall-agent/
├── README.md
├── requirements.txt
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── models.py
│   ├── agent/
│   │   ├── __init__.py
│   │   └── orchestrator.py
│   ├── adapters/
│   │   ├── __init__.py
│   │   ├── base.py
│   │   └── mock.py
│   ├── knowledge/
│   │   ├── __init__.py
│   │   ├── base.py
│   │   └── docs/
│   │       ├── checkout-api-5xx.md
│   │       └── api-latency.md
│   └── web/
│       └── static/
│           ├── app.js
│           ├── index.html
│           └── styles.css
└── tests/
    ├── test_adapters.py
    ├── test_api.py
    ├── test_knowledge.py
    ├── test_models.py
    ├── test_orchestrator.py
    └── test_web_static.py
```

Responsibilities:

- `app/models.py`: stable request, evidence, tool, and diagnosis response models.
- `app/adapters/base.py`: protocols for read-only tools.
- `app/adapters/mock.py`: deterministic mock alert, metric, log, and catalog adapters.
- `app/knowledge/base.py`: simple local Markdown knowledge search.
- `app/agent/orchestrator.py`: parse user input, call tools, combine evidence, handle degradation.
- `app/main.py`: FastAPI routes and static UI serving.
- `app/web/static/*`: single-page chat UI.

## Task 1: Project Scaffold And Models

**Files:**
- Create: `sre-oncall-agent/requirements.txt`
- Create: `sre-oncall-agent/app/__init__.py`
- Create: `sre-oncall-agent/app/models.py`
- Create: `sre-oncall-agent/tests/test_models.py`

- [ ] **Step 1: Create the dependency file**

Create `sre-oncall-agent/requirements.txt`:

```text
fastapi==0.115.6
uvicorn==0.34.0
pydantic==2.10.4
pytest==8.3.4
httpx==0.28.1
```

- [ ] **Step 2: Create the package marker**

Create `sre-oncall-agent/app/__init__.py`:

```python
"""SRE oncall agent demo package."""
```

- [ ] **Step 3: Write failing model tests**

Create `sre-oncall-agent/tests/test_models.py`:

```python
from app.models import DiagnosisResponse, EvidenceItem, ToolStatus


def test_diagnosis_response_keeps_evidence_and_boundaries():
    response = DiagnosisResponse(
        summary="checkout-api 5xx is elevated",
        confidence="medium",
        evidence=[
            EvidenceItem(
                source="metric",
                title="5xx rate elevated",
                detail="5xx rate reached 8.4% after 14:05",
                timestamp="2026-06-20T14:10:00+08:00",
            )
        ],
        next_steps=["Check payment-gateway latency"],
        escalation="Escalate to checkout owner if not recovered in 10 minutes.",
        boundaries=["No remediation action was executed."],
        tool_statuses=[
            ToolStatus(name="MetricTool", status="ok", message="mock metrics loaded")
        ],
    )

    assert response.confidence == "medium"
    assert response.evidence[0].source == "metric"
    assert response.boundaries == ["No remediation action was executed."]
```

- [ ] **Step 4: Run the model test and verify it fails**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_models.py -v
```

Expected: FAIL because `app.models` does not exist yet.

- [ ] **Step 5: Implement the models**

Create `sre-oncall-agent/app/models.py`:

```python
from typing import Literal

from pydantic import BaseModel, Field


Confidence = Literal["low", "medium", "high"]
EvidenceSource = Literal["alert", "metric", "log", "sop", "catalog"]
ToolState = Literal["ok", "failed", "degraded", "skipped"]


class ChatRequest(BaseModel):
    message: str = Field(min_length=1)
    service: str | None = None
    time_window_minutes: int = Field(default=30, ge=1, le=1440)


class ToolStatus(BaseModel):
    name: str
    status: ToolState
    message: str


class EvidenceItem(BaseModel):
    source: EvidenceSource
    title: str
    detail: str
    timestamp: str


class DiagnosisResponse(BaseModel):
    summary: str
    confidence: Confidence
    evidence: list[EvidenceItem]
    next_steps: list[str]
    escalation: str
    boundaries: list[str]
    tool_statuses: list[ToolStatus]
    needs_clarification: bool = False
    clarification_question: str | None = None
```

- [ ] **Step 6: Run the model test and verify it passes**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_models.py -v
```

Expected: PASS.

- [ ] **Step 7: Commit the scaffold and models**

Run:

```bash
git add sre-oncall-agent/requirements.txt sre-oncall-agent/app/__init__.py sre-oncall-agent/app/models.py sre-oncall-agent/tests/test_models.py
git commit -m "添加 SRE Agent 数据模型"
```

## Task 2: Mock Read-Only Tool Adapters

**Files:**
- Create: `sre-oncall-agent/app/adapters/__init__.py`
- Create: `sre-oncall-agent/app/adapters/base.py`
- Create: `sre-oncall-agent/app/adapters/mock.py`
- Create: `sre-oncall-agent/tests/test_adapters.py`

- [ ] **Step 1: Write failing adapter tests**

Create `sre-oncall-agent/tests/test_adapters.py`:

```python
from app.adapters.mock import MockSREDataSource


def test_mock_adapters_return_checkout_api_evidence():
    data_source = MockSREDataSource()

    alert = data_source.alerts.current_alert("checkout-api")
    metrics = data_source.metrics.service_metrics("checkout-api", 30)
    logs = data_source.logs.error_summary("checkout-api", 30)
    catalog = data_source.catalog.service("checkout-api")

    assert alert["status"] == "firing"
    assert metrics["five_xx_rate_percent"] == 8.4
    assert logs["top_exception"] == "UpstreamTimeoutError"
    assert catalog["owner"] == "checkout-platform"


def test_log_adapter_can_fail_for_degradation_tests():
    data_source = MockSREDataSource(fail_logs=True)

    try:
        data_source.logs.error_summary("checkout-api", 30)
    except RuntimeError as exc:
        assert "log backend unavailable" in str(exc)
    else:
        raise AssertionError("expected log adapter failure")
```

- [ ] **Step 2: Run the adapter tests and verify they fail**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_adapters.py -v
```

Expected: FAIL because `app.adapters.mock` does not exist yet.

- [ ] **Step 3: Implement adapter protocols**

Create `sre-oncall-agent/app/adapters/__init__.py`:

```python
"""Read-only data adapters for SRE evidence."""
```

Create `sre-oncall-agent/app/adapters/base.py`:

```python
from typing import Protocol


class AlertAdapter(Protocol):
    def current_alert(self, service: str) -> dict:
        """Return current alert state for a service."""


class MetricAdapter(Protocol):
    def service_metrics(self, service: str, time_window_minutes: int) -> dict:
        """Return metric summary for a service."""


class LogAdapter(Protocol):
    def error_summary(self, service: str, time_window_minutes: int) -> dict:
        """Return error log summary for a service."""


class ServiceCatalogAdapter(Protocol):
    def service(self, service: str) -> dict:
        """Return ownership and dependency metadata for a service."""
```

- [ ] **Step 4: Implement mock adapters**

Create `sre-oncall-agent/app/adapters/mock.py`:

```python
from dataclasses import dataclass


NOW = "2026-06-20T14:10:00+08:00"


class MockAlertAdapter:
    def current_alert(self, service: str) -> dict:
        if service != "checkout-api":
            return {
                "service": service,
                "status": "none",
                "name": "no-active-alert",
                "severity": "info",
                "started_at": NOW,
                "timestamp": NOW,
            }
        return {
            "service": "checkout-api",
            "status": "firing",
            "name": "checkout-api-high-5xx",
            "severity": "page",
            "started_at": "2026-06-20T14:05:00+08:00",
            "timestamp": NOW,
        }


class MockMetricAdapter:
    def service_metrics(self, service: str, time_window_minutes: int) -> dict:
        if service != "checkout-api":
            return {
                "service": service,
                "time_window_minutes": time_window_minutes,
                "five_xx_rate_percent": 0.1,
                "p95_latency_ms": 210,
                "p99_latency_ms": 450,
                "qps": 120,
                "timestamp": NOW,
            }
        return {
            "service": "checkout-api",
            "time_window_minutes": time_window_minutes,
            "five_xx_rate_percent": 8.4,
            "p95_latency_ms": 2400,
            "p99_latency_ms": 6100,
            "qps": 980,
            "timestamp": NOW,
        }


class MockLogAdapter:
    def __init__(self, should_fail: bool = False) -> None:
        self.should_fail = should_fail

    def error_summary(self, service: str, time_window_minutes: int) -> dict:
        if self.should_fail:
            raise RuntimeError("log backend unavailable")
        return {
            "service": service,
            "time_window_minutes": time_window_minutes,
            "top_exception": "UpstreamTimeoutError",
            "sample_message": "payment-gateway request timed out after 2000ms",
            "sample_trace_id": "trace-checkout-001",
            "timestamp": NOW,
        }


class MockServiceCatalogAdapter:
    def service(self, service: str) -> dict:
        if service == "checkout-api":
            return {
                "service": "checkout-api",
                "owner": "checkout-platform",
                "dependencies": ["payment-gateway", "inventory-api"],
                "escalation": "Page checkout-platform primary oncall.",
                "timestamp": NOW,
            }
        return {
            "service": service,
            "owner": "unknown",
            "dependencies": [],
            "escalation": "Escalate to SRE primary oncall.",
            "timestamp": NOW,
        }


@dataclass
class MockSREDataSource:
    fail_logs: bool = False

    def __post_init__(self) -> None:
        self.alerts = MockAlertAdapter()
        self.metrics = MockMetricAdapter()
        self.logs = MockLogAdapter(should_fail=self.fail_logs)
        self.catalog = MockServiceCatalogAdapter()
```

- [ ] **Step 5: Run adapter tests and verify they pass**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_adapters.py -v
```

Expected: PASS.

- [ ] **Step 6: Commit adapters**

Run:

```bash
git add sre-oncall-agent/app/adapters sre-oncall-agent/tests/test_adapters.py
git commit -m "添加 SRE 只读 mock 数据适配器"
```

## Task 3: Local Knowledge Base Search

**Files:**
- Create: `sre-oncall-agent/app/knowledge/__init__.py`
- Create: `sre-oncall-agent/app/knowledge/base.py`
- Create: `sre-oncall-agent/app/knowledge/docs/checkout-api-5xx.md`
- Create: `sre-oncall-agent/app/knowledge/docs/api-latency.md`
- Create: `sre-oncall-agent/tests/test_knowledge.py`

- [ ] **Step 1: Write failing knowledge tests**

Create `sre-oncall-agent/tests/test_knowledge.py`:

```python
from pathlib import Path

from app.knowledge.base import LocalKnowledgeBase


def test_search_returns_checkout_sop_with_source():
    docs_dir = Path("app/knowledge/docs")
    kb = LocalKnowledgeBase(docs_dir)

    results = kb.search("checkout-api 5xx")

    assert results
    assert results[0].title == "checkout-api 5xx 排查 SOP"
    assert results[0].source_path.endswith("checkout-api-5xx.md")
    assert "payment-gateway" in results[0].content
```

- [ ] **Step 2: Run the knowledge test and verify it fails**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_knowledge.py -v
```

Expected: FAIL because knowledge files do not exist yet.

- [ ] **Step 3: Add sample SOP documents**

Create `sre-oncall-agent/app/knowledge/docs/checkout-api-5xx.md`:

```markdown
# checkout-api 5xx 排查 SOP

适用场景：checkout-api 出现 5xx rate 升高、用户下单失败、接口超时。

优先检查：

1. 查看 checkout-api 当前 5xx rate 和 P95/P99 延迟。
2. 查看错误日志 Top exception。如果出现 UpstreamTimeoutError，优先检查下游 payment-gateway。
3. 检查最近 30 分钟 checkout-api 和 payment-gateway 是否有发布。
4. 如果 10 分钟内未恢复，升级 checkout-platform primary oncall。

安全边界：本 SOP 只提供排查建议，不要求自动回滚、重启或扩容。
```

Create `sre-oncall-agent/app/knowledge/docs/api-latency.md`:

```markdown
# API 延迟升高排查 SOP

适用场景：API P95 或 P99 延迟明显高于基线。

优先检查：

1. 区分是 QPS 增长、下游超时、数据库慢查询还是实例资源饱和。
2. 对比延迟升高时间点和发布、告警、依赖错误率。
3. 如果日志样本不足，先扩大时间窗口或查询 trace sample。

安全边界：高风险操作必须由值班负责人确认。
```

- [ ] **Step 4: Implement local knowledge search**

Create `sre-oncall-agent/app/knowledge/__init__.py`:

```python
"""Local SOP knowledge search."""
```

Create `sre-oncall-agent/app/knowledge/base.py`:

```python
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class KnowledgeHit:
    title: str
    content: str
    source_path: str
    score: int


class LocalKnowledgeBase:
    def __init__(self, docs_dir: Path) -> None:
        self.docs_dir = docs_dir

    def search(self, query: str, limit: int = 3) -> list[KnowledgeHit]:
        query_terms = [term.lower() for term in query.replace("/", " ").split() if term]
        hits: list[KnowledgeHit] = []

        for path in sorted(self.docs_dir.glob("*.md")):
            content = path.read_text(encoding="utf-8")
            title = self._extract_title(content, path)
            searchable = f"{title}\n{content}".lower()
            score = sum(1 for term in query_terms if term in searchable)
            if score > 0:
                hits.append(
                    KnowledgeHit(
                        title=title,
                        content=content,
                        source_path=str(path),
                        score=score,
                    )
                )

        return sorted(hits, key=lambda hit: hit.score, reverse=True)[:limit]

    @staticmethod
    def _extract_title(content: str, path: Path) -> str:
        for line in content.splitlines():
            if line.startswith("# "):
                return line.removeprefix("# ").strip()
        return path.stem
```

- [ ] **Step 5: Run knowledge tests and verify they pass**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_knowledge.py -v
```

Expected: PASS.

- [ ] **Step 6: Commit knowledge base**

Run:

```bash
git add sre-oncall-agent/app/knowledge sre-oncall-agent/tests/test_knowledge.py
git commit -m "添加 SRE SOP 本地检索"
```

## Task 4: SRE Agent Orchestrator

**Files:**
- Create: `sre-oncall-agent/app/agent/__init__.py`
- Create: `sre-oncall-agent/app/agent/orchestrator.py`
- Create: `sre-oncall-agent/tests/test_orchestrator.py`

- [ ] **Step 1: Write failing orchestrator tests**

Create `sre-oncall-agent/tests/test_orchestrator.py`:

```python
from pathlib import Path

from app.adapters.mock import MockSREDataSource
from app.agent.orchestrator import SREAgentOrchestrator
from app.knowledge.base import LocalKnowledgeBase


def build_orchestrator(fail_logs: bool = False) -> SREAgentOrchestrator:
    return SREAgentOrchestrator(
        data_source=MockSREDataSource(fail_logs=fail_logs),
        knowledge_base=LocalKnowledgeBase(Path("app/knowledge/docs")),
    )


def test_checkout_5xx_question_returns_evidence_backed_diagnosis():
    orchestrator = build_orchestrator()

    response = orchestrator.answer("checkout-api 5xx 升高怎么办？")

    assert response.needs_clarification is False
    assert response.confidence == "medium"
    assert "checkout-api" in response.summary
    assert {item.source for item in response.evidence} >= {"alert", "metric", "log", "sop"}
    assert any("No remediation action was executed" in item for item in response.boundaries)


def test_missing_service_name_asks_clarifying_question():
    orchestrator = build_orchestrator()

    response = orchestrator.answer("5xx 升高怎么办？")

    assert response.needs_clarification is True
    assert response.clarification_question == "你想排查哪个服务？例如 checkout-api。"
    assert response.evidence == []


def test_log_failure_degrades_without_inventing_log_evidence():
    orchestrator = build_orchestrator(fail_logs=True)

    response = orchestrator.answer("checkout-api 5xx 升高怎么办？")

    assert response.needs_clarification is False
    assert "log" not in {item.source for item in response.evidence}
    assert any(status.name == "LogTool" and status.status == "failed" for status in response.tool_statuses)
    assert any("LogTool failed" in boundary for boundary in response.boundaries)
```

- [ ] **Step 2: Run orchestrator tests and verify they fail**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_orchestrator.py -v
```

Expected: FAIL because `app.agent.orchestrator` does not exist yet.

- [ ] **Step 3: Implement agent package marker**

Create `sre-oncall-agent/app/agent/__init__.py`:

```python
"""Agent orchestration for SRE diagnosis."""
```

- [ ] **Step 4: Implement orchestrator**

Create `sre-oncall-agent/app/agent/orchestrator.py`:

```python
from app.adapters.mock import MockSREDataSource
from app.knowledge.base import LocalKnowledgeBase
from app.models import DiagnosisResponse, EvidenceItem, ToolStatus


class SREAgentOrchestrator:
    def __init__(self, data_source: MockSREDataSource, knowledge_base: LocalKnowledgeBase) -> None:
        self.data_source = data_source
        self.knowledge_base = knowledge_base

    def answer(self, message: str, service: str | None = None, time_window_minutes: int = 30) -> DiagnosisResponse:
        service_name = service or self._extract_service(message)
        if service_name is None:
            return DiagnosisResponse(
                summary="需要补充服务名后才能开始排查。",
                confidence="low",
                evidence=[],
                next_steps=[],
                escalation="如果这是正在影响用户的故障，请先通知 SRE primary oncall。",
                boundaries=["No remediation action was executed."],
                tool_statuses=[],
                needs_clarification=True,
                clarification_question="你想排查哪个服务？例如 checkout-api。",
            )

        evidence: list[EvidenceItem] = []
        statuses: list[ToolStatus] = []
        boundaries = ["No remediation action was executed."]

        alert = self.data_source.alerts.current_alert(service_name)
        statuses.append(ToolStatus(name="AlertTool", status="ok", message="alert evidence loaded"))
        evidence.append(
            EvidenceItem(
                source="alert",
                title=alert["name"],
                detail=f"{alert['service']} alert status is {alert['status']} with severity {alert['severity']}.",
                timestamp=alert["timestamp"],
            )
        )

        metrics = self.data_source.metrics.service_metrics(service_name, time_window_minutes)
        statuses.append(ToolStatus(name="MetricTool", status="ok", message="metric evidence loaded"))
        evidence.append(
            EvidenceItem(
                source="metric",
                title="5xx and latency summary",
                detail=(
                    f"5xx rate is {metrics['five_xx_rate_percent']}%, "
                    f"P95 latency is {metrics['p95_latency_ms']}ms, "
                    f"P99 latency is {metrics['p99_latency_ms']}ms."
                ),
                timestamp=metrics["timestamp"],
            )
        )

        try:
            logs = self.data_source.logs.error_summary(service_name, time_window_minutes)
        except RuntimeError as exc:
            statuses.append(ToolStatus(name="LogTool", status="failed", message=str(exc)))
            boundaries.append("LogTool failed; response is based on alert, metric, catalog, and SOP evidence only.")
        else:
            statuses.append(ToolStatus(name="LogTool", status="ok", message="log evidence loaded"))
            evidence.append(
                EvidenceItem(
                    source="log",
                    title=logs["top_exception"],
                    detail=f"{logs['sample_message']} Trace sample: {logs['sample_trace_id']}.",
                    timestamp=logs["timestamp"],
                )
            )

        catalog = self.data_source.catalog.service(service_name)
        statuses.append(ToolStatus(name="ServiceCatalogTool", status="ok", message="catalog evidence loaded"))
        evidence.append(
            EvidenceItem(
                source="catalog",
                title=f"{service_name} ownership",
                detail=f"Owner is {catalog['owner']}. Dependencies: {', '.join(catalog['dependencies']) or 'none'}.",
                timestamp=catalog["timestamp"],
            )
        )

        hits = self.knowledge_base.search(f"{service_name} 5xx latency", limit=1)
        if hits:
            hit = hits[0]
            statuses.append(ToolStatus(name="KnowledgeBase", status="ok", message=hit.source_path))
            evidence.append(
                EvidenceItem(
                    source="sop",
                    title=hit.title,
                    detail="SOP recommends checking 5xx rate, latency, top exception, recent deploys, and downstream dependencies.",
                    timestamp="local-doc",
                )
            )
        else:
            statuses.append(ToolStatus(name="KnowledgeBase", status="degraded", message="no SOP matched"))
            boundaries.append("No matching SOP was found.")

        confidence = "medium" if any(item.source == "log" for item in evidence) else "low"
        summary = (
            f"{service_name} is showing elevated error or latency symptoms. "
            "The strongest current hypothesis is downstream timeout pressure; validate with dependency metrics before declaring root cause."
        )

        return DiagnosisResponse(
            summary=summary,
            confidence=confidence,
            evidence=evidence,
            next_steps=[
                "Check downstream dependency latency and error rate for payment-gateway.",
                "Compare the incident start time with recent deploys in the last 30 minutes.",
                "If symptoms persist for 10 minutes, escalate to the service owner.",
            ],
            escalation=catalog["escalation"],
            boundaries=boundaries,
            tool_statuses=statuses,
        )

    @staticmethod
    def _extract_service(message: str) -> str | None:
        known_services = ["checkout-api"]
        for service in known_services:
            if service in message:
                return service
        return None
```

- [ ] **Step 5: Run orchestrator tests and verify they pass**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_orchestrator.py -v
```

Expected: PASS.

- [ ] **Step 6: Run all backend unit tests**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_models.py tests/test_adapters.py tests/test_knowledge.py tests/test_orchestrator.py -v
```

Expected: PASS.

- [ ] **Step 7: Commit orchestrator**

Run:

```bash
git add sre-oncall-agent/app/agent sre-oncall-agent/tests/test_orchestrator.py
git commit -m "添加 SRE Agent 编排器"
```

## Task 5: FastAPI API And Static Serving

**Files:**
- Create: `sre-oncall-agent/app/main.py`
- Create: `sre-oncall-agent/tests/test_api.py`

- [ ] **Step 1: Write failing API tests**

Create `sre-oncall-agent/tests/test_api.py`:

```python
from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_chat_endpoint_returns_diagnosis_card_payload():
    response = client.post("/api/chat", json={"message": "checkout-api 5xx 升高怎么办？"})

    assert response.status_code == 200
    payload = response.json()
    assert payload["needs_clarification"] is False
    assert "checkout-api" in payload["summary"]
    assert {"alert", "metric", "log", "sop"} <= {item["source"] for item in payload["evidence"]}


def test_chat_endpoint_can_simulate_log_failure():
    response = client.post(
        "/api/chat?fail_logs=true",
        json={"message": "checkout-api 5xx 升高怎么办？"},
    )

    assert response.status_code == 200
    payload = response.json()
    assert "log" not in {item["source"] for item in payload["evidence"]}
    assert any(status["name"] == "LogTool" and status["status"] == "failed" for status in payload["tool_statuses"])


def test_index_page_is_served():
    response = client.get("/")

    assert response.status_code == 200
    assert "SRE Oncall Agent" in response.text
```

- [ ] **Step 2: Run API tests and verify they fail**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_api.py -v
```

Expected: FAIL because `app.main` and static files do not exist yet.

- [ ] **Step 3: Implement FastAPI app**

Create `sre-oncall-agent/app/main.py`:

```python
from pathlib import Path

from fastapi import FastAPI, Query
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.adapters.mock import MockSREDataSource
from app.agent.orchestrator import SREAgentOrchestrator
from app.knowledge.base import LocalKnowledgeBase
from app.models import ChatRequest, DiagnosisResponse


BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "web" / "static"
DOCS_DIR = BASE_DIR / "knowledge" / "docs"

app = FastAPI(title="SRE Oncall Agent")
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")


def build_orchestrator(fail_logs: bool = False) -> SREAgentOrchestrator:
    return SREAgentOrchestrator(
        data_source=MockSREDataSource(fail_logs=fail_logs),
        knowledge_base=LocalKnowledgeBase(DOCS_DIR),
    )


@app.get("/")
def index() -> FileResponse:
    return FileResponse(STATIC_DIR / "index.html")


@app.post("/api/chat", response_model=DiagnosisResponse)
def chat(request: ChatRequest, fail_logs: bool = Query(default=False)) -> DiagnosisResponse:
    orchestrator = build_orchestrator(fail_logs=fail_logs)
    return orchestrator.answer(
        message=request.message,
        service=request.service,
        time_window_minutes=request.time_window_minutes,
    )
```

- [ ] **Step 4: Add minimal static files for API test**

Create `sre-oncall-agent/app/web/static/index.html`:

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>SRE Oncall Agent</title>
    <link rel="stylesheet" href="/static/styles.css" />
  </head>
  <body>
    <main class="app-shell">
      <section class="chat-panel">
        <header>
          <p class="eyebrow">SRE Oncall Agent</p>
          <h1>值班问题答疑</h1>
        </header>
        <form id="chat-form">
          <textarea id="message" name="message">checkout-api 5xx 升高怎么办？</textarea>
          <button type="submit">发送</button>
        </form>
        <div id="result" class="result" aria-live="polite"></div>
      </section>
    </main>
    <script src="/static/app.js"></script>
  </body>
</html>
```

Create `sre-oncall-agent/app/web/static/styles.css`:

```css
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  background: #f6f7f9;
  color: #17202a;
}

.app-shell {
  max-width: 980px;
  margin: 0 auto;
  padding: 32px 20px;
}

.chat-panel {
  background: #ffffff;
  border: 1px solid #d9dee7;
  border-radius: 8px;
  padding: 24px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #596579;
  font-size: 13px;
}

h1 {
  margin: 0 0 20px;
  font-size: 28px;
}

textarea {
  width: 100%;
  min-height: 88px;
  box-sizing: border-box;
}

button {
  margin-top: 12px;
  padding: 10px 16px;
}

.result {
  margin-top: 20px;
}
```

Create `sre-oncall-agent/app/web/static/app.js`:

```javascript
const form = document.querySelector("#chat-form");
const messageInput = document.querySelector("#message");
const result = document.querySelector("#result");

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  result.textContent = "查询中...";

  const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message: messageInput.value }),
  });

  const payload = await response.json();
  result.innerHTML = `<pre>${JSON.stringify(payload, null, 2)}</pre>`;
});
```

- [ ] **Step 5: Run API tests and verify they pass**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_api.py -v
```

Expected: PASS.

- [ ] **Step 6: Commit API and static serving**

Run:

```bash
git add sre-oncall-agent/app/main.py sre-oncall-agent/app/web/static sre-oncall-agent/tests/test_api.py
git commit -m "添加 SRE Agent Web API"
```

## Task 6: Diagnosis Card Web UI

**Files:**
- Modify: `sre-oncall-agent/app/web/static/index.html`
- Modify: `sre-oncall-agent/app/web/static/styles.css`
- Modify: `sre-oncall-agent/app/web/static/app.js`
- Create: `sre-oncall-agent/tests/test_web_static.py`

- [ ] **Step 1: Write failing static UI tests**

Create `sre-oncall-agent/tests/test_web_static.py`:

```python
from pathlib import Path


STATIC_DIR = Path("app/web/static")


def test_static_ui_contains_diagnosis_sections():
    html = (STATIC_DIR / "index.html").read_text(encoding="utf-8")
    js = (STATIC_DIR / "app.js").read_text(encoding="utf-8")

    assert "diagnosis-card" in html
    assert "renderDiagnosis" in js
    assert "初步判断" in js
    assert "证据" in js
    assert "下一步" in js
    assert "边界" in js
```

- [ ] **Step 2: Run static UI test and verify it fails**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_web_static.py -v
```

Expected: FAIL because current UI only renders raw JSON.

- [ ] **Step 3: Replace `index.html` with diagnosis-card layout**

Modify `sre-oncall-agent/app/web/static/index.html`:

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>SRE Oncall Agent</title>
    <link rel="stylesheet" href="/static/styles.css" />
  </head>
  <body>
    <main class="app-shell">
      <section class="chat-panel">
        <header class="page-header">
          <p class="eyebrow">SRE Oncall Agent</p>
          <h1>值班问题答疑</h1>
          <p class="subtitle">API 5xx / 延迟升高场景，只读查询 mock 告警、指标、日志和 SOP。</p>
        </header>

        <form id="chat-form" class="query-form">
          <label for="message">问题</label>
          <textarea id="message" name="message">checkout-api 5xx 升高怎么办？</textarea>
          <div class="form-row">
            <label class="checkbox">
              <input id="fail-logs" type="checkbox" />
              模拟日志工具失败
            </label>
            <button type="submit">发送</button>
          </div>
        </form>

        <section id="diagnosis-card" class="diagnosis-card" aria-live="polite">
          <p class="empty-state">发送一个值班问题后，这里会展示诊断卡。</p>
        </section>
      </section>
    </main>
    <script src="/static/app.js"></script>
  </body>
</html>
```

- [ ] **Step 4: Replace `app.js` with structured rendering**

Modify `sre-oncall-agent/app/web/static/app.js`:

```javascript
const form = document.querySelector("#chat-form");
const messageInput = document.querySelector("#message");
const failLogsInput = document.querySelector("#fail-logs");
const diagnosisCard = document.querySelector("#diagnosis-card");

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  diagnosisCard.innerHTML = '<p class="empty-state">查询中...</p>';

  const endpoint = failLogsInput.checked ? "/api/chat?fail_logs=true" : "/api/chat";
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message: messageInput.value }),
  });

  const payload = await response.json();
  renderDiagnosis(payload);
});

function renderDiagnosis(payload) {
  if (payload.needs_clarification) {
    diagnosisCard.innerHTML = `
      <h2>需要补充信息</h2>
      <p>${escapeHtml(payload.clarification_question)}</p>
    `;
    return;
  }

  diagnosisCard.innerHTML = `
    <h2>初步判断</h2>
    <p>${escapeHtml(payload.summary)}</p>
    <p class="confidence">置信度：${escapeHtml(payload.confidence)}</p>
    <h3>证据</h3>
    <ul>${payload.evidence.map(renderEvidence).join("")}</ul>
    <h3>下一步</h3>
    <ol>${payload.next_steps.map((step) => `<li>${escapeHtml(step)}</li>`).join("")}</ol>
    <h3>升级建议</h3>
    <p>${escapeHtml(payload.escalation)}</p>
    <h3>边界</h3>
    <ul>${payload.boundaries.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
    <h3>工具状态</h3>
    <ul>${payload.tool_statuses.map(renderToolStatus).join("")}</ul>
  `;
}

function renderEvidence(item) {
  return `
    <li>
      <strong>[${escapeHtml(item.source)}] ${escapeHtml(item.title)}</strong>
      <p>${escapeHtml(item.detail)}</p>
      <span>${escapeHtml(item.timestamp)}</span>
    </li>
  `;
}

function renderToolStatus(status) {
  return `
    <li>
      <strong>${escapeHtml(status.name)}:</strong>
      ${escapeHtml(status.status)}，${escapeHtml(status.message)}
    </li>
  `;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
```

- [ ] **Step 5: Replace `styles.css` with diagnosis-card styling**

Modify `sre-oncall-agent/app/web/static/styles.css`:

```css
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  background: #f6f7f9;
  color: #17202a;
}

.app-shell {
  max-width: 980px;
  margin: 0 auto;
  padding: 32px 20px;
}

.chat-panel {
  background: #ffffff;
  border: 1px solid #d9dee7;
  border-radius: 8px;
  padding: 24px;
}

.page-header {
  border-bottom: 1px solid #e7ebf1;
  margin-bottom: 20px;
  padding-bottom: 16px;
}

.eyebrow,
.subtitle,
.confidence,
.empty-state {
  color: #596579;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 13px;
}

h1 {
  margin: 0 0 8px;
  font-size: 28px;
}

label {
  display: block;
  font-weight: 600;
  margin-bottom: 8px;
}

textarea {
  width: 100%;
  min-height: 88px;
  box-sizing: border-box;
  border: 1px solid #c9d1dd;
  border-radius: 6px;
  padding: 10px;
}

.form-row {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.checkbox {
  align-items: center;
  display: flex;
  font-weight: 400;
  gap: 8px;
  margin: 0;
}

button {
  background: #1d4ed8;
  border: 0;
  border-radius: 6px;
  color: #ffffff;
  cursor: pointer;
  padding: 10px 16px;
}

.diagnosis-card {
  border: 1px solid #d9dee7;
  border-radius: 8px;
  margin-top: 20px;
  padding: 18px;
}

.diagnosis-card h2,
.diagnosis-card h3 {
  margin-bottom: 8px;
}

.diagnosis-card li {
  margin-bottom: 10px;
}

.diagnosis-card span {
  color: #596579;
  font-size: 13px;
}
```

- [ ] **Step 6: Run static UI tests and API tests**

Run:

```bash
cd sre-oncall-agent
pytest tests/test_web_static.py tests/test_api.py -v
```

Expected: PASS.

- [ ] **Step 7: Commit UI**

Run:

```bash
git add sre-oncall-agent/app/web/static sre-oncall-agent/tests/test_web_static.py
git commit -m "添加 SRE Agent 诊断卡界面"
```

## Task 7: End-To-End Verification And README

**Files:**
- Create: `sre-oncall-agent/README.md`

- [ ] **Step 1: Add README**

Create `sre-oncall-agent/README.md`:

```markdown
# SRE Oncall Agent

Web 版 SRE 值班答疑 Agent demo。第一版聚焦 API 服务 5xx / 延迟升高场景，使用本地 SOP 和 mock 只读数据源生成结构化诊断卡。

## 功能

- Web 聊天入口。
- `checkout-api 5xx 升高怎么办？` 示例排查。
- mock 告警、指标、日志和服务目录查询。
- SOP 本地检索。
- 工具失败降级展示。
- 只读建议，不执行修复动作。

## 启动

```bash
cd sre-oncall-agent
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

打开 `http://127.0.0.1:8000`。

## 测试

```bash
cd sre-oncall-agent
pytest -v
```

## 验收问题

```text
checkout-api 5xx 升高怎么办？
```

预期：页面展示初步判断、告警证据、指标证据、日志证据、SOP 证据、下一步排查、升级建议和边界说明。
```

- [ ] **Step 2: Run all tests**

Run:

```bash
cd sre-oncall-agent
pytest -v
```

Expected: all tests PASS.

- [ ] **Step 3: Run the app locally**

Run:

```bash
cd sre-oncall-agent
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Expected: server starts and logs `Uvicorn running on http://127.0.0.1:8000`.

- [ ] **Step 4: Verify API manually**

In another terminal, run:

```bash
curl -s http://127.0.0.1:8000/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"checkout-api 5xx 升高怎么办？"}'
```

Expected: JSON response has `needs_clarification: false` and evidence sources include `alert`, `metric`, `log`, and `sop`.

- [ ] **Step 5: Verify degraded API manually**

Run:

```bash
curl -s 'http://127.0.0.1:8000/api/chat?fail_logs=true' \
  -H 'Content-Type: application/json' \
  -d '{"message":"checkout-api 5xx 升高怎么办？"}'
```

Expected: JSON response has no `log` evidence and includes a `LogTool` status of `failed`.

- [ ] **Step 6: Commit README and final verification**

Run:

```bash
git add sre-oncall-agent/README.md
git commit -m "添加 SRE Agent 使用说明"
```

## Final Verification Checklist

Before declaring implementation complete, run:

```bash
cd sre-oncall-agent
pytest -v
```

Expected: all tests PASS.

Then start the server:

```bash
cd sre-oncall-agent
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Open `http://127.0.0.1:8000` and verify:

- The page loads.
- Submitting `checkout-api 5xx 升高怎么办？` renders a diagnosis card.
- The diagnosis card includes alert, metric, log, SOP evidence, next steps, escalation, and boundaries.
- Enabling log failure shows degraded behavior without log evidence.

## Self-Review Result

- Spec coverage: Covered Web entrypoint, API 5xx / latency scenario, SOP retrieval, mock read-only tools, diagnosis card, degradation behavior, and testing layers.
- Placeholder scan: No unresolved markers, vague edge handling, or missing code steps remain in this plan.
- Type consistency: `DiagnosisResponse`, `EvidenceItem`, `ToolStatus`, adapter names, and route payload names are consistent across tasks.
