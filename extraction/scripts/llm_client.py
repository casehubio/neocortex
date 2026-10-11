"""Shared LLM client factory — resolves Vertex AI or direct Anthropic from environment."""

import os
import anthropic


def create_client():
    """Create an Anthropic client from environment configuration.

    Supports:
    - Vertex AI: CLAUDE_CODE_USE_VERTEX=1 + ANTHROPIC_VERTEX_PROJECT_ID + CLOUD_ML_REGION
    - Direct API: ANTHROPIC_API_KEY
    """
    if os.environ.get('CLAUDE_CODE_USE_VERTEX') == '1':
        project_id = os.environ.get('ANTHROPIC_VERTEX_PROJECT_ID')
        region = os.environ.get('CLOUD_ML_REGION', 'us-east5')
        if not project_id:
            raise RuntimeError("ANTHROPIC_VERTEX_PROJECT_ID required when CLAUDE_CODE_USE_VERTEX=1")
        return anthropic.AnthropicVertex(project_id=project_id, region=region)

    return anthropic.Anthropic()


def model_id(preferred: str = None) -> str:
    """Resolve model ID for the current backend.

    Vertex uses '@' format: claude-haiku-4-5@20251001
    Direct API uses '-' format: claude-haiku-4-5-20251001
    """
    if preferred:
        return preferred
    if os.environ.get('CLAUDE_CODE_USE_VERTEX') == '1':
        return 'claude-haiku-4-5@20251001'
    return 'claude-haiku-4-5-20251001'
