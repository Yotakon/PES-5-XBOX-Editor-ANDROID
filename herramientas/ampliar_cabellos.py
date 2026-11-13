#!/usr/bin/env python3
"""
Añade cabellos de tipo Especial (EN) a la lista de cabellos del editor de PES 5 Xbox.

Hay que pasar antes por ampliar_caras.py: este script toma el número de FACE normales del editor
(suma de los cuatro contadores del archivo 424) y deja el mismo número de cabellos EN.

El editor elige el cabello por su posición en una lista (archivo 424 del 0_text) y el juego carga el
archivo 2829 + ID de HAIR. Los IDs 1300 en adelante apuntan a los modelos con gorra (4129-4148) y a
los E de las caras especiales (4149-4428), que no deben salir en el editor. Por eso los cabellos se
copian a un bloque nuevo al final del 0_text y el inicio de los cabellos del XBE se mueve ahí:

  IDs 0-1299     copia de los archivos 2829-4128 (normales y los 274 EN del juego)
  IDs 1300-...   cabellos EN nuevos: clones, en orden, de los 256 EN con textura (3873-4128),
                 para reemplazarlos con GGS por los tuyos

- 0_text.afs: bloque nuevo al final y el archivo 424 con la lista ampliada. En la lista, los 10
  últimos EN originales (IDs 1290-1299) siguen siendo los últimos por índice: el juego los oculta
  hasta que se desbloquean (regla "ocultar los últimos 10 especiales") y los nuevos quedan antes.
  Tras los registros, el archivo 424 guarda una marca 0/1 por cada índice de cabello especial (el juego
  la lee con el índice: 0 en los cabellos casi rapados, de 64-73 vértices, y en los 10 bloqueados).
  Esa lista se amplía con una marca por EN (1 en los EN nuevos).
- El archivo 424 comprimido debe caber en su hueco original del 0_text (4096 bytes, 2 sectores):
  con 3 sectores el juego se cierra tras el logo. Por eso los EN nuevos usan un registro sencillo.
- PES5.xbe: las entradas de la tabla de cabellos para FACE creada y normal (offset 0x578A24)
  apuntan al bloque nuevo y el número de archivos del 0_text (offset 0x4EF7E8) sube.
- Los cabellos de las FACE especiales (faceset 1) no cambian: siguen siendo archivo de FACE + 2559.

Uso:
    python3 ampliar_cabellos.py 0_text.afs PES5.xbe carpeta_de_salida [--en N]
"""
import os
import struct
import sys
import zlib

SECTOR = 2048
XBE_COUNT = 0x4EF7E8      # número de archivos de 0_text.afs
XBE_HAIR = 0x578A24       # TABLA_PELO: 3 tipos de FACE x 4 pieles, 0x10000 + archivo
XBE_FACE = 0x578E18
EDITOR_DATA = 424
HAIR_BASE = 2829
FIRST_SPECIAL = 1026      # ID del primer EN
LAST_ORIGINAL = 1299      # ID del último EN del juego
EN_ORIGINAL = 274
LOCKED = 10
TEXTURED = range(3873, 4129)
MAX_ID = 2047


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


def unpack_entry(raw):
    if raw[:4] != b"\x00\x06\x01\x00":
        sys.exit("El archivo %d no tiene el formato esperado" % EDITOR_DATA)
    zlen, ulen = struct.unpack("<II", raw[4:12])
    data = zlib.decompress(raw[32:32 + zlen])
    if len(data) != ulen:
        sys.exit("El archivo %d no se descomprime bien" % EDITOR_DATA)
    return bytearray(data), raw[12:32]


def pack_entry(data, rest):
    z = zlib.compress(bytes(data), 9)
    return b"\x00\x06\x01\x00" + struct.pack("<II", len(z), len(data)) + rest + z


def rebuild_editor_data(editor, en_total):
    """Devuelve el archivo 424 con la lista de cabellos ampliada y el plan de IDs nuevos."""
    n0 = struct.unpack("<I", editor[:4])[0]
    if n0 != 3:
        sys.exit("El archivo 424 no tiene 3 partes")
    o0, o1, o2 = struct.unpack("<3I", editor[8:20])
    p0, p1, p2 = bytes(editor[o0:o1]), bytes(editor[o1:o2]), bytes(editor[o2:])
    head = bytearray(editor[:o0])

    nrec, spec, hidden = struct.unpack("<IHH", p1[:8])
    if (nrec, spec, hidden) != (FIRST_SPECIAL + EN_ORIGINAL, EN_ORIGINAL, LOCKED):
        sys.exit("La lista de cabellos del archivo 424 es (%d, %d, %d), no la original; "
                 "¿ya está ampliada?" % (nrec, spec, hidden))
    recs = [bytearray(p1[8 + 8 * i:16 + 8 * i]) for i in range(nrec)]
    trailer = p1[8 + 8 * nrec:]
    for i in range(FIRST_SPECIAL, nrec):
        r = recs[i]
        if r[0] != 12 or (r[2] | r[3] << 8) != i - FIRST_SPECIAL:
            sys.exit("El registro %d de la lista de cabellos no es un EN en su posición" % i)

    new_cnt = en_total - EN_ORIGINAL
    if new_cnt < 0 or LAST_ORIGINAL + new_cnt > MAX_ID:
        sys.exit("EN debe estar entre %d y %d" % (EN_ORIGINAL, EN_ORIGINAL + MAX_ID - LAST_ORIGINAL))

    # Marcas 0/1 por índice especial (el juego lee trailer[índice]); se reordenan igual que los índices
    old_flags = {i - FIRST_SPECIAL: trailer[i - FIRST_SPECIAL] for i in range(FIRST_SPECIAL, nrec)}
    flags = [0] * en_total

    # Índices: los 264 EN visibles conservan el suyo; los nuevos van después; los 10 bloqueados quedan al final
    visible = EN_ORIGINAL - LOCKED
    for k in range(visible):
        flags[k] = old_flags[k]
    for k, i in enumerate(range(LAST_ORIGINAL - LOCKED + 1, LAST_ORIGINAL + 1)):
        idx = en_total - LOCKED + k
        recs[i][2], recs[i][3] = idx & 0xFF, idx >> 8
        flags[idx] = old_flags[i - FIRST_SPECIAL]
    for k in range(new_cnt):
        # Registro EN sencillo (sin variantes) y marca 1: así la lista comprimida cabe en el hueco
        # original del archivo 424 (2 sectores). El juego no admite el 424 si ocupa más.
        idx = visible + k
        flags[idx] = 1
        recs.append(bytearray([12, 0, idx & 0xFF, idx >> 8, 0, 0, 0, 0]))
    trailer = bytes(flags)

    nrec2 = len(recs)
    p1_new = struct.pack("<IHH", nrec2, en_total, hidden) + b"".join(bytes(r) for r in recs) + trailer
    p1_new += b"\0" * (align(len(p1_new), 16) - len(p1_new))
    if len(trailer) < en_total:
        sys.exit("La lista de marcas es más corta que los índices")

    n1 = o0 + len(p0)
    n2 = n1 + len(p1_new)
    struct.pack_into("<3I", head, 8, o0, n1, n2)
    return bytes(head) + p0 + p1_new + p2, nrec2, new_cnt


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    opts = [a for a in sys.argv[1:] if a.startswith("--")]
    if len(args) != 3:
        sys.exit(__doc__)
    afs_in, xbe_in, out_dir = args
    en_opt = None
    for o in opts:
        if o.startswith("--en="):
            en_opt = int(o[5:])
    os.makedirs(out_dir, exist_ok=True)

    xbe = bytearray(open(xbe_in, "rb").read())
    n, toc, names = read_afs(afs_in)
    xbe_n = struct.unpack("<I", xbe[XBE_COUNT:XBE_COUNT + 4])[0]
    if xbe_n != n:
        sys.exit("El XBE espera %d archivos en el 0_text y este tiene %d. ¿Es el mismo par de archivos?" % (xbe_n, n))
    hair = list(struct.unpack("<12I", xbe[XBE_HAIR:XBE_HAIR + 48]))
    if any(hair[i] != 0x10000 + HAIR_BASE for i in (0, 1, 2, 3, 8, 9, 10, 11)):
        sys.exit("Este XBE ya tiene los cabellos movidos")

    src = open(afs_in, "rb")
    editor, editor_rest = unpack_entry(read_entry(src, toc, EDITOR_DATA))
    p0 = struct.unpack("<I", editor[8:12])[0]
    face_counts = struct.unpack("<4H", editor[p0:p0 + 8])
    en_total = en_opt if en_opt else sum(face_counts)
    editor_new, nrec2, new_cnt = rebuild_editor_data(editor, en_total)
    editor_new = pack_entry(editor_new, editor_rest)

    # Bloque nuevo: ID -> archivo de origen
    sources = [HAIR_BASE + i for i in range(LAST_ORIGINAL + 1)]
    sources += [TEXTURED[k % len(TEXTURED)] for k in range(new_cnt)]
    block_base = n
    n2 = n + len(sources)

    used = [o for o, s in toc if s]
    first = min(used)
    # El 0_text original tiene relleno (no es cero) entre el final del índice y el primer archivo: se conserva
    src.seek(8 + 8 * n + 8)
    pad = src.read(first - (8 + 8 * n + 8))
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

        o, s = toc[EDITOR_DATA]
        nxt = min([x for x in used if x > o] + [align(end_old)])
        if align(len(editor_new)) > nxt - o:
            sys.exit("El archivo 424 ampliado ocupa %d bytes y no cabe en su hueco de %d: usa menos EN (--en=N)" % (len(editor_new), nxt - o))
        if align(len(editor_new)) <= nxt - o:
            out.seek(o + delta)
            out.write(editor_new + b"\0" * (align(len(editor_new)) - len(editor_new)))
            new_toc[EDITOR_DATA] = (o + delta, len(editor_new))
            out.seek(0, 2)
        else:
            pos = out.tell()
            out.write(editor_new + b"\0" * (align(len(editor_new)) - len(editor_new)))
            new_toc[EDITOR_DATA] = (pos, len(editor_new))

        for i in sources:
            data = read_entry(src, toc, i)
            pos = out.tell()
            out.write(data + b"\0" * (align(len(data)) - len(data)))
            new_toc.append((pos, len(data)))

        out.seek(0)
        out.write(b"AFS\0" + struct.pack("<I", n2))
        for o2, s2 in new_toc:
            out.write(struct.pack("<II", o2, s2))
        out.write(struct.pack("<II", *name_ptr))
        room = head_size - (8 + 8 * n2 + 8)
        out.write(pad[:max(room, 0)])

    for i in (0, 1, 2, 3, 8, 9, 10, 11):
        struct.pack_into("<I", xbe, XBE_HAIR + 4 * i, 0x10000 + block_base)
    struct.pack_into("<I", xbe, XBE_COUNT, n2)
    out_xbe = os.path.join(out_dir, os.path.basename(xbe_in))
    open(out_xbe, "wb").write(xbe)

    out_csv = os.path.join(out_dir, "cabellos_ampliados.csv")
    with open(out_csv, "w", encoding="utf-8-sig") as t:
        t.write("ID DE HAIR;ARCHIVO NUEVO;COPIA DE;TIPO\r\n")
        for i, s in enumerate(sources):
            kind = ("normal" if i < FIRST_SPECIAL else "especial (EN)") if i <= LAST_ORIGINAL else "especial (EN) nuevo"
            t.write("%d;%d;%d;%s\r\n" % (i, block_base + i, s, kind))

    print("0_text.afs: %d -> %d archivos (%s)" % (n, n2, out_afs))
    print("Cabellos: bloque %d-%d (IDs 0-%d). EN: %d (%d del juego + %d nuevos, IDs %d-%d)" % (
        block_base, n2 - 1, len(sources) - 1, en_total, EN_ORIGINAL, new_cnt, LAST_ORIGINAL + 1, len(sources) - 1))
    print("XBE: %s" % out_xbe)
    print("Tabla: %s" % out_csv)


if __name__ == "__main__":
    main()
