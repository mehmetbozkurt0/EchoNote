package com.echonote.echonote

import androidx.compose.runtime.Composable

/**
 * Sistem çubuğu ikonlarını temaya uydurur. Açık temada ikonlar koyu olmalı; aksi halde
 * beyaz ikonlar açık zeminde kaybolur.
 *
 * Masaüstünde karşılığı yok, no-op.
 */
@Composable
expect fun ApplySystemBarAppearance(isLight: Boolean)
