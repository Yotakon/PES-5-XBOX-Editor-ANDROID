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

- **Jugadores**: lista por equipo con búsqueda; toca un jugador para editar su cara y pelo.
- **Transferencias**: dos listas de equipos lado a lado. Toca un jugador y después el hueco de destino para moverlo o intercambiarlo. Si una lista es "Todos los jugadores", el jugador elegido se ficha en el hueco de la otra. Con la opción marcada, al fichar en un club se quita de su club anterior. Mantén pulsado un jugador para liberarlo, cambiar el dorsal o editar la cara.
- **Equipos**: nombre, abreviatura, dos colores, estadio y escudo de cada club.
- **Escudos y logos**: añadir, reemplazar, exportar a PNG y borrar escudos (64×64, de 128 o 16 colores) y los 80 logos (32×32, 16 colores). Cualquier imagen se ajusta al tamaño y se reduce a los colores necesarios.
- **Estadios y ligas**: nombres de los 35 estadios y de las 28 ligas y copas.

### Relinkeo

Poner a un jugador **cara normal** con el número de la cara que quieres es relinkearlo. Sin la marca de especial, el juego no le quita la cara al editarlo.

### Menú

- **Exportar / importar caras por CSV**: columnas `ID`, `TIPO CARA` (0 creada, 1 especial, 2 normal), `NUMERO CARA` (1–512), `PIEL` (1–4) y `PELO` (0–2047). Las celdas vacías no cambian nada.
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
