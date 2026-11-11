package com.yotakon.pes5editor.core

/**
 * Todos los datos editables de un jugador.
 * - Datos, posiciones, habilidades, estilos y habilidades especiales: Stats.java de PESFan Editor 5.
 * - FACE, HAIR, cuerpo y accesorios: player_data.py de PES5_WE9_LE_OF_Team_Editor (moth1995).
 * Las posiciones son relativas a los datos del jugador (registro + 48), igual que [Field].
 */
object PlayerFields {

    enum class Section(val label: String) {
        DATA("Datos"), POSITIONS("Posiciones"), ABILITIES("Habilidades (1-99)"), STYLES("Estilos"),
        SPECIAL("Habilidades especiales"), FACE("FACE"), HAIR("HAIR"), BODY("Cuerpo"), ACCESSORIES("Accesorios"),
    }

    sealed class Kind {
        /** Número mostrado = factor * valor guardado + suma. */
        data class Num(val factor: Int = 1, val add: Int = 0) : Kind()
        data class Choice(val labels: List<String>) : Kind()
        object Bool : Kind()
    }

    data class PField(val key: String, val label: String, val field: Field, val kind: Kind, val section: Section) {
        val displayRange: IntRange
            get() = when (kind) {
                is Kind.Num -> {
                    val a = kind.add
                    val b = kind.factor * field.mask + kind.add
                    minOf(a, b)..maxOf(a, b)
                }
                is Kind.Choice -> 0 until kind.labels.size
                Kind.Bool -> 0..1
            }

        fun get(of: OptionFile, id: Int): Int {
            val raw = field.get(of, id)
            return if (kind is Kind.Num) kind.factor * raw + kind.add else raw
        }

        fun set(of: OptionFile, id: Int, display: Int) {
            val raw = if (kind is Kind.Num) (display - kind.add) * kind.factor else display
            require(raw in 0..field.mask) { "$label fuera de rango" }
            field.set(of, id, raw)
        }
    }

    val NATIONS = listOf(
        "Austria", "Belgium", "Bulgaria", "Croatia", "Czech Republic", "Denmark", "England", "Finland", "France", "Germany",
        "Greece", "Hungary", "Ireland", "Italy", "Latvia", "Netherlands", "Northern Ireland", "Norway", "Poland", "Portugal",
        "Romania", "Russia", "Scotland", "Serbia and Montenegro", "Slovakia", "Slovenia", "Spain", "Sweden", "Switzerland",
        "Turkey", "Ukraine", "Wales", "Cameroon", "Cote d'Ivoire", "Morocco", "Nigeria", "Senegal", "South Africa", "Tunisia",
        "Costa Rica", "Mexico", "USA", "Argentina", "Brazil", "Chile", "Colombia", "Ecuador", "Paraguay", "Peru", "Uruguay",
        "Venezuela", "China", "Iran", "Japan", "Saudi Arabia", "South Korea", "Australia", "Albania", "Armenia", "Belarus",
        "Bosnia and Herzegovina", "Cyprus", "Georgia", "Estonia", "Faroe Islands", "Iceland", "Israel", "Lithuania",
        "Luxembourg", "Macedonia", "Moldova", "Algeria", "Angola", "Burkina Faso", "Cape Verde", "Congo", "DR Congo", "Egypt",
        "Equatorial Guinea", "Gabon", "Gambia", "Ghana", "Guinea", "Guinea-Bissau", "Liberia", "Libya", "Mali", "Mauritius",
        "Mozambique", "Namibia", "Sierra Leone", "Togo", "Zambia", "Zimbabwe", "Canada", "Grenada", "Guadeloupe", "Guatemala",
        "Honduras", "Jamaica", "Martinique", "Netherlands Antilles", "Panama", "Trinidad and Tobago", "Bolivia", "Guyana",
        "Uzbekistan", "New Zealand", "Free Nationality",
    )
    val POSITIONS = listOf("GK", "?", "CWP", "CBT", "SB", "DM", "WB", "CM", "SM", "AM", "WG", "SS", "CF")

    /** Pie y lado se guardan en dos campos; en el editor van juntos como en PESFan. */
    val FOOT = Field(5, 0, 1)
    val SIDE = Field(20, 6, 3)
    val FOOT_SIDE_LABELS = listOf(
        "Pie D / lado D", "Pie D / lado I", "Pie D / ambos lados", "Pie I / lado I", "Pie I / lado D", "Pie I / ambos lados",
    )

    /** Marca de "estadísticas editadas" que PESFan pone al guardar un jugador. */
    val STATS_EDITED = Field(39, 7, 1)

    private val list = ArrayList<PField>()
    private fun put(key: String, label: String, off: Int, sh: Int, mask: Int, kind: Kind, s: Section) {
        list += PField(key, label, Field(off, sh, mask), kind, s)
    }
    private fun num(key: String, label: String, off: Int, sh: Int, mask: Int, s: Section, add: Int = 0) =
        put(key, label, off, sh, mask, Kind.Num(1, add), s)
    /** moth1995 guarda algunos ajustes invertidos: mostrado = centro - guardado. */
    private fun inv(key: String, label: String, off: Int, sh: Int, mask: Int, s: Section, center: Int = 3) =
        put(key, label, off, sh, mask, Kind.Num(-1, center), s)
    private fun bool(key: String, label: String, off: Int, sh: Int, s: Section) = put(key, label, off, sh, 1, Kind.Bool, s)
    private fun choice(key: String, label: String, off: Int, sh: Int, mask: Int, labels: List<String>, s: Section) =
        put(key, label, off, sh, mask, Kind.Choice(labels), s)

    val ALL: List<PField> by lazy {
        val S = Section.DATA
        choice("nation", "Nacionalidad", 63, 2, 127, NATIONS, S)
        num("age", "Edad", 62, 5, 31, S, add = 15)
        num("height", "Altura (cm)", 41, 0, 63, S, add = 148)
        num("weight", "Peso (kg)", 41, 6, 127, S)
        choice("regpos", "Posición registrada", 6, 4, 15, POSITIONS, S)

        val P = Section.POSITIONS
        bool("gk", "GK", 8, 6, P); bool("cwp", "CWP", 8, 7, P); bool("cbt", "CBT", 12, 4, P); bool("sb", "SB", 12, 5, P)
        bool("dm", "DM", 12, 6, P); bool("wb", "WB", 12, 7, P); bool("cm", "CM", 16, 4, P); bool("sm", "SM", 16, 5, P)
        bool("am", "AM", 16, 6, P); bool("wg", "WG", 16, 7, P); bool("ss", "SS", 20, 4, P); bool("cf", "CF", 20, 5, P)

        val A = Section.ABILITIES
        for ((k, l, o, sh) in listOf(
            Q("attack", "Ataque", 7, 0), Q("defence", "Defensa", 7, 7), Q("balance", "Equilibrio", 9, 0),
            Q("stamina", "Resistencia", 9, 7), Q("speed", "Velocidad", 10, 6), Q("accel", "Aceleración", 11, 5),
            Q("response", "Respuesta", 13, 0), Q("agility", "Agilidad", 13, 7), Q("dribacc", "Precisión de regate", 14, 6),
            Q("dribspe", "Velocidad de regate", 15, 5), Q("spassacc", "Precisión pase corto", 17, 0),
            Q("spassspe", "Velocidad pase corto", 17, 7), Q("lpassacc", "Precisión pase largo", 18, 6),
            Q("lpassspe", "Velocidad pase largo", 19, 5), Q("shotacc", "Precisión de tiro", 21, 0),
            Q("shotpow", "Potencia de tiro", 21, 7), Q("shottec", "Técnica de tiro", 22, 6), Q("fk", "Precisión de falta", 23, 5),
            Q("curling", "Efecto", 25, 0), Q("heading", "Cabeceo", 25, 7), Q("jump", "Salto", 26, 6), Q("tech", "Técnica", 29, 0),
            Q("aggress", "Agresividad", 29, 7), Q("mental", "Mentalidad", 30, 6), Q("gk_abil", "Portero", 31, 5),
            Q("team", "Trabajo en equipo", 33, 0),
        )) num(k, l, o, sh, 127, A)

        val T = Section.STYLES
        num("wfa", "Precisión pie malo (1-8)", 34, 5, 7, T, add = 1)
        num("wff", "Frecuencia pie malo (1-8)", 35, 0, 7, T, add = 1)
        num("consistency", "Regularidad (1-8)", 33, 7, 7, T, add = 1)
        num("condition", "Forma física (1-8)", 34, 2, 7, T, add = 1)
        choice("injury", "Tolerancia a lesiones", 35, 3, 3, listOf("C", "B", "A"), T)
        num("dribsty", "Estilo de regate (1-4)", 6, 0, 3, T, add = 1)
        num("fksty", "Estilo de falta (1-9)", 5, 1, 15, T, add = 1)
        num("pksty", "Estilo de penalti (1-5)", 5, 5, 7, T, add = 1)
        num("dksty", "Estilo de drop kick (1-4)", 6, 2, 3, T, add = 1)
        num("celeb1", "Celebración 1", 37, 1, 127, T)
        num("celeb2", "Celebración 2", 38, 0, 127, T)

        val X = Section.SPECIAL
        bool("drib", "Regate", 24, 4, X); bool("tdrib", "Regate táctico", 24, 5, X); bool("posit", "Colocación", 24, 6, X)
        bool("react", "Reacción", 24, 7, X); bool("play", "Creación de juego", 28, 4, X); bool("pass", "Pase", 28, 5, X)
        bool("score", "Goleador", 28, 6, X); bool("k11", "Mano a mano", 28, 7, X); bool("post", "Pivote", 32, 4, X)
        bool("lines", "Desmarque", 32, 5, X); bool("mid", "Tiro de media distancia", 32, 6, X); bool("side", "Banda", 32, 7, X)
        bool("centre", "Centro", 35, 5, X); bool("pk", "Penaltis", 35, 6, X); bool("onetouch", "Pase al primer toque", 35, 7, X)
        bool("outside", "Exterior", 36, 0, X); bool("mark", "Marcaje", 36, 1, X); bool("slide", "Entrada", 36, 2, X)
        bool("cover", "Cobertura", 36, 3, X); bool("dline", "Control de la defensa", 36, 4, X)
        bool("gkpk", "Para penaltis", 36, 5, X); bool("gk11", "Portero en mano a mano", 36, 6, X)
        bool("throw", "Saque largo de banda", 36, 7, X)

        val F = Section.FACE
        choice("facetype", "Tipo de FACE", 60, 2, 3, FaceFields.TYPE_NAMES + "Desconocido (3)", F)
        num("skin", "Color de piel (1-4)", 43, 1, 3, F, add = 1)
        num("face", "Número de FACE", 53, 3, 0x1FF, F, add = 1)
        num("headh", "Altura de la cabeza", 43, 3, 15, F, add = -7)
        num("headw", "Anchura de la cabeza", 43, 7, 15, F, add = -7)
        num("browtype", "Cejas: tipo", 71, 5, 31, F, add = 1)
        inv("browangle", "Cejas: ángulo", 71, 2, 7, F); inv("browh", "Cejas: altura", 70, 4, 7, F)
        inv("browsp", "Cejas: separación", 70, 7, 7, F)
        num("eyetype", "Ojos: tipo", 68, 3, 31, F, add = 1)
        inv("eyepos", "Ojos: posición", 69, 0, 7, F); inv("eyeangle", "Ojos: ángulo", 69, 3, 7, F)
        inv("eyelen", "Ojos: longitud", 69, 6, 7, F); inv("eyew", "Ojos: anchura", 70, 1, 7, F)
        num("eyec1", "Ojos: color 1", 46, 9, 3, F, add = 1); num("eyec2", "Ojos: color 2", 47, 3, 15, F)
        num("nose", "Nariz: tipo", 73, 0, 7, F, add = 1); inv("noseh", "Nariz: altura", 73, 6, 7, F)
        inv("nosew", "Nariz: anchura", 73, 3, 7, F)
        num("cheek", "Mejillas: tipo", 72, 2, 7, F, add = 1); inv("cheeks", "Mejillas: forma", 72, 5, 7, F)
        num("mouth", "Boca: tipo", 74, 1, 31, F, add = 1); inv("mouths", "Boca: tamaño", 75, 1, 7, F)
        inv("mouthp", "Boca: posición", 74, 6, 7, F)
        num("jaw", "Mandíbula: tipo", 75, 4, 7, F, add = 1); inv("chin", "Mandíbula: barbilla", 75, 7, 7, F)

        val H = Section.HAIR
        num("hair", "ID de HAIR (0-1043 normal, 1044+ especial)", 45, 0, 0x7FF, H)
        num("haircfg", "Color de HAIR: preajuste (63 = RGB)", 46, 3, 63, H, add = 1)
        inv("hairr", "Color de HAIR: R (0-63)", 54, 5, 63, H, center = 63)
        inv("hairg", "Color de HAIR: G (0-63)", 55, 3, 63, H, center = 63)
        inv("hairb", "Color de HAIR: B (0-63)", 56, 1, 63, H, center = 63)
        num("bandanac", "Color de la cinta", 61, 2, 7, H, add = 1)
        num("beard", "Vello facial", 47, 7, 127, H)
        num("beardc", "Color del vello facial", 49, 0, 63, H, add = 1)

        val B = Section.BODY
        num("neckl", "Cuello: longitud", 57, 2, 15, B, add = -7); num("neckw", "Cuello: anchura", 44, 3, 15, B, add = -7)
        num("shoulh", "Hombros: altura", 61, 5, 15, B, add = -7); num("shoulw", "Hombros: anchura", 62, 1, 15, B, add = -7)
        num("chest", "Pecho", 57, 6, 15, B, add = -7); num("waist", "Cintura", 58, 6, 15, B, add = -7)
        num("arm", "Brazos", 58, 2, 15, B, add = -7); num("leg", "Piernas: grosor", 59, 2, 15, B, add = -7)
        num("calf", "Gemelos", 59, 6, 15, B, add = -7); num("legl", "Piernas: longitud", 60, 4, 15, B, add = -7)

        val C = Section.ACCESSORIES
        num("glasses", "Gafas: tipo", 49, 6, 3, C); num("glassesc", "Gafas: color", 66, 0, 7, C, add = 1)
        bool("neckwarm", "Braga de cuello", 50, 0, C)
        num("necklace", "Collar: tipo", 50, 1, 3, C); num("necklacec", "Collar: color", 50, 3, 7, C, add = 1)
        num("wrist", "Muñequera: tipo", 50, 7, 3, C); num("wristc", "Muñequera: color", 51, 1, 7, C, add = 1)
        num("bracelet", "Pulsera: tipo", 51, 4, 3, C); num("braceletc", "Pulsera: color", 51, 6, 7, C, add = 1)
        bool("gloves", "Guantes", 56, 7, C); num("finger", "Anillo/cinta en el dedo", 61, 0, 3, C)
        bool("shirt", "Camiseta por fuera", 44, 7, C); num("sleeves", "Mangas", 48, 6, 3, C)
        bool("undershort", "Malla", 52, 7, C); num("undershortc", "Malla: color", 53, 0, 7, C, add = 1)
        num("socks", "Medias", 57, 0, 3, C, add = 1); bool("tape", "Vendaje", 54, 4, C)
        list.toList()
    }

    private data class Q(val k: String, val l: String, val o: Int, val sh: Int)

    fun bySection(s: Section) = ALL.filter { it.section == s }
    fun byKey(k: String) = ALL.first { it.key == k }

    fun footSide(of: OptionFile, id: Int) = FOOT.get(of, id) * 3 + SIDE.get(of, id).coerceAtMost(2)

    fun setFootSide(of: OptionFile, id: Int, v: Int) {
        require(v in 0..5)
        FOOT.set(of, id, v / 3)
        SIDE.set(of, id, v % 3)
    }

    // ------------------------------------------------------------------ nombre y nombre de camiseta

    private val NAME_EDITED = Field(3, 0, 1)
    private val CALL_EDITED = Field(3, 2, 1)
    private val SHIRT_EDITED = Field(3, 1, 1)
    const val NAME_MAX = 15
    const val SHIRT_MAX = 15

    /** Nombre (máx. 15 caracteres, UTF-16), como Player.setName de PESFan. */
    fun setName(of: OptionFile, id: Int, name: String) {
        if (name.isEmpty() || name.length > NAME_MAX) throw OptionFileException("El nombre debe tener entre 1 y $NAME_MAX caracteres.")
        val a = Players.recordAdr(id)
        val b = name.toByteArray(Charsets.UTF_16LE)
        for (i in 0 until 32) of.data[a + i] = if (i < b.size) b[i] else 0
        of.data[a + 48] = 0xCD.toByte()
        of.data[a + 49] = 0xCD.toByte()
        NAME_EDITED.set(of, id, 1)
        CALL_EDITED.set(of, id, 1)
    }

    fun shirtName(of: OptionFile, id: Int): String {
        val a = Players.recordAdr(id) + 32
        return String(ByteArray(16) { of.data[a + it] }, Charsets.US_ASCII).replace("\u0000", "")
    }

    /** Nombre de camiseta: mayúsculas A-Z, espacio, punto y guion bajo (como PESFan). */
    fun setShirtName(of: OptionFile, id: Int, shirt: String) {
        if (shirt.length > SHIRT_MAX) throw OptionFileException("El nombre de camiseta tiene como máximo $SHIRT_MAX caracteres.")
        val s = shirt.uppercase().map { if (it in 'A'..'Z' || it == '.' || it == ' ' || it == '_') it else ' ' }.joinToString("")
        val a = Players.recordAdr(id) + 32
        val b = s.toByteArray(Charsets.US_ASCII)
        for (i in 0 until 16) of.data[a + i] = if (i < b.size) b[i] else 0
        SHIRT_EDITED.set(of, id, 1)
    }
}
