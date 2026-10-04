"""Generates the AngraiPhoneTransfer application icon.

Pure standard library: renders the mark at 4x and box-filters it down, then
packs PNG entries into a multi-size .ico. Run from the repo root:

    python3 AngraiPhoneTransfer/assets/make_icon.py
"""
import math, os, struct, zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "AngraiPhoneTransfer", "Assets", "AngraiPhoneTransfer.ico")
SIZES = [256, 128, 64, 48, 32, 16]
SS = 4  # supersampling factor

BG = (10, 10, 12)          # Angra background
GOLD = (212, 175, 55)      # Angra gold
GOLD_DIM = (141, 113, 59)  # Angra gold, muted


def render(size):
    """Returns an RGBA bytearray of one square icon face."""
    n = size * SS
    px = bytearray(n * n * 4)
    c = (n - 1) / 2.0
    radius_out = n * 0.40      # outer ring radius
    ring_w = n * 0.052         # ring stroke
    corner = n * 0.22          # rounded-square corner radius

    def put(x, y, rgb, a):
        i = (y * n + x) * 4
        ia = 255 - a
        px[i] = (px[i] * ia + rgb[0] * a) // 255
        px[i + 1] = (px[i + 1] * ia + rgb[1] * a) // 255
        px[i + 2] = (px[i + 2] * ia + rgb[2] * a) // 255
        px[i + 3] = min(255, px[i + 3] + (255 - px[i + 3]) * a // 255)

    # Rounded-square plate.
    for y in range(n):
        for x in range(n):
            dx = max(abs(x - c) - (n / 2 - corner), 0.0)
            dy = max(abs(y - c) - (n / 2 - corner), 0.0)
            if math.hypot(dx, dy) <= corner:
                put(x, y, BG, 255)

    # Aperture ring, with a dimmer lower arc so it reads as a lens.
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c)
            if abs(d - radius_out) <= ring_w / 2:
                put(x, y, GOLD if y <= c else GOLD_DIM, 255)

    # Downward transfer arrow inside the ring: stem plus chevron.
    stem_w = n * 0.070
    top = c - n * 0.23
    bottom = c + n * 0.02
    for y in range(int(top), int(bottom)):
        for x in range(int(c - stem_w / 2), int(c + stem_w / 2) + 1):
            put(x, y, GOLD, 255)

    head_h = n * 0.15
    head_w = n * 0.185
    for y in range(int(bottom - head_h), int(bottom + head_h)):
        t = (y - (bottom - head_h)) / (2 * head_h)
        half = head_w * (1 - t)
        if half <= 0:
            continue
        for x in range(int(c - half), int(c + half) + 1):
            if abs(x - c) <= half and y <= bottom + head_h:
                put(x, y, GOLD, 255)

    # Box-filter down to the target size.
    out = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            r = g = b = a = 0
            for sy in range(SS):
                for sx in range(SS):
                    i = ((y * SS + sy) * n + (x * SS + sx)) * 4
                    r += px[i]; g += px[i + 1]; b += px[i + 2]; a += px[i + 3]
            k = SS * SS
            o = (y * size + x) * 4
            out[o] = r // k; out[o + 1] = g // k; out[o + 2] = b // k; out[o + 3] = a // k
    return bytes(out)


def png(rgba, size):
    raw = b"".join(b"\x00" + rgba[y * size * 4:(y + 1) * size * 4] for y in range(size))

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


def main():
    faces = [(s, png(render(s), s)) for s in SIZES]
    header = struct.pack("<HHH", 0, 1, len(faces))
    offset = 6 + 16 * len(faces)
    entries, blobs = b"", b""
    for size, data in faces:
        entries += struct.pack("<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
        blobs += data
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(header + entries + blobs)
    print(f"wrote {os.path.normpath(OUT)} ({len(header + entries + blobs)} bytes)")


if __name__ == "__main__":
    main()
