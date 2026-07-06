# Mastra Studio Chinese Overlay Design

## Goal

Add an optional local Chinese UI overlay for Mastra Studio in `mastra-customer-support-demo` without modifying Mastra source files, `node_modules`, or committed generated Studio output.

## Design

Add `npm run dev:zh` as a wrapper around `mastra dev`. The wrapper runs Mastra normally, watches for generated Studio HTML under `.mastra/output/studio/index.html`, and injects a local overlay script tag when the file exists.

The browser-side overlay translates common visible Studio labels, buttons, menu entries, tab names, placeholders, and short status messages. It uses a translation map and a `MutationObserver` so React-rendered content added after navigation is translated too.

## Files

- `src/studio-zh/translations.ts`: shared English-to-Chinese translation map.
- `src/studio-zh/overlay.ts`: browser-side DOM translation logic.
- `src/studio-zh/injector.ts`: Node-side generated HTML injection helper.
- `src/studio-zh/*.test.ts`: tests for map behavior and injection.
- `scripts/dev-zh.ts`: starts Mastra dev and injects the overlay.
- `package.json`: adds `dev:zh`.
- `README.md`: documents Chinese Studio startup.

## Boundaries

This overlay is best-effort UI localization for local learning. It does not translate arbitrary user/model content, Monaco editor internals, external documentation, or every possible Mastra screen. It remains safe to remove by using normal `npm run dev`.
