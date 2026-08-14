FROM python:3.11-slim

RUN pip install --no-cache-dir uv

WORKDIR /app

COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

COPY src ./src
RUN uv sync --frozen --no-dev

EXPOSE 8000

# --no-sync: the image already has exactly what --frozen --no-dev installed above: a
# runtime re-sync at container start would pull in the dev group too (httpx/pytest/ruff)
# and fail without network access to PyPI.
CMD ["uv", "run", "--no-sync", "uvicorn", "credit_score_service.main:app", "--host", "0.0.0.0", "--port", "8000"]
