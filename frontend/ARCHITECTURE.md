# Frontend Structure

This frontend follows a Next.js App Router structure where routing stays in `src/app` and product logic is grouped by feature outside of it.

## Structure

```text
src/
  app/
    (app)/         # Authenticated pages
    api/           # API routes (proxy to backend)
    layout.tsx     # Root layout
    globals.css    # Global styles
  features/        # Feature-specific UI, hooks, data mappers
  components/      # Reusable cross-feature components
  lib/             # Infrastructure (backend fetch, cookies, config)
  types/           # Shared domain types
  styles/          # Global styles
```

## Rules

- Keep `src/app` for routes, layouts, metadata, and route handlers only.
- Move feature-specific UI, hooks, and data mappers into `src/features/<feature>`.
- Keep `src/components` for reusable cross-feature components only.
- Keep `src/lib` for infrastructure helpers like backend fetch wrappers, runtime config, cookies.
- All backend calls go through Next.js API routes (`src/app/api/`) — never call backend directly from client.
- Use `.ts` for non-JSX files and `.tsx` only for React components.

- Add feature-local hooks or mappers next to `followings`, `share`, and `donate` as those areas grow.
- Keep shared components in the grouped subfolders and do not add new files back to the flat `components/` root.
- Consider moving large route-only view logic from `page.tsx` into feature modules once each route gains more interactions.