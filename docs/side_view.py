"""Draws the card's trip in side view from the same model the app uses (SharedCard.kt).

You are on the right. Horizontal = distance from you, vertical = up/down the screen.
"""
import math, sys

# DIRECTION: -1 = bottom edge tips back (away from you), +1 = top edge tips back.
# PIVOT: where the card turns about, 0 = its middle, 1 = its bottom edge.
DIRECTION = int(sys.argv[1]) if len(sys.argv) > 1 else -1
# PIVOT "hold" = turn about the middle, but move the whole card towards you as it turns so the
# bottom edge never gets further away (its distance from you holds; up/down path unchanged).
PIVOT = sys.argv[2] if len(sys.argv) > 2 else "0"
TITLE = sys.argv[3] if len(sys.argv) > 3 else "Card trip, side view (you are on the right)"
NOTE = sys.argv[4] if len(sys.argv) > 4 else ""
OUT = sys.argv[5] if len(sys.argv) > 5 else "docs/card-trip-side-view.svg"

SLIDE_PART, TURN_START, TURN_END = 0.3, 0.04, 0.8   # as in SharedCard.kt
HALF = 55                                  # half the card's height, drawn edge-on
POCKET = (190, 380)                        # card centre in its pocket
LIFTED = (190, 270)                        # slid straight up
OVERLAY = (520, 190)                       # held up in front of you

def lerp(a, b, t): return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

def centre(trip):
    if trip <= SLIDE_PART: return lerp(POCKET, LIFTED, trip / SLIDE_PART)
    return lerp(LIFTED, OVERLAY, (trip - SLIDE_PART) / (1 - SLIDE_PART))

def turn(trip):  # degrees; + = top edge tips back (left), - = bottom edge tips back
    return DIRECTION * 180 * min(1, max(0, (trip - TURN_START) / (TURN_END - TURN_START)))

def edges(trip, off=0.0, half=HALF):
    cx, cy = centre(trip); a = math.radians(turn(trip))
    ux, uy = -math.sin(a), -math.cos(a)    # towards the top edge
    nx, ny = math.cos(a), -math.sin(a)     # out of the printed front (name side)
    if PIVOT == "app":
        # As in the app (FlipCard): the card turns about its bottom edge, and that edge moves up
        # from the card's bottom to its top as it turns, so the turned card lands exactly in place.
        # Depth is drawn flattened (the app's distant camera keeps the swing towards you gentle).
        u = abs(turn(trip)) / 180
        bx, by = cx, cy + HALF - 2 * HALF * u
        inset = HALF - half                # shortened face markers sit inside the card's ends
        top = (bx + ux * (2 * HALF - inset) + nx * off, by + uy * (2 * HALF - inset) + ny * off)
        bot = (bx + ux * inset + nx * off, by + uy * inset + ny * off)
        return top, bot
    if PIVOT == "hold":
        # The bottom edge would move away by HALF*sin(turn); move the card towards you to cancel it.
        cx += HALF * abs(math.sin(a))
    else:
        # Turning about a point PIVOT of the way down the card: that point stays on the path.
        f = float(PIVOT)
        px, py = cx, cy + HALF * f
        cx, cy = px + ux * HALF * f, py + uy * HALF * f
    nx, ny = math.cos(a), -math.sin(a)     # out of the printed front (name side)
    top = (cx + ux * half + nx * off, cy + uy * half + ny * off)
    bot = (cx - ux * half + nx * off, cy - uy * half + ny * off)
    return top, bot

def line(p, q, **kw):
    attrs = " ".join(f'{k.replace("_", "-")}="{v}"' for k, v in kw.items())
    return f'<line x1="{p[0]:.1f}" y1="{p[1]:.1f}" x2="{q[0]:.1f}" y2="{q[1]:.1f}" {attrs}/>'

def poly(pts, **kw):
    attrs = " ".join(f'{k.replace("_", "-")}="{v}"' for k, v in kw.items())
    return f'<polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x, y in pts)}" fill="none" {attrs}/>'

ORANGE, GREEN, BLUE, INK, GREY, PALE = "#D85A30", "#1D9E75", "#378ADD", "#2C2C2A", "#888780", "#D3D1C7"
TOP_TRACE, BOT_TRACE = "#7F77DD", "#BA7517"

out = []
# Wallet: cards behind lift a touch, cards in front dip (pale = where they rest).
for i, (dx, dy, budge) in enumerate([(-40, -68, -8), (-20, -34, -14), (20, 34, 22), (40, 68, 18)]):
    x, y = POCKET[0] + dx, POCKET[1] + dy
    out.append(line((x, y - HALF), (x + 4, y + HALF), stroke=PALE, stroke_width=5, stroke_linecap="round"))
    out.append(line((x, y - HALF + budge), (x + 4, y + HALF + budge), stroke=GREY, stroke_width=5, stroke_linecap="round"))

# Traces of the top and bottom edges over the whole trip.
steps = [k / 60 for k in range(61)]
out.append(poly([edges(t)[0] for t in steps], stroke=TOP_TRACE, stroke_width=1.5, stroke_dasharray="5 4"))
out.append(poly([edges(t)[1] for t in steps], stroke=BOT_TRACE, stroke_width=1.5, stroke_dasharray="2 3"))

# Faint in-between poses.
for t in [0.05, 0.25, 0.33, 0.52, 0.62, 0.72, 0.9]:
    top, bot = edges(t)
    out.append(line(top, bot, stroke=ORANGE, stroke_width=3, stroke_linecap="round", opacity="0.25"))

# Key poses.
# 2: just after the turn starts; 3: edge-on, half-way through the turn.
poses = [(0.0, "1"), (TURN_START + (TURN_END - TURN_START) * 0.15, "2"),
         (TURN_START + (TURN_END - TURN_START) * 0.5, "3"), (1.0, "4")]
for t, label in poses:
    top, bot = edges(t)
    ftop, fbot = edges(t, off=6, half=HALF - 4)
    btop, bbot = edges(t, off=-6, half=HALF - 4)
    out.append(line(ftop, fbot, stroke=GREEN, stroke_width=2))
    out.append(line(btop, bbot, stroke=BLUE, stroke_width=2))
    out.append(line(top, bot, stroke=ORANGE, stroke_width=5, stroke_linecap="round"))
    out.append(f'<circle cx="{top[0]:.1f}" cy="{top[1]:.1f}" r="4.5" fill="{INK}"/>')
    out.append(f'<rect x="{bot[0]-4:.1f}" y="{bot[1]-4:.1f}" width="8" height="8" fill="{INK}"/>')

def mid(t):
    top, bot = edges(t)
    return ((top[0] + bot[0]) / 2, (top[1] + bot[1]) / 2)
badges = {}
for (t, label), (dx, dy) in zip(poses, [(-60, 40), (-62, -40), (0, -36), (44, -70)]):
    mx, my = mid(t)
    badges[label] = (round(mx + dx), round(my + dy))
for k, (x, y) in badges.items():
    out.append(f'<circle cx="{x}" cy="{y}" r="12" fill="#F1EFE8" stroke="#5F5E5A" stroke-width="0.5"/>'
               f'<text x="{x}" y="{y + 5}" font-size="14" text-anchor="middle" fill="#444441">{k}</text>')

W, H = 680, 760
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="Helvetica, Arial, sans-serif">
<defs><marker id="a" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M2 1L8 5L2 9" fill="none" stroke="#5F5E5A" stroke-width="1.5"/></marker></defs>
<rect width="{W}" height="{H}" fill="#FFFFFF"/>
<text x="40" y="44" font-size="18" fill="{INK}">{TITLE}</text>
<text x="40" y="70" font-size="14" fill="#5F5E5A">{NOTE}</text>
{chr(10).join(out)}
<g font-size="13" fill="#5F5E5A">
<text x="620" y="{OVERLAY[1] + 5}" text-anchor="end">You →</text>
<text x="250" y="470">Cards in front dip</text>
<text x="40" y="300">Cards behind lift</text>
<line x1="120" y1="540" x2="600" y2="540" stroke="#5F5E5A" stroke-width="1.5" marker-start="url(#a)" marker-end="url(#a)"/>
<text x="120" y="562">Further from you</text><text x="600" y="562" text-anchor="end">Closer to you</text>
</g>
<g font-size="13" fill="#444441">
<line x1="40" y1="596" x2="62" y2="596" stroke="{ORANGE}" stroke-width="5" stroke-linecap="round"/><text x="70" y="600">Card, edge on</text>
<line x1="190" y1="596" x2="212" y2="596" stroke="{GREEN}" stroke-width="2"/><text x="220" y="600">Front: name</text>
<line x1="330" y1="596" x2="352" y2="596" stroke="{BLUE}" stroke-width="2"/><text x="360" y="600">Back: barcode</text>
<circle cx="44" cy="626" r="4.5" fill="{INK}"/><text x="56" y="630">Top edge</text>
<line x1="130" y1="626" x2="170" y2="626" stroke="{TOP_TRACE}" stroke-width="1.5" stroke-dasharray="5 4"/><text x="178" y="630">its path</text>
<rect x="290" y="622" width="8" height="8" fill="{INK}"/><text x="306" y="630">Bottom edge</text>
<line x1="400" y1="626" x2="440" y2="626" stroke="{BOT_TRACE}" stroke-width="1.5" stroke-dasharray="2 3"/><text x="448" y="630">its path</text>
</g>
<g font-size="14" fill="{INK}">
<text x="40" y="668">1  In its pocket, name facing you</text><text x="360" y="668">2  Slid up, starting to turn</text>
<text x="40" y="692">3  Edge-on, half-way round</text><text x="360" y="692">4  Barcode side facing you</text>
<text x="40" y="726">You are on the right. Opening plays 1 → 4; closing plays 4 → 1.</text>
</g>
</svg>'''
open(OUT, "w").write(svg)
