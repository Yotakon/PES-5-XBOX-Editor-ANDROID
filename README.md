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

Al guardar, la app recalcula las sumas de control y la firma de Xbox.

### Pestañas

- **Jugadores**: lista por equipo con búsqueda. Toca un jugador para editar todos sus datos: nombre, nombre en la camiseta, nacionalidad, edad, altura, peso, pie, posiciones, las 26 habilidades, estilos, habilidades especiales, FACE, HAIR (con su color), cuerpo y accesorios.
- **Transferencias**: dos listas de equipos lado a lado. Toca un jugador y después el hueco de destino para moverlo o intercambiarlo. Si una lista es "Todos los jugadores", el jugador elegido se ficha en el hueco de la otra. Con la opción marcada, al fichar en un club se quita de su club anterior. Mantén pulsado un jugador para liberarlo, cambiar el dorsal o editar la cara.
- **Equipos**: nombre, abreviatura, dos colores, estadio y escudo de cada club. Uniforme de clubes y selecciones: logos del uniforme, copiar el uniforme de otro equipo e importarlo desde otro option file (con sus logos y su escudo).
- **Escudos y logos**: añadir, reemplazar, exportar a PNG y borrar escudos (64×64, de 128 o 16 colores) y los 80 logos (32×32, 16 colores). Cualquier imagen se ajusta al tamaño y se reduce a los colores necesarios.
- **Estadios y ligas**: nombres de los 35 estadios y de las 28 ligas y copas.

### Relinkeo por archivo del 0_text

En el editor del jugador, el botón **Relinkear por archivo del 0_text** pide el número de archivo de FACE y de HAIR (contando desde 0, como GGS) y la piel. La app calcula el tipo de FACE, el número y el ID de HAIR que hacen que el juego cargue esos archivos. La lista de jugadores muestra qué archivos carga cada uno.

Así elige el juego los archivos (tablas del `PES5.xbe` de Xbox, offsets `0x578E18` y `0x578A24`):

| Tipo de FACE | Archivo de FACE | Archivo de HAIR |
|---|---|---|
| Especial | base especial de la piel + número - 1 | archivo de FACE + 2559 (no usa el ID de HAIR) |
| Normal | base normal de la piel + número - 1 | 2829 + ID de HAIR |
| Creada | se genera con los parámetros | 2829 + ID de HAIR |

| Piel | Base especial | Base normal |
|---|---|---|
| 1 (W) | 1640 | 1959 |
| 2 (Y) | 1786 | 2224 |
| 3 (R) | 1608 | 1902 |
| 4 (K) | 1590 | 1870 |

El número de FACE va de 1 a 512, así que cada piel solo llega a los archivos que hay entre su base y su base + 511:

- **Archivos 1590-1869 (FACE especiales):** el HAIR va unido a la FACE (archivo + 2559, del 4149 al 4428). Para cambiar ese HAIR hay que reemplazar su archivo en el 0_text.
- **Archivos 1870-2362 (FACE normales):** el HAIR se elige aparte con cualquier archivo del 2829 al 4876. Los HAIR del 4149 al 4428 cargados por ID de HAIR no están probados en el juego.
- La piel también cambia el color del cuerpo. La piel 4 (K) llega a todos los archivos de FACE. Las demás no llegan a los primeros: por ejemplo, la FACE 1910 (normal de Ronaldinho) solo se carga con piel 3 (R) o 4 (K).

Para meter caras y pelos nuevos, reemplaza en el 0_text un hueco de FACE especial y su HAIR (archivo + 2559), o una FACE normal y cualquier HAIR, y relinkea al jugador con esos números.

### Menú

- **Exportar / importar caras por CSV**: columnas `ID`, `TIPO CARA` (0 creada, 1 especial, 2 normal), `NUMERO CARA` (1–512), `PIEL` (1–4), `PELO` (0–2047), `ARCHIVO FACE` y `ARCHIVO HAIR`. Las celdas vacías no cambian nada. Si `ARCHIVO FACE` o `ARCHIVO HAIR` cambian el archivo que se carga, la app recalcula el resto igual que el botón de relinkeo.
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
