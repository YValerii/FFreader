# FFreader-UI

See the [root README](../README.md) for the full guide, including deployment and known limitations.

Simple Angular Material UI for FFreader-API:

- A form field to paste a fanfiction.net story URL and queue it for parsing.
- A Material table listing all loaded fanfics with title, status (with live polling
  while a story is `PENDING`/`PARSING`), chapters, word count and published date,
  backed by server-side pagination.
- A download button (enabled once a PDF exists, including for `PARSED_PARTIALLY` stories)
  that fetches the generated PDF.
- A delete button that removes the fanfic (and its files) via the API.

## Requirements

- Node.js 26 LTS (Angular 22 also accepts `^22.22.3` or `^24.15.0`, but not Node 25)
- FFreader-API running on `http://localhost:8080` (see `src/environments/environment.ts`
  to point at a different API URL)

## Running

```bash
npm ci
npm start        # serves on http://localhost:4200
```

## Building for production

```bash
npm run build     # outputs to dist/ffreader-ui
```
