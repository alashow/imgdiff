# ImgDiff Frontend

## Build Targets

- Standard app (Git + Manual, backend-assisted pickers)
- Standalone app (Manual only, offline browser pickers, single HTML output)

## Development

```bash
npm install
npm run dev
```

## Production Builds

```bash
npm run build
npm run build:standalone
```

- Standard output: `dist/`
- Standalone output: `dist-standalone/index.html`

The standalone artifact inlines JavaScript/CSS/assets into one deployable HTML file.

## Standalone Notes

- Git mode is disabled.
- Folder/image picking uses browser-native `<input type="file">` and drag/drop.
- No backend API is required for manual comparison.

