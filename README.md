# PES-5-XBOX-Editor-ANDROID

App Android en Kotlin para editar el option file de **PES 5 de Xbox** (`KONAMI-XBOX-PES5OPT.xsv`). También abre el de Winning Eleven 9 de Xbox.

## Descarga

APK de la última compilación de la rama `app-editor`:
<https://github.com/Yotakon/PES-5-XBOX-Editor-ANDROID/releases/download/ultima-app-editor/PES5XboxEditor.apk>

**Haz siempre una copia de tu partida antes de editarla.**

### Actualizar

Todas las compilaciones van firmadas con la misma clave (`firma/pes5editor.jks`) y cada una tiene un número de versión mayor, así que el APK nuevo se instala encima del anterior sin perder los ajustes ni la tabla de equivalencias. La app comprueba una vez al día si hay una versión nueva y avisa; también se puede comprobar en el menú con **Buscar actualizaciones**.

La clave de firma está en el repositorio para que GitHub Actions pueda usarla. Sirve para una app de uso personal; no la reutilices para otras apps.

## Uso

1. Copia la partida de la Xbox al móvil por FTP. En la consola está en `E:\UDATA\4b4e0030\5252897584A1\KONAMI-XBOX-PES5OPT.xsv`.
2. En la app, toca **Abrir option file** y elige el `.xsv`.
3. Elige un equipo en el desplegable, o "Todos los jugadores", y busca por nombre o ID.
4. Toca un jugador para cambiar el **tipo de cara** (creada / especial / normal), el **número de cara**, el **color de piel** y el **ID de pelo**.
5. Guarda con **Guardar** (reemplaza el archivo) o con **Guardar como…**, y vuelve a copiarlo a la Xbox con el nombre `KONAMI-XBOX-PES5OPT.xsv`.
6. Para trabajar con otro archivo, usa **Cerrar option file** en el menú. Si hay cambios sin guardar, la app pregunta si guardarlos antes de cerrar.

Al guardar, la app recalcula las sumas de control y la firma de Xbox.

### Pestañas

- **Jugadores**: lista por equipo con búsqueda. Toca un jugador para editar todos sus datos: nombre, nombre en la camiseta, nacionalidad, edad, altura, peso, pie, posiciones, las 26 habilidades, estilos, habilidades especiales, FACE, HAIR (con su color), cuerpo y accesorios.
- **Transferencias**: dos listas de equipos lado a lado. Toca un jugador y después el hueco de destino para moverlo o intercambiarlo. Si una lista es "Todos los jugadores", el jugador elegido se ficha en el hueco de la otra. Con la opción marcada, al fichar en un club se quita de su club anterior. Mantén pulsado un jugador para liberarlo, cambiar el dorsal o editar la cara.
- **Equipos**: nombre, abreviatura, dos colores, estadio y escudo de cada club. Uniforme de clubes y selecciones: logos del uniforme, copiar el uniforme de otro equipo e importarlo desde otro option file (con sus logos y su escudo).
- **Escudos y logos**: añadir, reemplazar, exportar a PNG y borrar escudos (64×64, de 128 o 16 colores) y los 80 logos (32×32, 16 colores). Cualquier imagen se ajusta al tamaño y se reduce a los colores necesarios.
- **Estadios y ligas**: nombres de los 35 estadios y de las 28 ligas y copas.

### Relinkeo por archivo del 0_text

En el editor del jugador, el botón **Relinkear por archivo del 0_text** pide el número de archivo de FACE y de HAIR (contando desde 0, como GGS) y la piel. La app deja al jugador con **FACE normal** y calcula el número de FACE y el ID de HAIR para que el juego cargue esos archivos. Nunca pone FACE especial, porque con ella el editor del juego no deja editar la cara. La lista de jugadores muestra qué archivos carga cada uno.

Así elige el juego los archivos (tablas del `PES5.xbe` de Xbox, offsets `0x578E18` y `0x578A24`):

| Tipo de FACE | Archivo de FACE | Archivo de HAIR |
|---|---|---|
| Especial | base especial de la piel + número - 1 | archivo de FACE + 2559 (no usa el ID de HAIR) |
| Normal | base normal de la piel + número - 1 | 2829 + ID de HAIR |
| Creada | se genera con los parámetros | 2829 + ID de HAIR |

| Piel | Base normal | Archivos de FACE normal que alcanza |
|---|---|---|
| 1 (W) | 1959 | 1959-2362 |
| 2 (Y) | 2224 | 2224-2362 |
| 3 (R) | 1902 | 1902-2362 |
| 4 (K) | 1870 | 1870-2362 |

- **HAIR:** con FACE normal se puede cargar cualquier archivo del 2829 al 4876, también los de la zona de HAIR especiales (4149-4428, ID de HAIR 1320-1599). Cargarlos así no está probado en el juego.
- **FACE:** con FACE normal el juego no llega a la zona de FACE especiales (1590-1869). Si escribes uno de esos archivos, la app te dice cuál es su copia normal. Para usar un modelo nuevo, ponlo en un archivo de FACE normal.
- Si el jugador tenía FACE especial y no escribes archivo de FACE, pasa a su copia normal de la tabla de equivalencias. Por ejemplo, Ronaldinho pasa de la FACE especial 1614 a la normal 1910.
- La piel también cambia el color del cuerpo. La piel 4 (K) llega a todas las FACE normales.

### 0_text ampliado: FACE especiales dentro de las normales

`herramientas/ampliar_caras.py` añade a cada piel, como FACE normales, sus FACE especiales. Así un jugador con FACE normal (editable en el juego) puede cargar el modelo de una especial.

```
python3 herramientas/ampliar_caras.py 0_text.afs PES5.xbe salida
```

- **0_text.afs:** al final crea un bloque por piel con sus FACE normales, en el mismo orden, seguidas de sus especiales. También cambia el archivo 424, que dice al editor del juego cuántas FACE normales hay por piel. Los demás archivos no cambian: solo se desplazan 6 KB para que quepa el índice nuevo.
- **PES5.xbe:** las bases de FACE normal (offset `0x578E38`) apuntan a los bloques nuevos y el número de archivos del 0_text (offset `0x4EF7E8`) pasa de 11778 a 12551.
- Hay que usar los dos archivos juntos. La tabla `tablas/caras_ampliadas.csv` dice de qué archivo es copia cada FACE nueva.

| Piel | Archivos | FACE normales | Especiales (normal = N + especial) |
|---|---|---|---|
| 1 (W) | 11778-12188 | 1-265 | 266-411 |
| 2 (Y) | 12189-12411 | 1-139 | 140-223 |
| 3 (R) | 12412-12500 | 1-57 | 58-89 |
| 4 (K) | 12501-12550 | 1-32 | 33-50 |

Por ejemplo, la FACE especial de Ronaldinho (1614, piel 3 número 7) es la FACE normal 64 de la piel 3, archivo 12475.

En la app, marca **Uso el 0_text ampliado** en Ajustes. Entonces:
- El relinkeo acepta los archivos del 0_text original (1590-2362) y usa su copia en el bloque nuevo.
- **Quitar caras especiales** pasa a cada jugador a la copia exacta de su FACE especial.
- Con el 0_text ampliado, para cambiar una FACE normal en GGS hay que reemplazar su archivo del bloque nuevo, no el original.

Sin probar en el juego: que el editor del juego muestre y deje elegir las FACE nuevas de cada piel.

### Cabellos EN ampliados: tantos como caras en el editor

`herramientas/ampliar_cabellos.py` deja en la lista de cabellos del editor del juego tantos cabellos de tipo Especial (EN) como caras tiene el editor (773 con el 0_text de caras ampliado). Se ejecuta después de `ampliar_caras.py`, con el 0_text y el XBE que salieron de ahí:

```
python3 herramientas/ampliar_caras.py 0_text.afs PES5.xbe salida
python3 herramientas/ampliar_cabellos.py salida/0_text.afs salida/PES5.xbe salida_cabellos
```

Los EN son los cabellos que el editor lista como "Especial": los IDs 1026-1299 (274, archivos 3855-4128). Con esa lista no salen los cabellos de las FACE especiales (4149-4428), que solo se cargan con su cara, ni los 20 modelos con gorra (4129-4148). Como el juego carga el archivo 2829 + ID de HAIR, los IDs por encima de 1299 caerían en esos archivos. Por eso los cabellos se copian a un bloque nuevo y el inicio de los cabellos se mueve ahí:

| IDs de HAIR | Archivos (0_text con cabellos ampliados) | Qué son |
|---|---|---|
| 0-1299 | 12551-13850 | Copia de los archivos 2829-4128: los 1026 normales y los 274 EN del juego |
| 1300-1798 | 13851-14349 | 499 EN nuevos: clones de relleno de los 256 EN con textura, para reemplazarlos |

- **0_text.afs:** pasa de 12551 a 14350 archivos. El archivo 424 (lista del editor) queda con 1799 cabellos, 773 de ellos EN, y el resto de archivos no cambia.
- **PES5.xbe:** la tabla de cabellos (offset `0x578A24`) apunta al bloque nuevo para FACE creada y normal, y el número de archivos (offset `0x4EF7E8`) pasa a 14350. Los cabellos de las FACE especiales no cambian: siguen siendo archivo de FACE + 2559.
- **Los 10 EN bloqueados:** el juego oculta los 10 últimos EN de la lista hasta que se desbloquean. Siguen siendo los últimos por orden en el menú, así que los EN nuevos se ven siempre y los bloqueados quedan al final.
- La tabla `tablas/cabellos_ampliados.csv` dice de qué archivo es copia cada ID.
- Para cambiar un cabello con GGS hay que reemplazar su copia en el bloque nuevo, no el archivo 2829-4128 original, que ya no se carga.

En la app, marca también **...y también los HAIR ampliados** en Ajustes. Entonces el relinkeo calcula el archivo de HAIR en el bloque nuevo, acepta los archivos 2829-4128 originales (usa su copia) y avisa con el EN equivalente cuando pides un cabello de FACE especial, porque ese no está en el bloque.

Sin probar en el juego: que el editor muestre los 773 EN, que no falle con ellos y que el bloqueo siga afectando a los mismos 10.

### Menú

- **Exportar / importar caras por CSV**: columnas `ID`, `TIPO CARA` (0 creada, 1 especial, 2 normal), `NUMERO CARA` (1–512), `PIEL` (1–4), `PELO` (0–2047), `ARCHIVO FACE` y `ARCHIVO HAIR`. Las celdas vacías no cambian nada. Si `ARCHIVO FACE` o `ARCHIVO HAIR` cambian el archivo que se carga, la app recalcula el resto igual que el botón de relinkeo y deja la FACE normal.
- **Quitar caras especiales**: pasa a cara normal a todos los jugadores con cara especial, o solo a los del equipo elegido.
  - Cada jugador pasa a la cara normal que es copia de su cara especial, según la tabla de equivalencias incluida en la app.
  - Por defecto guarda el resultado en un option file aparte.
  - Al terminar puedes guardar un informe CSV. Si importas ese informe con *Importar caras*, se deshacen los cambios.
- **Exportar este equipo / todos los equipos**: archivos `.pes5eq` con la plantilla, los dorsales, la formación (posiciones, estrategia, capitán y lanzadores), los datos de los jugadores y el nombre del club. Son compatibles con el editor de escritorio.
- **Importar equipo**: carga un `.pes5eq` en una selección o en un club con el mismo número de jugadores.
- **Posiciones de los datos de cara**: permite corregir dónde están los campos de cara sin actualizar la app.

## De dónde salen los datos

- **Formato del `.xsv`, sumas de control, plantillas, transferencias, formaciones, clubes, escudos, logos, estadios y ligas**: [PESFan Editor 5](https://github.com/PeterC10/PES5Editor). Además de las firmas de PES 5 y WE9 de PESFan, la app acepta la firma con clave cero.
- **Campos de cara y pelo**: PESFan Editor no los edita. Salen de [PES5_WE9_LE_OF_Team_Editor](https://github.com/moth1995/PES5_WE9_LE_OF_Team_Editor) y todavía no se han comprobado en una partida de Xbox.

| Campo | Posición, desplazamiento de bits, máscara |
|---|---|
| Tipo de cara | 60, 2, 3 |
| Número de cara | 53, 3, 511 |
| Piel | 43, 1, 3 |
| Pelo | 45, 0, 2047 |

## Compilar

GitHub Actions compila y prueba la app en cada push (`.github/workflows/android.yml`). En local, con JDK 17 y Gradle 8.9, ejecuta `gradle assembleDebug`.

## Próxima fase

Uniformes de los equipos e importar desde otro option file de Xbox.
