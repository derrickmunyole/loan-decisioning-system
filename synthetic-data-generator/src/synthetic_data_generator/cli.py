"""CLI entrypoint: seeds N submitted applications through the real REST API.

`--target=api` is the only supported mode for v1 — a bulk `--target=db` mode is a later
(Milestone 6) addition for load testing, not needed here.
"""

from __future__ import annotations

import argparse
import logging
import os
import sys

from synthetic_data_generator.api_client import ApiClient, ApiError
from synthetic_data_generator.fake_data import (
    fake_applicant_profile,
    fake_document,
    fake_draft_fields,
)

logger = logging.getLogger("synthetic_data_generator")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Seeds demo applications by driving the real REST API."
    )
    parser.add_argument(
        "--target",
        choices=["api"],
        default="api",
        help="Seeding mode (only 'api' is supported for now).",
    )
    parser.add_argument(
        "--count", type=int, default=10, help="Number of applications to submit."
    )
    parser.add_argument(
        "--base-url", default="http://localhost:8080", help="Origination API base URL."
    )
    parser.add_argument(
        "--username", default="applicant", help="Seeded applicant username to log in as."
    )
    parser.add_argument(
        "--password-env",
        default="SEED_USERS_PASSWORD",
        help="Environment variable holding the login password.",
    )
    return parser.parse_args(argv)


def generate_one(client: ApiClient) -> str:
    application = client.create_application(fake_applicant_profile())
    application_id = application["id"]
    client.update_draft(application_id, fake_draft_fields())
    document = fake_document()
    client.upload_document(
        application_id, document.document_type, document.filename, document.content
    )
    client.submit_application(application_id)
    return application_id


def run(args: argparse.Namespace) -> int:
    password = os.environ.get(args.password_env)
    if not password:
        logger.error("Environment variable %s is not set.", args.password_env)
        return 1

    client = ApiClient(args.base_url)
    client.login(args.username, password)

    succeeded = 0
    for i in range(args.count):
        try:
            application_id = generate_one(client)
            succeeded += 1
            logger.info("[%d/%d] submitted application %s", i + 1, args.count, application_id)
        except ApiError as e:
            logger.error("[%d/%d] failed: %s", i + 1, args.count, e)

    logger.info("Done: %d/%d applications submitted.", succeeded, args.count)
    return 0 if succeeded == args.count else 1


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    args = parse_args(sys.argv[1:] if argv is None else argv)
    return run(args)


if __name__ == "__main__":
    raise SystemExit(main())
