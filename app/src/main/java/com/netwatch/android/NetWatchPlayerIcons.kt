package com.netwatch.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object NetWatchPlayerIcons {
    val Play = icon("M6.90588 4.53682C6.50592 4.2998 6 4.58808 6 5.05299V18.947C6 19.4119 6.50592 19.7002 6.90588 19.4632L18.629 12.5162C19.0211 12.2838 19.0211 11.7162 18.629 11.4838L6.90588 4.53682Z")
    val Pause = icon(
        "M6 18.4V5.6C6 5.26863 6.26863 5 6.6 5H9.4C9.73137 5 10 5.26863 10 5.6V18.4C10 18.7314 9.73137 19 9.4 19H6.6C6.26863 19 6 18.7314 6 18.4Z",
        "M14 18.4V5.6C14 5.26863 14.2686 5 14.6 5H17.4C17.7314 5 18 5.26863 18 5.6V18.4C18 18.7314 17.7314 19 17.4 19H14.6C14.2686 19 14 18.7314 14 18.4Z",
    )
    val SoundHigh = icon(
        "M1 13.8571V10.1429C1 9.03829 1.89543 8.14286 3 8.14286H5.9C6.09569 8.14286 6.28708 8.08544 6.45046 7.97772L12.4495 4.02228C13.1144 3.5839 14 4.06075 14 4.85714V19.1429C14 19.9392 13.1144 20.4161 12.4495 19.9777L6.45046 16.0223C6.28708 15.9146 6.09569 15.8571 5.9 15.8571H3C1.89543 15.8571 1 14.9617 1 13.8571Z",
        "M17.5 7.5C17.5 7.5 19 9 19 11.5C19 14 17.5 15.5 17.5 15.5",
        "M20.5 4.5C20.5 4.5 23 7 23 11.5C23 16 20.5 18.5 20.5 18.5",
    )
    val SoundOff = icon(
        "M18 14L20.0005 12M22 10L20.0005 12M20.0005 12L18 10M20.0005 12L22 14",
        "M2 13.8571V10.1429C2 9.03829 2.89543 8.14286 4 8.14286H6.9C7.09569 8.14286 7.28708 8.08544 7.45046 7.97772L13.4495 4.02228C14.1144 3.5839 15 4.06075 15 4.85714V19.1429C15 19.9392 14.1144 20.4161 13.4495 19.9777L7.45046 16.0223C7.28708 15.9146 7.09569 15.8571 6.9 15.8571H4C2.89543 15.8571 2 14.9617 2 13.8571Z",
    )
    val Expand = icon(
        "M9 9L4 4M4 4V8M4 4H8", "M15 9L20 4M20 4V8M20 4H16",
        "M9 15L4 20M4 20V16M4 20H8", "M15 15L20 20M20 20V16M20 20H16",
    )
    val Collapse = icon(
        "M20 20L15 15M15 15V19M15 15H19", "M4 20L9 15M9 15V19M9 15H5",
        "M20 4L15 9M15 9V5M15 9H19", "M4 4L9 9M9 9V5M9 9H5",
    )
    val Back = icon("M15 6L9 12L15 18")
    val Tracks = icon(
        "M1 15V9C1 5.68629 3.68629 3 7 3H17C20.3137 3 23 5.68629 23 9V15C23 18.3137 20.3137 21 17 21H7C3.68629 21 1 18.3137 1 15Z",
        "M10.5 10L10.3284 9.82843C9.79799 9.29799 9.07857 9 8.32843 9C6.76633 9 5.5 10.2663 5.5 11.8284V12.1716C5.5 13.7337 6.76633 15 8.32843 15C9.07857 15 9.79799 14.702 10.3284 14.1716L10.5 14",
        "M18.5 10L18.3284 9.82843C17.798 9.29799 17.0786 9 16.3284 9C14.7663 9 13.5 10.2663 13.5 11.8284V12.1716C13.5 13.7337 14.7663 15 16.3284 15C17.0786 15 17.798 14.702 18.3284 14.1716L18.5 14",
    )
    val Download = icon("M6 20L18 20", "M12 4V16M12 16L15.5 12.5M12 16L8.5 12.5")
    val Resize = icon(
        "M11 13.6V21H3.6C3.26863 21 3 20.7314 3 20.4V13H10.4C10.7314 13 11 13.2686 11 13.6Z",
        "M11 21H14", "M3 13V10", "M6 3H3.6C3.26863 3 3 3.26863 3 3.6V6", "M14 3H10",
        "M21 10V14", "M18 3H20.4C20.7314 3 21 3.26863 21 3.6V6", "M18 21H20.4C20.7314 21 21 20.7314 21 20.4V18",
        "M11 10H14V13",
    )
    val SkipArrow = icon(
        "M21.8883 13.5C21.1645 18.3113 17.013 22 12 22C6.47715 22 2 17.5228 2 12C2 6.47715 6.47715 2 12 2C16.1006 2 19.6248 4.46819 21.1679 8",
        "M17 8H21.4C21.7314 8 22 7.73137 22 7.4V3",
    )

    private fun icon(vararg paths: String): ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}
