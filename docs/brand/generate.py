"""Generate the Argus identity assets.

The mark is the Gjallarhorn recast as the series itself: it rises from the
baseline, crosses the level you set, and everything past the line is the part
that sounds. The horn is not an artefact here — it is the shape of the event.

  --ink   #034694  the level, and the structure
  --ink-2 #2E73C8  the series below the level
  --sig   #B45309  the breach: the part past the line, and nothing else

The horn is one continuous outline. It is split exactly at the level by
subdividing its two bezier edges, so the artwork ships as two plain paths with
no clip path and no element ids — which is what lets the same drawing be a
favicon, an inline React component and a README image without collisions.

    python docs/brand/generate.py .
"""
import os
import sys

from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.misc.transform import Transform

INK, INK2, SIG = "#034694", "#2E73C8", "#B45309"
FACE = "/usr/share/fonts/opentype/fira/FiraSans-Medium.otf"

LEVEL = 37.0                     # y of the rule
RULE = (6.0, 58.0, 10.0)         # x0, x1, thickness

# The horn: an outer edge from the bell rim down to the tip, and an inner edge
# back up to the mouth. Bell upper right, tip lower left.
OUTER = ((52, 8), (52, 28), (38, 46), (12, 53))
TIP = ((12, 53), (18, 45))
INNER = ((18, 45), (33, 40), (42, 30), (34, 15))
MOUTH = ((34, 15), (52, 8))


# ------------------------------------------------------------------ bezier --

def _bez(p, t):
    """Point on a cubic at t."""
    u = 1 - t
    return tuple(u ** 3 * p[0][i] + 3 * u * u * t * p[1][i]
                 + 3 * u * t * t * p[2][i] + t ** 3 * p[3][i] for i in (0, 1))


def _split(p, t):
    """de Casteljau: return the two cubics either side of t."""
    def lerp(a, b):
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

    ab, bc, cd = lerp(p[0], p[1]), lerp(p[1], p[2]), lerp(p[2], p[3])
    abc, bcd = lerp(ab, bc), lerp(bc, cd)
    m = lerp(abc, bcd)
    return (p[0], ab, abc, m), (m, bcd, cd, p[3])


def _t_at_y(p, y, lo=0.0, hi=1.0):
    """Bisect for the t where the curve reaches a given y (monotonic in y)."""
    for _ in range(60):
        mid = (lo + hi) / 2
        if (_bez(p, mid)[1] < y) == (_bez(p, lo)[1] < y):
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def _c(p):
    return (f"C{p[1][0]:.3f} {p[1][1]:.3f} {p[2][0]:.3f} {p[2][1]:.3f} "
            f"{p[3][0]:.3f} {p[3][1]:.3f}")


def horn_split(level=LEVEL):
    """The horn as two closed paths: below the level, and above it."""
    to, ti = _t_at_y(OUTER, level), _t_at_y(INNER, level)
    o_top, o_bot = _split(OUTER, to)
    i_bot, i_top = _split(INNER, ti)

    bell = (f"M{OUTER[0][0]} {OUTER[0][1]}{_c(o_top)}"
            f"L{i_top[0][0]:.3f} {i_top[0][1]:.3f}{_c(i_top)}Z")
    tube = (f"M{o_bot[0][0]:.3f} {o_bot[0][1]:.3f}{_c(o_bot)}"
            f"L{TIP[1][0]} {TIP[1][1]}{_c(i_bot)}Z")
    return tube, bell


def rr(x, y, w, h, r):
    return (f"M{x + r} {y}H{x + w - r}A{r} {r} 0 0 1 {x + w} {y + r}"
            f"V{y + h - r}A{r} {r} 0 0 1 {x + w - r} {y + h}"
            f"H{x + r}A{r} {r} 0 0 1 {x} {y + h - r}"
            f"V{y + r}A{r} {r} 0 0 1 {x + r} {y}Z")


def mark_paths(level=LEVEL, x0=RULE[0], x1=RULE[1], t=RULE[2], tf=None):
    tube, bell = horn_split(level)
    g0 = f'<g transform="{tf}">' if tf else ""
    g1 = "</g>" if tf else ""
    return (f'{g0}<path d="{tube}" fill="var(--argus-series, {INK2})"/>'
            f'<path d="{bell}" fill="var(--argus-breach, {SIG})"/>{g1}'
            f'<path d="{rr(x0, level - t / 2, x1 - x0, t, 2.5)}" '
            f'fill="var(--argus-level, {INK})"/>')


def svg(body, w=64, h=64, vb="0 0 64 64"):
    return ('<svg xmlns="http://www.w3.org/2000/svg" '
            f'viewBox="{vb}" width="{w}" height="{h}" role="img">'
            f'{body}</svg>\n')


# ---------------------------------------------------------------- wordmark --

def wordmark(text="ARGUS", cap=26.0, tracking=0.16):
    font = TTFont(FACE)
    cmap, gs, hmtx = font.getBestCmap(), font.getGlyphSet(), font["hmtx"]
    scale = cap / font["OS/2"].sCapHeight
    parts, x = [], 0.0
    for ch in text:
        g = cmap[ord(ch)]
        pen = SVGPathPen(gs, ntos=lambda v: f"{v:.2f}")
        gs[g].draw(TransformPen(pen, Transform(scale, 0, 0, -scale, x, 0)))
        if pen.getCommands():
            parts.append(pen.getCommands())
        x += hmtx[g][0] * scale + cap * tracking
    return " ".join(parts), x - cap * tracking


def main():
    root = os.path.abspath(sys.argv[1])
    brand = os.path.join(root, "docs", "brand")
    os.makedirs(brand, exist_ok=True)

    # 1. the primary mark
    primary = svg(mark_paths())

    # 2. the reduced cut — same drawing, opened up and weighted for small sizes
    compact = svg(mark_paths(
        level=37, x0=4, x1=60, t=12,
        tf="translate(32 32) scale(1.12) translate(-32 -32)"))

    # 3. the horizontal lockup
    box, gap, cap, pad = 46, 18, 24, 3
    d, w = wordmark(cap=cap)
    total = pad * 2 + box + gap + w
    lockup = svg(
        f'<g transform="translate({pad} {pad}) scale({box / 64:.5f})">'
        f'{mark_paths()}</g>'
        f'<path transform="translate({pad + box + gap:.1f} '
        f'{pad + box / 2 + cap / 2:.1f})" d="{d}" '
        f'fill="var(--argus-level, {INK})"/>',
        w=f"{total:.1f}", h=pad * 2 + box,
        vb=f"0 0 {total:.1f} {pad * 2 + box}")

    for name, content in (("argus-mark.svg", primary),
                          ("argus-mark-compact.svg", compact),
                          ("argus-lockup.svg", lockup)):
        with open(os.path.join(brand, name), "w") as f:
            f.write(content)

    # the favicon is the reduced cut with the palette baked in
    fav = os.path.join(root, "frontend", "public", "favicon.svg")
    with open(fav, "w") as f:
        f.write(compact.replace("var(--argus-series, ", "")
                       .replace("var(--argus-breach, ", "")
                       .replace("var(--argus-level, ", "")
                       .replace(f"{INK2})", INK2).replace(f"{SIG})", SIG)
                       .replace(f"{INK})", INK))

    print(f"wrote mark, compact cut and lockup to {brand}")
    print(f"wrote {fav}")


if __name__ == "__main__":
    main()
