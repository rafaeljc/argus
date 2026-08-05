# Argus — brand mark

<img src="argus-lockup.svg" alt="Argus" height="56">

## The mark

A horn rising through the level you set. Everything above the rule is the part
that has crossed it.

The Gjallarhorn is the horn a watchman sounds when something has actually
happened — which is what this product does, and only when it has to. But the
horn is not an artefact here. It *is* the series: it rises from the baseline,
meets the level, and the part past the line is the part that carries. Monitoring
is the rule and the rising value; alerting is everything above the line.

## Files

| File | Use |
| --- | --- |
| `argus-mark.svg` | The primary mark, 64-unit grid. |
| `argus-mark-compact.svg` | The reduced cut — opened up and weighted for small sizes. Shipped as `frontend/public/favicon.svg`. |
| `argus-lockup.svg` | Mark plus the "ARGUS" wordmark, horizontal. |
| `generate.py` | Regenerates all of the above. |

In the app the mark is a React component,
`frontend/src/shared/components/layout/ArgusLockup.tsx`, which carries the same
path data and colours itself from the theme tokens.

## Colour

| Role | Token | Hex |
| --- | --- | --- |
| The level, and the structure | `--color-brand` | `#034694` |
| The series below the level | `--color-brand-soft` | `#2E73C8` |
| The breach — the part past the line | `--color-signal` | `#B45309` |

The signal tone is reserved for the part of the mark that has crossed its level.
It is not a decorative accent, and should not be used anywhere the meaning is
not "this crossed a threshold".

The standalone SVGs read `--argus-level`, `--argus-series` and `--argus-breach`
from any ancestor and fall back to the hexes above, so one file serves colour,
monochrome and reversed contexts:

```html
<div style="--argus-level:#fff; --argus-series:#9dc0ea; --argus-breach:#f0a04b">
```

**On dark grounds the level must be lifted** — `#034694` sits too close to a
dark navy to hold its own. **In monochrome** set all three to one colour: the
rule covers the seam where the horn changes tone, so the form still reads.

## Construction

The horn is a single continuous outline. It is split at the level by
subdividing its two bezier edges with de Casteljau, so the artwork ships as two
plain paths — **no clip path and no element ids**. That is what lets the same
drawing be a favicon, an inline component rendered many times on one page, and
a README image without id collisions.

The split point is computed rather than eyeballed, so the two halves meet
exactly at `LEVEL` however the geometry is later adjusted.

```bash
python docs/brand/generate.py .
```

Needs `fonttools`, and reads Fira Sans Medium from the system to outline the
wordmark. The wordmark ships as paths, so the artwork carries no font
dependency and no network request.

The font path in `generate.py` is a hardcoded Linux path
(`/usr/share/fonts/opentype/fira/FiraSans-Medium.otf`); update `FACE` if
regenerating on macOS or Windows.

## Clear space and minimum size

- Keep clear space of at least the height of the rule on every side.
- Primary mark: 24px and up. Below that, use the reduced cut.
- The reduced cut is the favicon and app-icon; it is not for display use.
