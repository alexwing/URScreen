package com.alexwing.urscreen.ui

/**
 * Una tecla del teclado en pantalla.
 *
 * @param name nombre que entiende el servidor de Unified Remote (comprobado contra uno real con teclado español).
 * @param label lo que se dibuja en la tecla.
 * @param shiftLabel símbolo que da con Mayús, dibujado pequeño encima, o `null`.
 * @param width ancho en unidades de tecla.
 * @param modifier `true` si es un modificador: un doble toque rápido lo deja fijado (ver [KeyLatch]).
 */
data class KeyDef(
    val name: String,
    val label: String,
    val shiftLabel: String? = null,
    val width: Float = 1f,
    val modifier: Boolean = false,
)

/**
 * Teclado español (ISO) en filas. Las teclas mandan el código de tecla físico, no el carácter: lo que sale depende
 * de la distribución del PC, que aquí se da por española, igual que las leyendas.
 */
object SpanishKeyboard {
    /** Ancho de una fila completa, en unidades de tecla. */
    const val ROW_UNITS = 15f

    private fun k(name: String, label: String = name.uppercase(), shift: String? = null, width: Float = 1f, mod: Boolean = false) =
        KeyDef(name, label, shift, width, mod)

    private fun letters(chars: String) = chars.map { k(it.toString(), it.toString().uppercase()) }

    val rows: List<List<KeyDef>> = listOf(
        // Fila de función
        listOf(k("esc", "Esc", width = 1.5f)) +
            (1..12).map { k("f$it", "F$it") } +
            k("delete", "Supr", width = 1.5f),
        // Números
        listOf(k("oem_5", "º", "ª")) +
            listOf(
                k("1", "1", "!"), k("2", "2", "\""), k("3", "3", "·"), k("4", "4", "$"), k("5", "5", "%"),
                k("6", "6", "&"), k("7", "7", "/"), k("8", "8", "("), k("9", "9", ")"), k("0", "0", "="),
                k("oem_4", "'", "?"), k("oem_6", "¡", "¿"),
            ) +
            k("back", "Borrar", width = 2f),
        // QWERTY
        listOf(k("tab", "Tab", width = 1.5f)) + letters("qwertyuiop") +
            listOf(k("oem_1", "`", "^"), k("oem_plus", "+", "*")),
        // ASDF
        listOf(k("capital", "Bloq", width = 1.75f)) + letters("asdfghjkl") +
            listOf(k("oem_3", "Ñ"), k("oem_7", "´", "¨"), k("oem_2", "Ç")),
        // ZXCV
        listOf(k("lshift", "Mayús", width = 1.25f, mod = true), k("oem_102", "<", ">")) + letters("zxcvbnm") +
            listOf(
                k("oem_comma", ",", ";"), k("oem_period", ".", ":"), k("oem_minus", "-", "_"),
                k("rshift", "Mayús", width = 2.75f, mod = true),
            ),
        // Inferior
        listOf(
            k("lctrl", "Ctrl", width = 1.25f, mod = true), k("lwin", "Win", width = 1.25f, mod = true),
            k("lalt", "Alt", width = 1.25f, mod = true), k("space", "", width = 6f),
            k("ralt", "AltGr", width = 1.25f, mod = true),
            k("left", "←"), k("up", "↑"), k("down", "↓"), k("right", "→"),
        ),
    )

    /** Todas las teclas del teclado, incluido el Intro ISO (que no está en [rows] porque ocupa dos filas). */
    val allKeys: List<KeyDef> = rows.flatten() + IsoEnter.key
}

/** Intro del teclado ISO: una sola tecla en forma de L que ocupa las filas 2 y 3, a la derecha. */
object IsoEnter {
    val key = KeyDef("return", "Intro")
    const val FIRST_ROW = 2          // fila donde empieza (la de Tab/QWERTY)
    const val LEFT = 13.5f           // x del borde izquierdo de la parte alta, en unidades de tecla
    const val TOP_WIDTH = 1.5f       // ancho de la parte alta (hasta ROW_UNITS)
    const val BOTTOM_WIDTH = 1.25f   // ancho del tallo bajo, en la fila siguiente
    const val NOTCH = TOP_WIDTH - BOTTOM_WIDTH   // 0.25f: lo que entra la parte baja por la izquierda

    /**
     * Vértices de la L, en coordenadas locales de la caja que mide TOP_WIDTH*keyWidth de ancho y 2*keyHeight de alto
     * (origen arriba a la izquierda), en el sentido de las agujas del reloj desde arriba a la izquierda, con todos los
     * bordes metidos `inset` hacia dentro (para dejar el mismo hueco que entre las demás teclas).
     * Con W = TOP_WIDTH*keyWidth, H = keyHeight, N = NOTCH*keyWidth, son exactamente 6 puntos:
     * (inset, inset), (W-inset, inset), (W-inset, 2H-inset), (N+inset, 2H-inset), (N+inset, H-inset), (inset, H-inset)
     *
     * `clearance` deja más aire por el lado que toca a las demás teclas (el izquierdo y la muesca): mueve a la derecha
     * los vértices de la izquierda y sube el borde horizontal de la muesca. Con 0 no cambia nada.
     */
    fun polygon(keyWidth: Float, keyHeight: Float, inset: Float, clearance: Float = 0f): List<Pair<Float, Float>> {
        val w = TOP_WIDTH * keyWidth
        val h = keyHeight
        val n = NOTCH * keyWidth
        return listOf(
            (inset + clearance) to inset,
            (w - inset) to inset,
            (w - inset) to (2 * h - inset),
            (n + inset + clearance) to (2 * h - inset),
            (n + inset + clearance) to (h - inset - clearance),
            (inset + clearance) to (h - inset - clearance),
        )
    }
}
