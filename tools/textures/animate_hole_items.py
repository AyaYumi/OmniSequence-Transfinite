"""Animate the user's native 32px hole artwork without changing its silhouette.

Editable stills live in tools/textures/static/items. Every frame keeps the exact
source alpha and the black event horizon. Frame zero is the untouched source.
"""
import hashlib
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / "static/items"
TARGET = ROOT / "src/main/resources/assets/molecularmanipulator/textures/item"
PREVIEW = ROOT / "build/reports/hole-item-animation"
FRAMES = 32
TICKS = 2
TAU = math.tau


def sample(rgb, x, y):
    """Bilinear sampling moves the artwork's color details along an orbit."""
    x = np.clip(x, 0, rgb.shape[1] - 1)
    y = np.clip(y, 0, rgb.shape[0] - 1)
    x0, y0 = x.astype(int), y.astype(int)
    x1, y1 = np.minimum(x0 + 1, rgb.shape[1]-1), np.minimum(y0 + 1, rgb.shape[0]-1)
    fx, fy = (x-x0)[..., None], (y-y0)[..., None]
    return ((rgb[y0,x0]*(1-fx)+rgb[y0,x1]*fx)*(1-fy)
            +(rgb[y1,x0]*(1-fx)+rgb[y1,x1]*fx)*fy)


def frame(source, index, white):
    index %= FRAMES
    if index == 0:
        return source.copy()
    pixels = np.asarray(source, dtype=np.uint8)
    rgb = pixels[..., :3].astype(float)
    y, x = np.mgrid[:source.height, :source.width]
    phase = TAU * index / FRAMES
    # Orbit color details within a fixed ring. Alpha, silhouette and center stay put.
    cx, cy = (15.5, 14.5) if white else (16.0, 14.0)
    dx, dy = x-cx, (y-cy)*1.1
    angle, radius = np.arctan2(dy, dx), np.hypot(dx, dy)
    direction = -1 if white else 1
    turn = angle-direction*phase
    sx, sy = cx+radius*np.cos(turn), cy+radius*np.sin(turn)/1.1
    alpha = pixels[...,3:4].astype(float)/255
    sampled_alpha = sample(alpha, sx, sy)
    advected = sample(rgb*alpha, sx, sy)/np.maximum(sampled_alpha,.001)
    advected = np.where((sampled_alpha>.12),advected,rgb)
    luminous = rgb.max(axis=-1)
    ring = np.clip((radius-3.3)/2.2,0,1)*np.clip((12.3-radius)/2.3,0,1)
    if white:
        # Preserve the brilliant core; carry blue/white streaks around the halo.
        mask = ring*np.clip((luminous-65)/100,0,1)
        colored = rgb*(1-mask[...,None]*.68)+advected*mask[...,None]*.68
        arc = ((1+np.cos(2*angle+phase))/2)**7
        arc0 = ((1+np.cos(2*angle))/2)**7
        trail = np.sin(3*angle+phase-radius*.48)-np.sin(3*angle-radius*.48)
        outward = np.cos(radius*1.4-phase*2)-np.cos(radius*1.4)
        motion = (arc-arc0)*1.0+trail*.22+outward*.17
        energy = np.clip(motion*mask,-.38,1.2)
        colored *= (1+energy)[...,None]
        colored += energy[...,None]*np.array([35,45,55])
        center = (radius < 3.8) | ((rgb.min(axis=-1)>235)&(radius<5.0))
        colored = np.where(center[...,None],rgb,colored)
    else:
        # Two bright moving arcs on the photon ring, plus a sliding accretion stream.
        light = np.clip((luminous-45)/95,0,1)
        mask = ring*light
        colored = rgb*(1-mask[...,None]*.45)+advected*mask[...,None]*.45
        arc = ((1+np.cos(2*angle-phase))/2)**8
        arc0 = ((1+np.cos(2*angle))/2)**8
        spiral = np.sin(4*angle-phase*2-radius*.5)-np.sin(4*angle-radius*.5)
        disk = np.exp(-((y-(18.0-.28*x))/1.6)**2)*light
        stream = np.cos(x*.62-phase*2)-np.cos(x*.62)
        motion = (arc-arc0)*1.2+spiral*.20
        energy = np.clip(motion*mask+stream*disk*.48,-.58,1.4)
        colored *= (1+energy)[...,None]
        colored += energy[...,None]*np.array([65,50,20])
        colored = np.where((luminous<=45)[...,None],rgb,colored)
    result = np.clip(np.rint(colored),0,255).astype(np.uint8)
    return Image.fromarray(np.dstack([result,pixels[...,3]]))


def composite(image, size=256):
    result = Image.new("RGB", (size, size), (20, 23, 32))
    display = image.resize((size, size), Image.Resampling.NEAREST)
    result.paste(display, mask=display.getchannel("A"))
    return result


def generate():
    PREVIEW.mkdir(parents=True, exist_ok=True)
    details = {}
    sequences = []
    for name, white in [("black_hole", False), ("white_hole", True)]:
        source = Image.open(SOURCE / (name + ".png")).convert("RGBA")
        assert source.size == (32, 32), source.size
        frames = [frame(source, i, white) for i in range(FRAMES)]
        original = np.asarray(source)
        assert frames[0].tobytes() == source.tobytes()
        assert all(np.array_equal(np.asarray(f)[..., 3], original[..., 3]) for f in frames)
        if not white:
            dark = original[..., :3].max(axis=-1) <= 45
            assert all(np.array_equal(np.asarray(f)[dark], original[dark]) for f in frames)
        assert frame(source, FRAMES, white).tobytes() == source.tobytes()
        assert len({f.tobytes() for f in frames}) == FRAMES
        strip = Image.new("RGBA", (32, 32 * FRAMES))
        for i, image in enumerate(frames):
            strip.paste(image, (0, i * 32))
        strip.save(TARGET / (name + ".png"), optimize=True)
        metadata = {"animation": {"width": 32, "height": 32, "frametime": TICKS, "interpolate": True}}
        (TARGET / (name + ".png.mcmeta")).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf8")
        previews = [composite(f) for f in frames]
        previews[0].save(PREVIEW / (name + ".gif"), save_all=True, append_images=previews[1:], duration=TICKS*50, loop=0, disposal=2)
        contact = Image.new("RGB", (256 * 4, 256 * 2), (20, 23, 32))
        for i in range(8):
            contact.paste(previews[i*4], ((i%4)*256, (i//4)*256))
        contact.save(PREVIEW / (name + "_frames.png"))
        details[name] = {"source_sha256": hashlib.sha256((SOURCE/(name+".png")).read_bytes()).hexdigest(),
                         "frames": FRAMES, "frame_size": [32, 32], "cycle_ticks": FRAMES*TICKS,
                         "alpha_unchanged": True, "first_frame_unchanged": True,
                         "unique_frames": len({f.tobytes() for f in frames})}
        sequences.append(frames)
        print(name, "32 distinct 32x32 frames; 64-tick loop; source alpha preserved")
    combined = []
    for i in range(FRAMES):
        canvas = Image.new("RGB", (640, 340), (20, 23, 32))
        draw = ImageDraw.Draw(canvas)
        draw.text((68, 15), "BLACK HOLE / flowing accretion light", fill=(235, 198, 125))
        draw.text((350, 15), "WHITE HOLE / rotating outward halo", fill=(180, 220, 255))
        for j in range(2):
            canvas.paste(composite(sequences[j][i]), (25+j*310, 42))
            small = sequences[j][i].resize((64,64),Image.Resampling.NEAREST)
            canvas.paste(small,(225+j*310,255),small)
        combined.append(canvas)
    combined[0].save(PREVIEW / "black-white-animation.gif", save_all=True, append_images=combined[1:], duration=TICKS*50, loop=0, disposal=2)
    (PREVIEW / "generation.json").write_text(json.dumps(details,indent=2) + "\n",encoding="utf8")


if __name__ == "__main__":
    generate()
