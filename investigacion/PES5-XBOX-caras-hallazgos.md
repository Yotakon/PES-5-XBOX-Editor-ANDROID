# PES 5 Xbox: cómo se asignan cara y pelo a un jugador

Fuentes analizadas: `0_text.afs` (release 1.1 de `Yotakon/0TEXT-PES2010-PS2`) y `PES5.xbe` (título `ProEvolutionSoccer5`, ID `0x4B4E0030`).
Todos los números de entrada del AFS cuentan desde 0.

## 1. Registro de jugador (base de datos)

- Está en la entrada 28 del `0_text.afs`, bloque 0, comprimido con zlib: 4.896 registros de 124 bytes.
- El número de registro es el ID del jugador. Ronaldinho = 999.
- Los campos de apariencia están en los mismos offsets que en PES 5 PC (los que usa el `fserv` de Kitserver 5):

| Campo | Offset y bits | Valores |
|---|---|---|
| Color de piel | byte 90, bits 1-2: `(b[90] >> 1) & 3` | 0 = W, 1 = Y, 2 = R, 3 = K |
| FaceID | word 100, bits 3-11: `(w[100] >> 3) & 0x1FF` | índice dentro del grupo |
| Tipo de cara (faceset) | byte 107, bits 2-3: `(b[107] >> 2) & 3` | 0 = generada, 1 = especial, 2 = cara "normal" |

## 2. De FaceID a entrada del AFS (código del XBE)

- Función de cara: VA `0x314830`. Calcula `ID de archivo = FaceID + TABLA_CARA[piel + 4 * faceset]`. Con faceset 0, la piel se fuerza a 0.
- Los ID de archivo valen `0x10000 + entrada`, y el índice 1 corresponde a `0_text.afs`.
- `TABLA_CARA`: VA `0x7D88B8`, offset `0x578E18` en el archivo `.xbe`.
- `TABLA_PELO`: VA `0x7D84C4`, offset `0x578A24`. Para faceset 1, el pelo es `inicio[piel] + FaceID`. Para los demás casos es `2829 + HairID`.

| faceset | W | Y | R | K |
|---|---|---|---|---|
| Cara 0 (generada) | 2364 | 2364 | 2364 | 2364 |
| Cara 1 (especial) | 1640 (146) | 1786 (84) | 1608 (32) | 1590 (18) |
| Cara 2 (normal) | 1959 (265) | 2224 (139) | 1902 (57) | 1870 (32) |
| Pelo 0 y 2 | 2829 + HairID | 2829 + HairID | 2829 + HairID | 2829 + HairID |
| Pelo 1 (especial) | 4199 | 4345 | 4167 | 4149 |

Entre paréntesis va el número de huecos de cada grupo.

Comprobación con Ronaldinho: piel R, faceset 1, FaceID 6. La cara es 1608 + 6 = 1614 y el pelo 4167 + 6 = 4173. Las dos entradas coinciden byte a byte con los `.bin` de `PES-XBOX-face-converter`.

## 3. Huecos especiales sin uso en la base de datos

Formato: FaceID → entrada de cara / entrada de pelo. Ningún registro de la entrada 28 usa estos huecos. Todavía no se ha comprobado si los usan el Option File o la Liga Master.

- W: 7→1647/4206, 22→1662/4221, 26→1666/4225, 54→1694/4253, 122→1762/4321, 137→1777/4336
- Y: 42 huecos, entre ellos 18→1804/4363 y 83→1869/4428 (la lista completa sale con el script)
- R: 2→1610/4169
- K: 11→1601/4160, 14→1604/4163

## 4. Límites

- El XBE fija el número de entradas de `0_text.afs` en 11778. Está en la VA `0x74F908`, offset `0x4EF7E8`.
- FaceID ocupa 9 bits, así que cada grupo admite como máximo 512 caras.

## 5. Option File (`KONAMI-XBOX-PES5OPT.xsv`)

- Guarda una copia completa de la tabla de jugadores: los 4.896 registros de 124 bytes, también los de jugadores que no se han editado.
  - Empieza en el offset `0xBC08`. El jugador con ID n está en `0xBC08 + 124 * n`.
  - Los offsets de los campos son los mismos que en la entrada 28 del AFS.
- También contiene el bloque 2 de la entrada 28, idéntico, en `0xA5B42`. El bloque 4 aparece modificado en `0xA3BD6`.
- **Marca de apariencia editada:** byte 111 del registro, bit 5 (`0x20`).
  - En la base de datos del AFS vale 0 en los 4.896 jugadores.
  - En este Option File vale 1 en 28 jugadores, que son justo los que tienen piel, faceset o FaceID distintos a los del AFS.
  - En esos jugadores el byte 112 (bits 0-5) también deja de valer 0. Todavía no sé qué significa.
- En los jugadores que perdieron la cara real, el faceset pasó de 2 a 0. El FaceID se conservó.
  - Voronin (ID 700, offset `0x20F18`): byte 107 `59` → `51`, byte 111 `00` → `20`.
- La función que elige la cara (VA `0x314830`) solo lee piel, faceset y FaceID. No comprueba la marca.
  - La única rutina encontrada que toca ese bit es la que guarda la edición (VA `0x5CC2C8`). Ahí aparece como el bit `0x800` de un campo de 12 bits que empieza en el bit 18 del dword `0x6C`, junto a un campo de 9 bits que parece ser la nacionalidad.

## 6. Editor interno y caras especiales (sección `OA019_CD` del XBE)

1. **Al entrar en el editor** (VA `0x5E3630`):
   - Llama a `0x5E32C0`, que convierte la cara especial (faceset 1) en su cara "similar". Pone faceset 2 y FaceID = `similar(piel, FaceID)`, valor que saca de una tabla en memoria (función `0x5E2BE0`, contadores en `0xCB8FAC`).
   - Inmediatamente después escribe el registro convertido en la tabla de jugadores (`call 0x589A60`). Por eso al salir el juego detecta un cambio aunque no se haya tocado nada.
2. **Al salir por la opción que guarda** (llamadas desde `0x5E5841` y `0x5E5927`):
   - La función `0x5E3320` comprueba que el original tenía faceset 1 y el editado tiene faceset 2.
   - También comprueba que la piel es la misma, que el FaceID es igual a la cara similar y que unos 40 campos de apariencia no han cambiado.
   - Si todo coincide, `0x5E2C10` vuelve a copiar el faceset y los campos originales, con lo que se recupera la cara especial.
3. **Al salir por "No"** no se ejecuta esa comprobación. El jugador se queda con la cara similar (faceset 2) y, si se guarda el Option File, el cambio queda permanente.

**Parche propuesto (sin probar):** en el offset `0x910ED4` del archivo `.xbe`, cambiar `E8 87 FC FF FF` por `90 90 90 90 90`. Así el editor no convierte la cara especial al entrar. Esa secuencia de bytes es única en el ejecutable.

## 7. Código que cambia la cara por su cuenta

Revisé todo el código que escribe el faceset (`0x0C000000` del dword `0x68`) o el FaceID (`0xFF8` del dword `0x64`).

| VA | Qué hace | ¿Toca el registro guardado? |
|---|---|---|
| `0x5E32C0` (llamada en `0x5E3634`) | Al entrar al editor convierte la cara especial en la similar | **Sí**. Es la causa del problema |
| `0x5E2C10` (desde `0x5E3320`) | Devuelve la cara especial si contestas "Sí" | Sí, pero solo después de la conversión |
| `0x58A690` (desde `0x5E48B0`) | Copia temporal con la cara similar para la vista previa 3D del editor | No (usa el búfer `0x9AB314`) |
| `0x5E3E80` | Aplica lo que el usuario elige en el menú de cara | Sí, pero solo es la edición manual |
| `0x5902D8` | Valores por defecto de un jugador creado | Solo al crear un jugador |
| `0x314830` | Carga la cara en el partido | No modifica nada, solo lee |

## 8. Parches

| Archivo en `parches/` | Cambios | MD5 |
|---|---|---|
| `PES5_editor_sin_conversion.xbe` | `0x910ED4`: `E8 87 FC FF FF` → `90 90 90 90 90` | `3d63a2d361e2cfcfeb4b46a33c0f8933` |
| `PES5_editor_sin_conversion_y_preview.xbe` | Lo anterior + `0x8B7F80`: `81 E2 FF FF FF FB` → `90`×6, `0x8B7F8C`: `81 CA 00 00 00 08` → `90`×6, `0x8B7F92`: `33 C1` → `90 90` | `d5022a9c5472bf662efc2369033c6ef7` |

MD5 del original: `b574d29e94f57b48938d9587619e39b9`.

Con el parche 1, el registro ya no se modifica al entrar al editor. Al no hacerse la conversión, la restauración de `0x5E3320` deja de activarse, porque exige que el editado tenga faceset 2.

El parche 2 hace además que la vista previa use la cara especial en lugar de la similar. No está comprobado que el visor del editor admita caras especiales.

## 9. Resultado de la prueba del parche 1 (no recomendado)

En la prueba del usuario, con `PES5_editor_sin_conversion.xbe` el jugador conserva la cara especial al pulsar OK y la cara deja de poder editarse.

Explicación:
- El editor guarda el registro en dos ranuras de 124 bytes (`0x58CD10`, base `0x8FC708`):
  - Ranura 0: copia de trabajo.
  - Ranura 1: referencia, copiada del registro actual del jugador al entrar al editor (`0x5E7FE8`).
- El menú de cara solo admite faceset 0 (construida) y 2 (predefinida). La conversión de `0x5E32C0` es lo que permite editar a un jugador con cara especial: lo pasa a su cara similar (faceset 2).
- Sin esa conversión la ranura 0 sigue con faceset 1. El OK la escribe tal cual y el menú de cara queda bloqueado.

Conclusión: el juego carga la cara especial solo cuando el byte 107 del registro tiene faceset 1. Para que un jugador deje de usarla de forma permanente hay que cambiar ese byte en el Option File (y en la base de datos), usando el XBE original. La restauración de `0x5E3320` solo se activa si la ranura de referencia tiene faceset 1.

## 10. Causa confirmada: la restauración del OK (prueba con Ronaldinho)

Resultado de la prueba del usuario, con el Option File ya procesado por "Quitar FACE especiales" (Ronaldinho con faceset 2 y FaceID 9):
- En el partido no se carga la cara especial, así que el Option File está bien.
- En el editor, al pulsar OK sin cambios, vuelve la cara especial.
- Con FaceID 10 no vuelve.
- Si se cambian otros parámetros, sigue volviendo. Deja de volver si se cambia el ancho o el alto de la cabeza.

Esto coincide con `0x5E3320`, que compara la ranura 0 (editada) con la ranura 1 (referencia, de la plantilla `0xCB51A8`). Restaura la cara especial si se cumplen estas condiciones:
- la referencia tiene faceset 1 y la editada faceset 2,
- la piel es la misma,
- el FaceID es igual a `similar(piel, FaceID especial)`,
- coinciden unos 40 campos de apariencia. Entre ellos están los bits 19-22 y 23-26 del dword `0x58` (probablemente el ancho y el alto de la cabeza). Los parámetros que no están en esa lista no impiden la restauración.

La referencia sigue con faceset 1 aunque el Option File tenga 2. Por tanto no sale del Option File; lo más probable es que salga de los datos originales. No está verificado.

## 11. Parche final (`parches/`)

| Archivo | Cambios | MD5 |
|---|---|---|
| `PES5_sin_restauracion.xbe` | `0x910BC0`: `8B` → `C3` (`0x5E3320` sale sin hacer nada) | `504cf8c17cb8062285735ab0289a7b78` |

Los dos llamadores de `0x5E3320` (`0x5E5841` y `0x5E5927`) no usan su valor de retorno. Salir al principio equivale a que la comprobación falle.

**Estado: probado por el usuario y funciona.** Se usa junto con un Option File procesado con "Quitar FACE especiales" del editor Android. Con eso el editor del juego ya no devuelve la cara especial al pulsar OK.

Se descartó un segundo parche que además anulaba la conversión de `0x910ED4`, porque no hace falta. Es la misma conversión que se describe en la sección 9 como no recomendada.
