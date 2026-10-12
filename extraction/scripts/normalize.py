"""Shared normalisation utilities for the extraction pipeline.

Guards against common LLM output issues like type suffixes in names,
inconsistent casing, and trailing whitespace.
"""

import re

TYPE_SUFFIXES = re.compile(r'\s*\((person|place|activity|goal|project|concept|organisation|org|event|work-of-art|group|unknown)\)\s*$', re.IGNORECASE)


def clean_entity_name(name: str) -> str:
    """Remove LLM-appended type suffixes and normalise whitespace."""
    if not name:
        return name
    cleaned = TYPE_SUFFIXES.sub('', name).strip()
    cleaned = ' '.join(cleaned.split())
    return cleaned


def clean_entity_dict(entity: dict, name_key: str = 'name') -> dict:
    """Clean the name field of an entity dict."""
    if name_key in entity and isinstance(entity[name_key], str):
        entity[name_key] = clean_entity_name(entity[name_key])
    return entity
