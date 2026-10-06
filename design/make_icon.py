"""Builds the simplified KAIRO app icon from the original logo (measured geometry, original orb + spark pixels)."""
from PIL import Image, ImageDraw, ImageFilter
import numpy as np, math

SRC = '/root/.claude/uploads/2da1e78d-aca3-5cb4-b11a-922802291090/a3572063-image.png'
OUT = 1024; SS = 2; N = OUT * SS
src = np.asarray(Image.open(SRC).convert('RGB')).astype(np.float32)

# Measured in the original (pixels of the 1254 image)
RING_C = (627.4, 531.9); RING_R = 302.0; RING_W = 21.0
GAP_HALF_DEG = 6.3
ORB_C = (624.0, 547.0); ORB_R = 200.0
STEM_X_ORIG = 423.5
UPPER = ((825.5, 381.0), (0.692, -0.722))
LOWER = ((817.1, 684.0), (0.739, 0.673))
SPARK_BOX = (585, 150, 668, 252)

ORB_GROW = 1.10; THICK = 1.28
SYM_CENTER = (627.5, 503.0); SYM_H = 684.0
S = 568.0 / SYM_H * SS            # original px -> supersampled output px

def T(p): return ((p[0] - SYM_CENTER[0]) * S + N / 2, (p[1] - SYM_CENTER[1]) * S + N / 2)

BG = np.array([5, 7, 15], np.float32)
canvas = np.ones((N, N, 3), np.float32) * BG

def screen(base, layer):  # layer in 0..255
    return 255 - (255 - base) * (255 - layer) / 255

yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)

# 1. faint radial glow behind the orb
oc = T(ORB_C); d = np.hypot(xx - oc[0], yy - oc[1]) / (RING_R * S * 1.25)
g = np.clip(1 - d, 0, 1) ** 2.2 * 0.38
canvas = screen(canvas, g[..., None] * np.array([0, 95, 230], np.float32))

# 2. ring (gap at top) + K stem + two arms, as a mask
w = RING_W * THICK * S
mask = Image.new('L', (N, N), 0); dr = ImageDraw.Draw(mask)
rc = T(RING_C); rr = RING_R * S
# PIL angles: 0 = 3 o'clock, clockwise. Top is 270.
dr.arc([rc[0] - rr - w / 2, rc[1] - rr - w / 2, rc[0] + rr + w / 2, rc[1] + rr + w / 2],
       start=270 + GAP_HALF_DEG, end=270 - GAP_HALF_DEG, fill=255, width=int(round(w)))
new_orb_r = ORB_R * ORB_GROW
stem_x = ORB_C[0] - ORB_GROW * (ORB_C[0] - STEM_X_ORIG)      # stays tangent to the bigger orb
half = math.sqrt(RING_R ** 2 - (stem_x - RING_C[0]) ** 2)
dr.line([T((stem_x, RING_C[1] - half)), T((stem_x, RING_C[1] + half))], fill=255, width=int(round(w)))

def ring_hit(p, dvec):
    # solve |p + t d - c| = R for the forward t
    px, py = p[0] - RING_C[0], p[1] - RING_C[1]; dx, dy = dvec
    b = px * dx + py * dy; c = px * px + py * py - RING_R ** 2
    t = -b + math.sqrt(b * b - c); return (p[0] + t * dx, p[1] + t * dy)

for p, dvec in (UPPER, LOWER):
    end = ring_hit(p, dvec)
    start = (p[0] - 260 * dvec[0], p[1] - 260 * dvec[1])     # starts under the orb, which covers it
    dr.line([T(start), T(end)], fill=255, width=int(round(w)))
m = np.asarray(mask).astype(np.float32) / 255

# blue glow around the strokes (two blurs, like the original's soft halo)
for radius, strength, col in ((10 * SS, 0.85, (0, 120, 255)), (28 * SS, 0.55, (0, 80, 220))):
    gl = np.asarray(mask.filter(ImageFilter.GaussianBlur(radius))).astype(np.float32) / 255 * strength
    canvas = screen(canvas, gl[..., None] * np.array(col, np.float32))

# stroke fill: bright cyan at top-left to deeper cyan-blue at bottom-right, as in the original
u = np.clip(((xx - N * 0.25) + (yy - N * 0.2)) / (N * 1.1), 0, 1)[..., None]
fill = (1 - u) * np.array([95, 248, 254], np.float32) + u * np.array([0, 208, 254], np.float32)
canvas = canvas * (1 - m[..., None]) + fill * m[..., None]

# 3. orb: original pixels, scaled 10% about its own centre, soft circular edge
R_EXT = 232
x0, y0 = int(ORB_C[0] - R_EXT), int(ORB_C[1] - R_EXT)
crop = src[y0:y0 + 2 * R_EXT, x0:x0 + 2 * R_EXT]
cy_, cx_ = np.mgrid[0:2 * R_EXT, 0:2 * R_EXT].astype(np.float32)
rd = np.hypot(cx_ - (ORB_C[0] - x0), cy_ - (ORB_C[1] - y0))
alpha = np.clip((219 - rd) / (219 - 205), 0, 1)
rgba = np.dstack([crop, alpha * 255]).astype(np.uint8)
size = int(round(2 * R_EXT * S * ORB_GROW))
orb = np.asarray(Image.fromarray(rgba, 'RGBA').resize((size, size), Image.LANCZOS)).astype(np.float32)
ox = int(round(oc[0] - size / 2 + (x0 + R_EXT - ORB_C[0]) * S * ORB_GROW))
oy = int(round(oc[1] - size / 2 + (y0 + R_EXT - ORB_C[1]) * S * ORB_GROW))
a = orb[..., 3:4] / 255
region = canvas[oy:oy + size, ox:ox + size]
canvas[oy:oy + size, ox:ox + size] = region * (1 - a) + orb[..., :3] * a

# 4. amber spark: original pixels, amber parts only (keeps the cyan ring ends out), added with screen
sx0, sy0, sx1, sy1 = SPARK_BOX
sp = src[sy0:sy1, sx0:sx1]
warm = np.clip((sp[..., 0] - sp[..., 2]) / 60.0, 0, 1)[..., None]
sp_layer = np.clip(sp - BG, 0, 255) * warm
sw, sh = int(round((sx1 - sx0) * S)), int(round((sy1 - sy0) * S))
spi = np.asarray(Image.fromarray(sp_layer.astype(np.uint8)).resize((sw, sh), Image.LANCZOS)).astype(np.float32)
px, py = T((sx0, sy0)); px, py = int(round(px)), int(round(py))
canvas[py:py + sh, px:px + sw] = screen(canvas[py:py + sh, px:px + sw], spi)

img = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8)).resize((OUT, OUT), Image.LANCZOS)
img.save('/home/user/KAIRO/design/kairo_app_icon_1024.png')
print('saved')
