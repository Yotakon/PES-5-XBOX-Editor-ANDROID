#!/usr/bin/env python3
"""
Añade las FACE especiales a la lista de FACE normales de cada piel en PES 5 Xbox.

Para cada piel (1 W, 2 Y, 3 R, 4 K) crea al final del 0_text.afs un bloque nuevo con
sus FACE normales (en el mismo orden, así los jugadores conservan su número) seguidas
de sus FACE especiales. Después:

- 0_text.afs: el archivo 424 (datos del editor) pasa a tener, para cada piel, tantas
  FACE normales como normales + especiales. El índice del AFS crece y los datos
  existentes se desplazan para hacerle sitio.
- PES5.xbe: las bases de FACE normal (offset 0x578E38) apuntan a los bloques nuevos
  y el número de archivos del 0_text (offset 0x4EF7E8) sube.

Con FACE normal, la FACE especial número E de una piel con N normales es la normal N + E.
El HAIR no cambia: con FACE normal es 2829 + ID de HAIR.

Uso:
    python3 ampliar_caras.py 0_text.afs PES5.xbe carpeta_de_salida
"""
import os
import struct
import sys
import zlib

SECTOR = 2048
XBE_COUNT = 0x4EF7E8        # número de archivos de 0_text.afs (VA 0x74F908)
XBE_FACE = 0x578E18         # TABLA_CARA (VA 0x7D88B8): 4 tipos x 4 pieles, 0x10000 + archivo
EDITOR_DATA = 424           # archivo del 0_text con los contadores de FACE del editor
SKINS = ["1 (W)", "2 (Y)", "3 (R)", "4 (K)"]
MAX_NUMBER = 512


def align(n, a=SECTOR):
    return (n + a - 1) // a * a


def read_afs(path):
    with open(path, "rb") as f:
        head = f.read(8)
        if head[:4] != b"AFS\0":
            sys.exit("%s no es un archivo AFS" % path)
        n = struct.unpack("<I", head[4:])[0]
        toc = [struct.unpack("<II", f.read(8)) for _ in range(n)]
        names = struct.unpack("<II", f.read(8))
    return n, toc, names


def read_entry(f, toc, i):
    o, s = toc[i]
    f.seek(o)
    return f.read(s)


def unpack_editor_data(raw):
    """Archivo comprimido del 0_text: cabecera 00 06 01 00, tamaño zlib, tamaño real, zlib en +32."""
    if raw[:4] != b"\x00\x06\x01\x00":
        sys.exit("El archivo %d no tiene el formato esperado" % EDITOR_DATA)
    zlen, ulen = struct.unpack("<II", raw[4:12])
    data = zlib.decompress(raw[32:32 + zlen])
    if len(data) != ulen:
        sys.exit("El archivo %d no se descomprime bien" % EDITOR_DATA)
    return bytearray(data), raw[12:32]


def pack_editor_data(data, rest):
    z = zlib.compress(bytes(data), 9)
    return b"\x00\x06\x01\x00" + struct.pack("<II", len(z), len(data)) + rest + z


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    afs_in, xbe_in, out_dir = sys.argv[1:]
    os.makedirs(out_dir, exist_ok=True)

    xbe = bytearray(open(xbe_in, "rb").read())
    n, toc, names = read_afs(afs_in)
    xbe_n = struct.unpack("<I", xbe[XBE_COUNT:XBE_COUNT + 4])[0]
    if xbe_n != n:
        sys.exit("El XBE espera %d archivos en el 0_text y este tiene %d. ¿Es el PES5.xbe de Xbox?" % (xbe_n, n))
    face = list(struct.unpack("<16I", xbe[XBE_FACE:XBE_FACE + 64]))
    if any(v < 0x10000 for v in face[4:12]):
        sys.exit("La tabla de FACE del XBE no tiene el formato esperado")
    special = [v - 0x10000 for v in face[4:8]]
    normal = [v - 0x10000 for v in face[8:12]]
    if min(normal) >= n:
        sys.exit("Este XBE ya está ampliado")

    src = open(afs_in, "rb")
    editor_raw = read_entry(src, toc, EDITOR_DATA)
    editor, editor_rest = unpack_editor_data(editor_raw)
    p0 = struct.unpack("<I", editor[8:12])[0]
    counts = list(struct.unpack("<8H", editor[p0:p0 + 16]))
    nc, sc = counts[:4], counts[4:]

    # Bloques nuevos: normales de la piel + especiales de la piel
    blocks, new_normal, cur = [], [], n
    for s in range(4):
        total = nc[s] + sc[s]
        if total > MAX_NUMBER:
            sys.exit("La piel %s tendría %d FACE normales (máximo %d)" % (SKINS[s], total, MAX_NUMBER))
        new_normal.append(cur)
        blocks += [normal[s] + i for i in range(nc[s])] + [special[s] + j for j in range(sc[s])]
        cur += total
    n2 = cur

    # Datos del editor con los contadores nuevos
    struct.pack_into("<4H", editor, p0, *[nc[s] + sc[s] for s in range(4)])
    editor_new = pack_editor_data(editor, editor_rest)

    # Índice nuevo: los datos existentes se desplazan lo justo para que quepa
    used = [o for o, s in toc if s]
    first = min(used)
    head_size = align(max(first, 8 + 8 * n2 + 8))
    delta = head_size - first
    end_old = max(o + s for o, s in toc)
    new_toc = [(o + delta if s else 0, s) for o, s in toc]
    name_ptr = (names[0] + delta if names[0] else 0, names[1])

    out_afs = os.path.join(out_dir, "0_text.afs")
    with open(out_afs, "wb") as out:
        out.write(b"\0" * head_size)
        src.seek(first)
        left = align(end_old) - first
        while left > 0:
            chunk = src.read(min(left, 16 << 20))
            if not chunk:
                break
            out.write(chunk)
            left -= len(chunk)
        out.write(b"\0" * (align(out.tell()) - out.tell()))

        # Archivo 424: en su sitio si cabe, si no al final
        o, s = toc[EDITOR_DATA]
        nxt = min([x for x in used if x > o] + [align(end_old)])
        if align(len(editor_new)) <= nxt - o:
            out.seek(o + delta)
            out.write(editor_new + b"\0" * (align(len(editor_new)) - len(editor_new)))
            new_toc[EDITOR_DATA] = (o + delta, len(editor_new))
            out.seek(0, 2)
        else:
            pos = out.tell()
            out.write(editor_new + b"\0" * (align(len(editor_new)) - len(editor_new)))
            new_toc[EDITOR_DATA] = (pos, len(editor_new))

        # Copias de las FACE
        for i in blocks:
            data = read_entry(src, toc, i)
            pos = out.tell()
            out.write(data + b"\0" * (align(len(data)) - len(data)))
            new_toc.append((pos, len(data)))

        out.seek(0)
        out.write(b"AFS\0" + struct.pack("<I", n2))
        for o2, s2 in new_toc:
            out.write(struct.pack("<II", o2, s2))
        out.write(struct.pack("<II", *name_ptr))

    # XBE
    for s in range(4):
        struct.pack_into("<I", xbe, XBE_FACE + 4 * (8 + s), 0x10000 + new_normal[s])
    struct.pack_into("<I", xbe, XBE_COUNT, n2)
    out_xbe = os.path.join(out_dir, os.path.basename(xbe_in))
    open(out_xbe, "wb").write(xbe)

    # Tabla de los archivos nuevos
    out_csv = os.path.join(out_dir, "caras_ampliadas.csv")
    with open(out_csv, "w", encoding="utf-8-sig") as t:
        t.write("PIEL;NUMERO FACE NORMAL;ARCHIVO NUEVO;COPIA DE;ORIGEN\r\n")
        k = n
        for s in range(4):
            for i in range(nc[s]):
                t.write("%d;%d;%d;%d;normal %d\r\n" % (s + 1, i + 1, k, normal[s] + i, i + 1)); k += 1
            for j in range(sc[s]):
                t.write("%d;%d;%d;%d;especial %d\r\n" % (s + 1, nc[s] + j + 1, k, special[s] + j, j + 1)); k += 1

    print("0_text.afs: %d -> %d archivos (%s)" % (n, n2, out_afs))
    for s in range(4):
        print("Piel %s: FACE normal 1-%d en %d-%d (normales 1-%d, especiales %d-%d)" % (
            SKINS[s], nc[s] + sc[s], new_normal[s], new_normal[s] + nc[s] + sc[s] - 1, nc[s], nc[s] + 1, nc[s] + sc[s]))
    print("XBE: %s" % out_xbe)
    print("Tabla: %s" % out_csv)


if __name__ == "__main__":
    main()
