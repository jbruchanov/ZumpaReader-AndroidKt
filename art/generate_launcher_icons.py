#!/usr/bin/env python3
"""
Regenerates every launcher icon raster from art/ic_launcher_source.png.

Run from the repo root:  python art/generate_launcher_icons.py

Adaptive icon geometry
----------------------
Both adaptive layers are 108dp square. The system masks them down to a 72dp viewport, and only
a 66dp-diameter circle centred in the layer is guaranteed visible on *every* mask shape.

The source art is a wide blob (aspect 1.226) carrying ~4% padding of its own, so padding here is
measured against its real alpha bounding box rather than the file edges. At 54dp wide the whole
silhouette sits inside the 66dp circle with zero clipping on any mask - verified by counting
opaque pixels outside the circle - while still filling ~75% of the 72dp visible viewport.

The art is placed on its centre of mass, not on its bounding box. The jaw and brow carry most of
the weight while thin protrusions stretch the box down and right, so box-centring leaves it
visibly sitting low and left inside a circle mask. The mass centroid is 3.9% left and 3.1% above
the box centre, and correcting for that is what makes it read as centred.

Legacy icon
-----------
The pre-26 bitmap keeps the torn-paper sheet the original icon was drawn on, from
art/ic_launcher_frame_source.png. It is unmasked, so unlike the adaptive background it is free to
keep a transparent surround, and gets no orange plate.

The frame stays on the legacy bitmap only. An adaptive background has to be opaque and full
bleed, and the mask crops everything outside the 72dp viewport, so a square ragged border there
would either be cropped away entirely or read as a square frame sliced by a circle.
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "appAndroid", "src", "main", "res")
SOURCE = os.path.join(ROOT, "art", "ic_launcher_source.png")
FRAME_SOURCE = os.path.join(ROOT, "art", "ic_launcher_frame_source.png")

BACKGROUND = (0xFF, 0xA7, 0x10, 0xFF)   # @color/yellow_orange

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

ADAPTIVE_DP = 108          # adaptive layer canvas
CONTENT_DP = 54            # art width, fully inside the 66dp guaranteed-visible circle
LEGACY_DP = 48             # legacy launcher bitmap
LEGACY_CONTENT_DP = 31.5   # clears the frame`s inward spikes, whose narrowest gap is 78.5%



def art():
    """Source cropped to its true alpha bounding box, so the padding is ours to set.

    Mirrored on the way through: the meme is drawn facing right, and the icon wants it facing
    left. The art is symmetric in everything but the gaze, so a flip is all it takes.
    """
    src = Image.open(SOURCE).convert("RGBA").transpose(Image.FLIP_LEFT_RIGHT)
    bbox = src.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
    return src.crop(bbox)


def monochrome(rgba):
    """Ink-only alpha: black linework goes opaque, the white face fill drops out.

    A themed icon is tinted one flat colour, so handing it the filled silhouette would give a
    featureless blob - only the linework carries the face.
    """
    alpha = Image.eval(rgba.convert("L"), lambda v: 255 - v)
    alpha = Image.composite(alpha, Image.new("L", rgba.size, 0), rgba.getchannel("A"))
    ink = Image.new("RGBA", rgba.size, (0, 0, 0, 0))
    ink.putalpha(alpha)
    return ink


def torn_paper(size_px):
    """The hand-drawn ripped sheet, cropped to its own edges and scaled to fill the canvas.

    The art already carries an opaque white interior, a black ragged border and a transparent
    surround, so there is nothing to composite - just crop the slack off and resize.
    """
    frame = Image.open(FRAME_SOURCE).convert("RGBA")
    bbox = frame.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
    return frame.crop(bbox).resize((size_px, size_px), Image.LANCZOS)


def optical_offset(layer, samples=512):
    """How far to shift `layer`, as a fraction of its own size, to sit on its centre of mass.

    Measured on a boxed-down copy: averaging preserves the mass distribution, and 512 across
    leaves the centroid accurate to well under a tenth of a dp at every density we emit.
    """
    mask = layer.getchannel("A").resize((samples, samples), Image.BOX)
    px = mask.load()
    sx = sy = total = 0
    for y in range(samples):
        for x in range(samples):
            v = px[x, y]
            if v:
                sx += x * v
                sy += y * v
                total += v
    return (0.5 - sx / total / samples), (0.5 - sy / total / samples)


def centred(layer, canvas_px, content_px, offset=(0.0, 0.0)):
    """Scale layer to content_px wide and place it on a transparent square.

    `offset` shifts it by a fraction of its own scaled size - see optical_offset. It is passed in
    rather than measured per layer so the foreground and the monochrome glyph, whose ink alone has
    a different centroid to the filled face, still land in exactly the same place.
    """
    w, h = layer.size
    target = (content_px, max(1, round(content_px * h / w)))
    out = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    out.paste(layer.resize(target, Image.LANCZOS),
              (round((canvas_px - target[0]) / 2 + offset[0] * target[0]),
               round((canvas_px - target[1]) / 2 + offset[1] * target[1])))
    return out


def write(image, folder, name):
    path = os.path.join(RES, folder)
    os.makedirs(path, exist_ok=True)
    image.save(os.path.join(path, name), optimize=True)
    print(f"  {folder}/{name}  {image.width}x{image.height}")


def main():
    face = art()
    ink = monochrome(face)
    offset = optical_offset(face)
    print(f"source art {face.width}x{face.height} (aspect {face.width / face.height:.3f})")
    print(f"optical centring shifts it {offset[0] * 100:+.2f}% x, {offset[1] * 100:+.2f}% y")

    for density, scale in DENSITIES.items():
        canvas = round(ADAPTIVE_DP * scale)
        content = round(CONTENT_DP * scale)
        write(centred(face, canvas, content, offset),
              f"mipmap-{density}", "ic_launcher_foreground.png")
        write(centred(ink, canvas, content, offset),
              f"mipmap-{density}", "ic_launcher_monochrome.png")

        legacy_px = round(LEGACY_DP * scale)
        sheet = torn_paper(legacy_px)
        sheet.alpha_composite(
            centred(face, legacy_px, round(LEGACY_CONTENT_DP * scale), offset))
        write(sheet, f"mipmap-{density}", "ic_launcher.png")

    # Play Console listing icon: 512px, full bleed, opaque, no mask applied by us.
    store = Image.new("RGBA", (512, 512), BACKGROUND)
    store.alpha_composite(centred(face, 512, round(512 * CONTENT_DP / 72), offset))
    store.convert("RGB").save(
        os.path.join(ROOT, "appAndroid", "src", "main", "ic_launcher-playstore.png"), optimize=True)
    print("  appAndroid/src/main/ic_launcher-playstore.png  512x512")


if __name__ == "__main__":
    main()
