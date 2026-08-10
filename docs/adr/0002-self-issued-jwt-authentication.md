# 0002. Self-issued JWT authentication over OIDC/Keycloak

## Status

Accepted

## Context

The blueprint defines 6 actors (§2) needing server-enforced authorization, and the roadmap's non-functional requirements call for authorization enforced per role. A real identity provider (Keycloak/OIDC) is more representative of production systems, but this is a solo, AI-assisted portfolio build where the explicit goal is building genuine Spring Boot/Spring Security fluency — and self-issued JWT still exercises the real Spring Security filter chain, token issuance, and stateless-session patterns that are the actually-transferable skill here, without the operational overhead of standing up and configuring a separate identity provider for a project with no real users.

## Decision

`platform-security` issues its own JWTs via `POST /auth/login`, validated against a self-managed `app_user` table (username + BCrypt password hash + role), signed with a single HMAC secret (`JWT_SECRET`, ≥32 bytes, externalized via `.env`). Authorization is a hybrid model: coarse endpoint-level role gates in `SecurityFilterChain.authorizeHttpRequests`, plus method-level `@PreAuthorize` for ownership checks an endpoint pattern can't express (e.g. `#username == authentication.name`). No fine-grained ACL table — six static roles don't warrant one. A `SyntheticUserSeeder` creates one demo user per role on startup so every actor can be exercised immediately against a fresh stack.

## Consequences

Zero external identity-provider infrastructure to run or configure; login works the moment the stack is up. The real cost paid to get there: this reactor deliberately doesn't inherit `spring-boot-starter-parent` (it needs its own multi-module dependency management), which silently drops two defaults that parent normally sets — the `spring-boot-maven-plugin` `repackage` goal binding, and the `-parameters` javac flag. The second one is what actually bit us: without it, `@PreAuthorize("#username == authentication.name")` couldn't resolve the parameter name by reflection and silently denied every request rather than erroring, since Spring's SpEL parameter-name resolution depends on that flag being on. Fixed via `maven.compiler.parameters=true` in the root POM; both gotchas are logged in `CLAUDE.md` so they aren't rediscovered per module. Migrating to Keycloak/OIDC later (a documented stretch goal, not core scope) would mean replacing the token issuance/validation path but not the authorization model built on top of it.