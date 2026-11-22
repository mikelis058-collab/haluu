import json, os, shutil, math
from PIL import Image

ROOT = "/home/claude/proj/halo-client/src/main/resources"
ANGLE = -45  # flip to 45 if the blade points the wrong way in game

# ---- texture: 16x16 swatch sheet -------------------------------------------
img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
def fill(x0, y0, x1, y1, c):
    for x in range(x0, x1):
        for y in range(y0, y1):
            img.putpixel((x, y), c + (255,))
fill(0, 0, 4, 4,   (0x16, 0x16, 0x1A))   # black handle
fill(4, 0, 8, 4,   (0x2B, 0x2B, 0x33))   # dark grey
fill(8, 0, 12, 4,  (0xD4, 0x2A, 0x44))   # crimson accent
fill(12, 0, 16, 4, (0xBF, 0xC4, 0xCC))   # steel
fill(8, 4, 12, 8,  (0x7A, 0x0F, 0x22))   # dark red spine
fill(12, 4, 16, 8, (0xFF, 0x8F, 0xA3))   # pink edge
# blade gradient 8x4: dark crimson -> red -> pink
stops = [(0x8A,0x12,0x26),(0xC8,0x1E,0x3C),(0xE8,0x3A,0x5C),(0xFF,0x7A,0x96)]
for x in range(8):
    t = x / 7 * (len(stops) - 1); i = min(int(t), len(stops) - 2); f = t - i
    c = tuple(int(stops[i][k] * (1 - f) + stops[i+1][k] * f) for k in range(3))
    for y in range(4, 8):
        j = 6 if (x + y) % 5 == 0 else 0   # faint marbling
        img.putpixel((x, y), tuple(min(255, v + j) for v in c) + (255,))
os.makedirs(f"{ROOT}/assets/halo/textures/item", exist_ok=True)
img.save(f"{ROOT}/assets/halo/textures/item/knife.png")

UV = dict(black=[0,0,4,4], grey=[4,0,8,4], accent=[8,0,12,4], steel=[12,0,16,4],
          spine=[8,4,12,8], edge=[12,4,16,8], blade=[0,4,8,8])

# ---- geometry (u across, v along length, w thickness) ----------------------
# (u0,u1, v0,v1, w0,w1, main_uv, edge_uv)
E = []
def box(u0,u1,v0,v1,w0,w1,main,edge=None): E.append((u0,u1,v0,v1,w0,w1,main,edge or main))
# handles
box(-1.3,1.3, 0,9.5, -1.9,-0.7, "black")
box(-1.3,1.3, 0,9.5,  0.7, 1.9, "black")
box(-0.5,0.5, 1.5,8.2, -2.0,-1.9, "accent")
box(-0.5,0.5, 1.5,8.2,  1.9, 2.0, "accent")
box(-0.6,0.6, 0.5,2.5, -0.7, 0.7, "grey")        # spacer
box(-1.5,1.5, -0.8,0.0, -2.0, 2.0, "grey")       # end cap
box(-1.6,1.6, 9.5,10.6, -1.0, 1.0, "grey")       # guard block
box(-0.5,0.5, 9.7,10.7, -2.2, 2.2, "steel")      # pivot pin
# blade: 24 thin segments, curved spine, belly that sweeps to a hooked tip
B0, BL, N = 10.6, 11.2, 24
for i in range(N):
    t0, t1 = i / N, (i + 1) / N
    tm = (t0 + t1) / 2
    spine = -1.1 + 2.4 * tm ** 3                  # spine curves toward the cutting edge near the tip
    width = 2.6 * (1 - tm ** 2.6) + 0.35          # belly, narrowing to the point
    box(round(spine,3), round(spine + width,3), round(B0 + t0*BL,3), round(B0 + t1*BL + 0.02,3), -0.4, 0.4, "blade", "edge")
box(-1.15,-0.8, B0, B0 + 8.2, -0.5, 0.5, "spine")   # dark spine strip

def conv(e):
    u0,u1,v0,v1,w0,w1,main,edge = e
    dy = -2.45
    frm = [round(8+u0,3), round(v0+dy,3), round(8+w0,3)]
    to  = [round(8+u1,3), round(v1+dy,3), round(8+w1,3)]
    faces = {}
    for f in ("north","south"):
        faces[f] = {"uv": UV[main], "texture": "#t"}
    for f in ("east","west","up","down"):
        faces[f] = {"uv": UV[edge], "texture": "#t"}
    return {"from": frm, "to": to,
            "rotation": {"origin": [8,8,8], "axis": "z", "angle": ANGLE},
            "faces": faces}

model = {
  "textures": {"t": "halo:item/knife", "particle": "halo:item/knife"},
  "gui_light": "front",
  "elements": [conv(e) for e in E],
  "display": {
    "thirdperson_righthand": {"rotation":[0,-90,55],"translation":[0,4,0.5],"scale":[0.85,0.85,0.85]},
    "thirdperson_lefthand":  {"rotation":[0,90,-55],"translation":[0,4,0.5],"scale":[0.85,0.85,0.85]},
    "firstperson_righthand": {"rotation":[0,-90,25],"translation":[1.13,3.2,1.13],"scale":[0.68,0.68,0.68]},
    "firstperson_lefthand":  {"rotation":[0,90,-25],"translation":[1.13,3.2,1.13],"scale":[0.68,0.68,0.68]},
    "gui":    {"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]},
    "ground": {"translation":[0,2,0],"scale":[0.5,0.5,0.5]},
    "fixed":  {"rotation":[0,180,0],"scale":[1,1,1]}
  }
}
os.makedirs(f"{ROOT}/assets/halo/models/item", exist_ok=True)
json.dump(model, open(f"{ROOT}/assets/halo/models/item/knife.json", "w"), indent=1)

# ---- one tiny pack per sword ------------------------------------------------
ITEMS = ["wooden_sword","stone_sword","copper_sword","iron_sword","golden_sword","diamond_sword","netherite_sword"]
shutil.rmtree(f"{ROOT}/resourcepacks", ignore_errors=True)
mcmeta = '{ "pack": { "description": "Halo knife skin", "pack_format": 75, "supported_formats": { "min_inclusive": 46, "max_inclusive": 99 } } }\n'
for it in ITEMS:
    d = f"{ROOT}/resourcepacks/knives_{it}"
    os.makedirs(f"{d}/assets/minecraft/items", exist_ok=True)
    open(f"{d}/pack.mcmeta", "w").write(mcmeta)
    open(f"{d}/assets/minecraft/items/{it}.json", "w").write(
        '{ "model": { "type": "minecraft:model", "model": "halo:item/knife" } }\n')

# ---- flat preview (front view, same rigid rotation) -------------------------
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon
fig, ax = plt.subplots(figsize=(5,5)); ax.set_facecolor("#9aa")
col = dict(black="#16161A", grey="#2B2B33", accent="#D42A44", steel="#BFC4CC", spine="#7A0F22", edge="#FF8FA3", blade="#E0304F")
a = math.radians(ANGLE)
def rot(x,y):
    dx,dy = x-8,y-8
    return 8+dx*math.cos(a)-dy*math.sin(a), 8+dx*math.sin(a)+dy*math.cos(a)
order = sorted(range(len(E)), key=lambda i: (E[i][4]+E[i][5]))
for i in order:
    e = E[i]; u0,u1,v0,v1 = e[:4]
    pts = [rot(8+u,v-2.45) for u,v in ((u0,v0),(u1,v0),(u1,v1),(u0,v1))]
    ax.add_patch(Polygon(pts, closed=True, fc=col[e[6]], ec="k", lw=.4))
ax.add_patch(plt.Rectangle((0,0),16,16,fill=False,ec="w",ls="--"))
ax.set_xlim(-3,19); ax.set_ylim(-3,19); ax.set_aspect("equal")
plt.savefig("/home/claude/knife_preview.png", dpi=90)
print("ok", len(E), "elements")
