# CLAUDE.md - Frontend

## Tech Stack

- React 19 with TypeScript
- Vite (env var `VITE_API_BASE_URL`)
- Axios for HTTP communication
- Zustand for state management
- TailwindCSS 4.x for styling
- @heroicons/react for icons

## Architecture

- Vertical Slice Architecture: organize by feature, not by type
- Each feature owns: page component, API service, types, child components

## Project Structure

- `src/features/[feature-name]/` — page, components, service, types
- `src/shared/components/` — reusable UI (buttons, form fields, layout, toasts, skeletons)
- `src/shared/hooks/` — custom hooks (`useAuthStore`, `useCsrfToken`)
- `src/shared/api/` — shared HTTP layer: `client.ts` (Axios instance + CSRF / envelope-unwrap / error interceptors), `errors.ts` (typed error hierarchy + global handler registry), `cookies.ts` (jsdom-safe cookie reader)
- `src/shared/lib/` — cross-feature runtime helpers (e.g. `money.ts`) that are not HTTP-specific
- `src/shared/types/` — envelope types (`SuccessEnvelope<T>`, `ErrorEnvelope`, `Paginated<T>`) mirroring `contracts/openapi/argus-v1.yaml`

## Conventions

- API base URL in `.env` (`VITE_API_BASE_URL`), never hardcoded
- Typed API service per feature (never raw Axios calls in components)
- Axios request interceptor: `withCredentials: true`; attach `X-CSRF-Token` on non-GET by reading the `argus_csrf` cookie
- Axios response interceptor: on 401 → clear auth store + redirect to `/login` (no refresh flow); on 403 `EMAIL_NOT_VERIFIED` → `/verify-email`; on 403 `ACCOUNT_SUSPENDED` → suspended terminal page; on 429 → toast with `Retry-After`
- Conditional rendering driven by `is_admin` / `is_verified` from `GET /account/me` (cached in Zustand `useAuthStore`)
- Money and quantity fields arrive as JSON strings — do not `parseFloat` in the render path

## UI Requirements

- Clean, professional design with `#034694` as accent color, exposed as a Tailwind theme token (`--color-brand`) and consumed as `bg-brand` / `text-brand` / `ring-brand`; never hardcode the hex in components
- Rounded borders on every interactive element (buttons, inputs, cards, modals) — default to `rounded-lg`
- Responsive layout for desktop, tablet, mobile; mobile-first with Tailwind `sm`/`md`/`lg` breakpoints, no fixed pixel widths
- Loading states for every API call (skeleton for lists, spinner for detail, disabled button for submits) — no blank flashes
- Field-level errors from `ErrorEnvelope.details[].field` render inline directly under the corresponding form input; non-field errors (429, network, 5xx) go to a toast

## Patterns We Do NOT Use

- Redux, MobX, or React Context for global state (Zustand only)
- CSS Modules, styled-components, or ad-hoc CSS files (TailwindCSS only)
- Default exports (named exports only)
- JWT, refresh tokens, or `Authorization: Bearer …` headers (session cookie only)
- HATEOAS-driven button visibility (role + state + error-code gating instead)

## References

- `docs/frontend-pdr.md` — per-page contract, request/response shapes, error handling matrix
- `contracts/openapi/argus-v1.yaml` — schema source of truth
