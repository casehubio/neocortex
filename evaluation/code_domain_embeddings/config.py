# evaluation/code_domain_embeddings/config.py
"""Shared configuration for code-domain embedding evaluation."""

import os
from pathlib import Path

MODEL_REGISTRY = {
    # Baselines
    "nomic-embed-text": {
        "hf_id": "nomic-ai/nomic-embed-text-v1",
        "role": "baseline",
        "params": "137M",
        "dims": 768,
        "notes": "Current production model (Ollama). BERT WordPiece tokenizer.",
    },
    "BGE-M3": {
        "hf_id": "BAAI/bge-m3",
        "role": "baseline",
        "params": "568M",
        "dims": 1024,
        "notes": "Planned replacement (#30). XLM-RoBERTa BPE. Dense+sparse+ColBERT.",
    },
    # Candidates
    "CodeBERT": {
        "hf_id": "microsoft/codebert-base",
        "role": "candidate",
        "params": "125M",
        "dims": 768,
        "notes": "Pre-trained on CodeSearchNet (6 languages). RoBERTa BPE.",
    },
    "UniXcoder": {
        "hf_id": "microsoft/unixcoder-base",
        "role": "candidate",
        "params": "125M",
        "dims": 768,
        "notes": "Code + NL pairs. RoBERTa BPE.",
    },
    "jina-code": {
        "hf_id": "jinaai/jina-embeddings-v2-base-code",
        "role": "candidate",
        "params": "161M",
        "dims": 768,
        "notes": "150M code pairs. JinaBERT ALiBi. 8192 context.",
    },
    "nomic-v1.5": {
        "hf_id": "nomic-ai/nomic-embed-text-v1.5",
        "role": "candidate",
        "params": "137M",
        "dims": 768,
        "notes": "Updated nomic with broader training data.",
    },
    # Skyline
    "nomic-embed-code": {
        "hf_id": "nomic-ai/nomic-embed-code",
        "role": "skyline",
        "params": "7B",
        "dims": 768,
        "notes": "7B code model. Requires GPU (14GB VRAM at fp16). Upper bound.",
    },
}

TEST_VOCABULARY = [
    # Class names
    "ConcurrentHashMap",
    "CopyOnWriteArrayList",
    "ExceptionMapper",
    "ChatModel",
    "InMemoryWorkItemTemplateStore",
    # CDI annotations
    "@DefaultBean",
    "@Alternative",
    "@ApplicationScoped",
    "@Priority",
    # Framework identifiers
    "AmbiguousResolutionException",
    "websearch_to_tsquery",
    "quarkus.datasource.active",
    # Compound concepts
    "shadowing",
    "fireAsync",
    "doChat",
]

DISCRIMINATION_PAIRS = [
    # Should be far — surface similarity, different semantics
    {"a": "ConcurrentHashMap", "b": "HashMap",
     "category": "should_be_far", "label": "concurrency vs basic data structure"},
    {"a": "@DefaultBean", "b": "default",
     "category": "should_be_far", "label": "CDI concept vs English word"},
    {"a": "ChatModel", "b": "chat",
     "category": "should_be_far", "label": "LLM abstraction vs conversation"},
    {"a": "shadowing", "b": "Shadow DOM",
     "category": "should_be_far", "label": "JPA field inheritance vs web component"},
    {"a": "stream (Java Streams API — functional pipeline for collections)",
     "b": "stream (Server-Sent Events HTTP streaming)",
     "category": "should_be_far", "label": "Java streams vs SSE streaming"},
    # Should be close — different surface, same concept
    {"a": "@DefaultBean",
     "b": "CDI ambiguous dependency resolution",
     "category": "should_be_close", "label": "annotation vs concept description"},
    {"a": "ConcurrentHashMap",
     "b": "thread-safe map for lock-free concurrency",
     "category": "should_be_close", "label": "class name vs concept description"},
    {"a": "ExceptionMapper",
     "b": "JAX-RS error handling",
     "category": "should_be_close", "label": "class name vs concept description"},
    # Polysemy — same token, different Java concepts
    {"a": "Optional.map()", "b": "HashMap.put()",
     "category": "polysemy", "label": "map as transform vs data structure"},
    {"a": "@Inject (CDI dependency injection)",
     "b": "inject (SQL injection attack)",
     "category": "polysemy", "label": "CDI inject vs security inject"},
]

SCENARIOS_WITH_FAILURE_MODES = {
    "issue-1-reactive-async": ["SEMANTIC_WIN"],
    "issue-2-cdi-wiring": ["VOCABULARY_GAP"],
    "issue-3-persistence-migrations": ["POLYSEMY", "SEMANTIC_WIN"],
    "issue-4-rest-messaging": ["POLYSEMY", "SEMANTIC_WIN"],
    "issue-5-ai-llm-inference": ["VOCABULARY_GAP", "DOMAIN_ABSENCE"],
    "issue-6-testing-ci": ["UNAMBIGUOUS_TERM"],
    "spec1-d1-cdi-priority-tiers": ["VOCABULARY_GAP"],
    "spec1-d2-thread-safety": ["UNAMBIGUOUS_TERM"],
    "spec1-d3-extension-deactivation": ["SEMANTIC_WIN"],
    "spec1-d4-protocol-compliance": ["DOMAIN_ABSENCE"],
    "spec2-d1-cdi-tier-coexistence": ["VOCABULARY_GAP"],
    "spec2-d2-chatmodel-adaptation": ["VOCABULARY_GAP"],
    "spec2-d3-circular-deps": ["POLYSEMY", "SEMANTIC_WIN"],
    "spec2-d4-exception-mapper": ["VOCABULARY_GAP"],
}


def garden_path() -> Path:
    return Path(os.environ.get("GARDEN_PATH", os.path.expanduser("~/.hortora/garden")))


def benchmark_path() -> Path:
    return Path(os.environ.get(
        "BENCHMARK_PATH",
        os.path.expanduser("~/claude/hortora/engine/scripts/benchmark"),
    ))


def results_dir() -> Path:
    return Path(__file__).parent / "results"
