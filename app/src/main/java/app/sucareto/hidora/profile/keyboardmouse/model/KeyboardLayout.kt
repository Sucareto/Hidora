package app.sucareto.hidora.profile.keyboardmouse.model

import app.sucareto.hidora.R
import app.sucareto.hidora.profile.keyboardmouse.KeyCodes
import app.sucareto.hidora.profile.keyboardmouse.KeyboardReporter

object KeyboardLayout {
    val arrowUp: KeyDef = DualKey(KeyCodes.UP, KeyCodes.PAGE_UP, "\u2191", "PgUp", weight = 1.25f)
    val arrowLeft: KeyDef = DualKey(KeyCodes.LEFT, KeyCodes.HOME, "\u2190", "Home", weight = 1.25f)
    val arrowDown: KeyDef = DualKey(KeyCodes.DOWN, KeyCodes.PAGE_DOWN, "\u2193", "PgDn", weight = 1.25f)
    val arrowRight: KeyDef = DualKey(KeyCodes.RIGHT, KeyCodes.END, "\u2192", "End", weight = 1.25f)
    val menuKey: KeyDef = NormalKey(KeyCodes.APPLICATION, "\u2630", weight = 1.25f)

    val functionRow: List<KeyDef> = buildList {
        add(NormalKey(KeyCodes.ESCAPE, "Esc"))
        for (n in 1..12) add(NormalKey(KeyCodes.functionKey(n), "F$n"))
        add(DualKey(KeyCodes.DELETE, KeyCodes.INSERT, "Del", "Ins"))
    }

    val numberRow: List<KeyDef> = listOf(
        NormalKey(KeyCodes.GRAVE, "`", shiftLabel = "~"),
        NormalKey(KeyCodes.NUM_1, "1", shiftLabel = "!"), NormalKey(KeyCodes.NUM_2, "2", shiftLabel = "@"),
        NormalKey(KeyCodes.NUM_3, "3", shiftLabel = "#"), NormalKey(KeyCodes.NUM_4, "4", shiftLabel = "$"),
        NormalKey(KeyCodes.NUM_5, "5", shiftLabel = "%"), NormalKey(KeyCodes.NUM_6, "6", shiftLabel = "^"),
        NormalKey(KeyCodes.NUM_7, "7", shiftLabel = "&"), NormalKey(KeyCodes.NUM_8, "8", shiftLabel = "*"),
        NormalKey(KeyCodes.NUM_9, "9", shiftLabel = "("), NormalKey(KeyCodes.NUM_0, "0", shiftLabel = ")"),
        NormalKey(KeyCodes.MINUS, "-", shiftLabel = "_"), NormalKey(KeyCodes.EQUAL, "=", shiftLabel = "+"),
        NormalKey(KeyCodes.BACKSPACE, "Backspace", weight = 2f)
    )

    val qwertyRow: List<KeyDef> = listOf(
        NormalKey(KeyCodes.TAB, "Tab", weight = 1.5f),
        NormalKey(KeyCodes.Q, "Q"), NormalKey(KeyCodes.W, "W"), NormalKey(KeyCodes.E, "E"),
        NormalKey(KeyCodes.R, "R"), NormalKey(KeyCodes.T, "T"), NormalKey(KeyCodes.Y, "Y"),
        NormalKey(KeyCodes.U, "U"), NormalKey(KeyCodes.I, "I"), NormalKey(KeyCodes.O, "O"),
        NormalKey(KeyCodes.P, "P"),
        NormalKey(KeyCodes.LEFT_BRACKET, "[", shiftLabel = "{"), NormalKey(KeyCodes.RIGHT_BRACKET, "]", shiftLabel = "}"),
        NormalKey(KeyCodes.BACKSLASH, "\\", weight = 1.5f, shiftLabel = "|")
    )

    val homeRow: List<KeyDef> = listOf(
        NormalKey(KeyCodes.CAPS_LOCK, "Caps", weight = 1.75f),
        NormalKey(KeyCodes.A, "A"), NormalKey(KeyCodes.S, "S"), NormalKey(KeyCodes.D, "D"),
        NormalKey(KeyCodes.F, "F"), NormalKey(KeyCodes.G, "G"), NormalKey(KeyCodes.H, "H"),
        NormalKey(KeyCodes.J, "J"), NormalKey(KeyCodes.K, "K"), NormalKey(KeyCodes.L, "L"),
        NormalKey(KeyCodes.SEMICOLON, ";", shiftLabel = ":"), NormalKey(KeyCodes.QUOTE, "'", shiftLabel = "\""),
        ComboKey(KeyCodes.ENTER, ComboAction.CTRL_ALT_DEL, "Enter", weight = 2.25f, fnLabelRes = R.string.fn_label_ctrl_alt_del)
    )

    val bottomLettersRow: List<KeyDef> = listOf(
        ModifierKey(KeyboardReporter.MODIFIER_LEFT_SHIFT, "Shift", weight = 2.25f),
        NormalKey(KeyCodes.Z, "Z"), NormalKey(KeyCodes.X, "X"), NormalKey(KeyCodes.C, "C"),
        NormalKey(KeyCodes.V, "V"), NormalKey(KeyCodes.B, "B"), NormalKey(KeyCodes.N, "N"),
        NormalKey(KeyCodes.M, "M"), NormalKey(KeyCodes.COMMA, ",", shiftLabel = "<"),
        NormalKey(KeyCodes.PERIOD, ".", shiftLabel = ">"), NormalKey(KeyCodes.SLASH, "/", shiftLabel = "?"),
        arrowUp,
        ModifierKey(KeyboardReporter.MODIFIER_RIGHT_SHIFT, "Shift", weight = 1.25f)
    )

    val controlRow: List<KeyDef> = listOf(
        ModifierKey(KeyboardReporter.MODIFIER_LEFT_CTRL, "Ctrl", weight = 1.25f),
        FnKey(),
        ModifierKey(KeyboardReporter.MODIFIER_LEFT_GUI, "Win"),
        ModifierKey(KeyboardReporter.MODIFIER_LEFT_ALT, "Alt"),
        ComboKey(KeyCodes.SPACE, ComboAction.SWITCH_TO_MOUSE, "Space", weight = 6f, fnLabelRes = R.string.fn_label_mouse),
        menuKey,
        arrowLeft, arrowDown, arrowRight
    )

    val allModifierBits: List<Int> = listOf(
        KeyboardReporter.MODIFIER_LEFT_SHIFT, KeyboardReporter.MODIFIER_RIGHT_SHIFT,
        KeyboardReporter.MODIFIER_LEFT_CTRL,
        KeyboardReporter.MODIFIER_LEFT_ALT,
        KeyboardReporter.MODIFIER_LEFT_GUI
    )
}
