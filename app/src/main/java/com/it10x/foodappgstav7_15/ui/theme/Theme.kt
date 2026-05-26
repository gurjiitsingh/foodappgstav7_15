package com.it10x.foodappgstav7_15.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// =====================================================
// THEME MODES
// =====================================================

enum class PosThemeMode {
    AUTO,
    LIGHT,
    DARK,
    GSTA,

    // ✅ NEW THEMES
    SQUARE,
    LIGHTSPEED,
    TOAST
}


// =====================================================
// ACCENT + PRODUCT COLORS
// =====================================================

data class PosAccentColors(
    val cartAddBg: Color,        // ➜ Add to Cart button background
    val cartAddText: Color,      // ➜ Add to Cart button text color
    val cartRemoveBorder: Color, // ➜ Remove button border color
    val cartRemoveText: Color    // ➜ Remove button text color
)

data class PosProductColors(
    val productCardBg: Color,    // ➜ Product card background
    val productCardText: Color   // ➜ Product name text color
)

// =====================================================
// GLOBAL ACCESS
// =====================================================

object PosTheme {
    lateinit var accent: PosAccentColors
        internal set
    lateinit var product: PosProductColors
        internal set
}

// =====================================================
// MAIN THEME
// =====================================================

@Composable
fun FoodPosTheme(
    mode: PosThemeMode = PosThemeMode.AUTO,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()

    val finalMode = when (mode) {
        PosThemeMode.AUTO -> if (isSystemDark) PosThemeMode.DARK else PosThemeMode.LIGHT
        else -> mode
    }

    val colorScheme = when (finalMode) {

        // ================= LIGHT THEME =================
        // ================= LIGHT THEME =================
        // ================= LIGHT THEME =================
        PosThemeMode.LIGHT -> {

            PosTheme.accent = PosAccentColors(
                cartAddBg = Color(0xFF16A34A),        // ✅ green
                cartAddText = Color.White,
                cartRemoveBorder = Color(0xFFCBD5E1), // slate-300
                cartRemoveText = Color(0xFF334155)    // slate-700
            )

            PosTheme.product = PosProductColors(
                productCardBg = Color.White,          // keep cards clean
                productCardText = Color.Black
            )

            lightColorScheme(
                primary = Color(0xFF16A34A),
                onPrimary = Color.White,
                background = Color(0xFF64748B),       // ✅ slate-500 (darker)
                onBackground = Color.White,           // ✅ FIX contrast
                surface = Color.White,
                onSurface = Color.Black,
                error = Color(0xFFDC2626)
            )
        }

        // ================= DARK THEME =================
        PosThemeMode.DARK -> {

            PosTheme.accent = PosAccentColors(
                cartAddBg = Color(0xFFF97316),        // 🟠 Orange (Add button)
                cartAddText = Color.White,            // ⚪ White text
                cartRemoveBorder = Color(0xFFF97316), // 🟠 Orange border
                cartRemoveText = Color(0xFFF97316)    // 🟠 Orange text
            )

            PosTheme.product = PosProductColors(
                productCardBg = Color(0xFF1E293B),              // slate card
                productCardText = Color.White.copy(alpha = 0.85f)
            )

            darkColorScheme(
                primary = Color(0xFFF25C05),          // 🟠 Primary Orange more dark:  0xFFF24505
                onPrimary = Color.White,
                background = Color(0xFF0F172A),       // 🔵 Dark blue background
                onBackground = Color.White,
                surface = Color(0xFF1E293B),          // 🔵 Card surface
                onSurface = Color.White,
                error = Color(0xFFDC2626)             // 🔴 Error red
            )
        }

        // ================= GSTA THEME =================
        // ================= GSTA THEME =================
        PosThemeMode.GSTA -> {

            PosTheme.accent = PosAccentColors(
                cartAddBg = Color(0xFF16A34A),        // ✅ Green (Add button)
                cartAddText = Color.White,
                cartRemoveBorder = Color(0xFF334155), // ✅ Slate border
                cartRemoveText = Color.White          // ✅ White text for visibility
            )

            PosTheme.product = PosProductColors(
                productCardBg = Color(0xFF1E293B),    // ✅ Slate card
                productCardText = Color.White
            )

            darkColorScheme(
                primary = Color(0xFF16A34A),          // ✅ Green primary
                onPrimary = Color.White,
                background = Color(0xFF0F172A),       // ✅ Dark slate background
                onBackground = Color.White,
                surface = Color(0xFF1E293B),          // ✅ Slate surface
                onSurface = Color.White,
                error = Color(0xFFDC2626)
            )
        }

        // ================= SQUARE THEME =================
        PosThemeMode.SQUARE -> {

            PosTheme.accent = PosAccentColors(
                cartAddBg = Color(0xFF65A30D),        // 🥑 Avocado green
                cartAddText = Color.White,
                cartRemoveBorder = Color(0xFF84CC16), // 🟢 Lime border
                cartRemoveText = Color(0xFF84CC16)
            )

            PosTheme.product = PosProductColors(
                productCardBg = Color(0xFFF7FEE7),    // 🟢 Very light lime
                productCardText = Color(0xFF1A2E05)   // Dark green text
            )

            lightColorScheme(
                primary = Color(0xFF65A30D),           // Avocado
                onPrimary = Color.White,
                background = Color(0xFFECFCCB),        // Soft lime background
                onBackground = Color(0xFF1A2E05),
                surface = Color.White,
                onSurface = Color(0xFF1A2E05),
                error = Color(0xFFDC2626)
            )
        }

        // ================= LIGHTSPEED THEME =================
        PosThemeMode.LIGHTSPEED -> {

            PosTheme.accent = PosAccentColors(
                cartAddBg = Color(0xFF004600),
                cartAddText = Color.White,
                cartRemoveBorder = Color(0xFF004600),
                cartRemoveText = Color(0xFF004600)
            )

            PosTheme.product = PosProductColors(
                productCardBg = Color(0xFFFFFFFF),
                productCardText = Color(0xFF004600)
            )

            lightColorScheme(
                primary = Color(0xFF004600),
                onPrimary = Color.White,
                background = Color(0xFFF8F8F8),
                onBackground = Color(0xFF004600),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF004600),
                error = Color(0xFFDC2626)
            )
        }

        // ================= TOAST THEME =================
        PosThemeMode.TOAST -> {

            PosTheme.accent = PosAccentColors(
                cartAddBg = Color(0xFFCFCD7F),
                cartAddText = Color.White,            // ⚪ White text
                cartRemoveBorder = Color(0xFFCFCD7F), // 🔴 Red border
                cartRemoveText = Color(0xFFDC2626)    // 🔴 Red text
            )

            PosTheme.product = PosProductColors(
                productCardBg = Color(0xFFFFFBEB),    // Soft warm cream
                productCardText = Color(0xFF78350F)   // Deep brown text
            )

            lightColorScheme(
                primary = Color(0xFFD97706),          // Turmeric
                onPrimary = Color.White,
                background = Color(0xFFFEF3C7),       // Warm light yellow
                onBackground = Color(0xFF78350F),
                surface = Color.White,
                onSurface = Color(0xFF78350F),
                error = Color(0xFFDC2626)
            )
        }



        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
