"""Synthetic benchmark for Google Antigravity #308.

This file contains intentionally incorrect expiry-boundary behavior.
It is public, synthetic, and contains no Job Search Automation source or data.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Generic, TypeVar

T = TypeVar("T")


@dataclass(frozen=True)
class _Entry(Generic[T]):
    value: T
    expires_at: float


class ExpiryCache(Generic[T]):
    """Small deterministic TTL cache used only for the #308 benchmark."""

    def __init__(self) -> None:
        self._items: dict[str, _Entry[T]] = {}

    def set(self, key: str, value: T, ttl_seconds: float, *, now: float) -> None:
        self._items[key] = _Entry(value=value, expires_at=now + ttl_seconds)

    def get(self, key: str, *, now: float) -> T | None:
        entry = self._items.get(key)
        if entry is None:
            return None

        # Intentional benchmark defect:
        # an item must already be expired when now == expires_at.
        if now > entry.expires_at:
            self._items.pop(key, None)
            return None

        return entry.value
