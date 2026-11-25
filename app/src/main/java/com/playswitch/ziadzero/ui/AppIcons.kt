package com.playswitch.ziadzero.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val AccountCircle: ImageVector by lazy {
        vector("AccountCircle") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 6f)
                curveToRelative(1.93f, 0f, 3.5f, 1.57f, 3.5f, 3.5f)
                reflectiveCurveTo(13.93f, 13f, 12f, 13f)
                reflectiveCurveToRelative(-3.5f, -1.57f, -3.5f, -3.5f)
                reflectiveCurveTo(10.07f, 6f, 12f, 6f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-2.03f, 0f, -4.43f, -0.82f, -6.14f, -2.88f)
                curveTo(7.55f, 15.8f, 9.68f, 15f, 12f, 15f)
                reflectiveCurveToRelative(4.45f, 0.8f, 6.14f, 2.12f)
                curveTo(16.43f, 19.18f, 14.03f, 20f, 12f, 20f)
                close()
            }
        }
    }

    val Apps: ImageVector by lazy {
        vector("Apps") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 8f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineTo(4f)
                verticalLineToRelative(4f)
                close()
                moveTo(10f, 20f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 20f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(4f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 14f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(4f)
                verticalLineToRelative(4f)
                close()
                moveTo(10f, 14f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(16f, 4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineToRelative(-4f)
                close()
                moveTo(10f, 8f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(16f, 14f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(16f, 20f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
            }
        }
    }

    val Backup: ImageVector by lazy {
        vector("Backup") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
                horizontalLineToRelative(13f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
                moveTo(14f, 13f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(7f)
                lineToRelative(5f, -5f)
                lineToRelative(5f, 5f)
                horizontalLineToRelative(-3f)
                close()
            }
        }
    }

    val Refresh: ImageVector by lazy {
        vector("Refresh") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17.65f, 6.35f)
                curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
                curveToRelative(-4.42f, 0f, -7.99f, 3.58f, -7.99f, 8f)
                reflectiveCurveToRelative(3.57f, 8f, 7.99f, 8f)
                curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6f)
                horizontalLineToRelative(-2.08f)
                curveToRelative(-0.82f, 2.33f, -3.04f, 4f, -5.65f, 4f)
                curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
                reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
                curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
                lineTo(13f, 11f)
                horizontalLineToRelative(7f)
                verticalLineTo(4f)
                lineToRelative(-2.35f, 2.35f)
                close()
            }
        }
    }

    val Search: ImageVector by lazy {
        vector("Search") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.5f, 14f)
                horizontalLineToRelative(-0.79f)
                lineToRelative(-0.28f, -0.27f)
                curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
                curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
                reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
                reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
                curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
                lineToRelative(0.27f, 0.28f)
                verticalLineToRelative(0.79f)
                lineToRelative(5f, 4.99f)
                lineTo(20.49f, 19f)
                lineToRelative(-4.99f, -5f)
                close()
                moveTo(9.5f, 14f)
                curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
                reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
                reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
                reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
                close()
            }
        }
    }

    val Close: ImageVector by lazy {
        vector("Close") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 6.41f)
                lineTo(17.59f, 5f)
                lineTo(12f, 10.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(12f, 13.41f)
                lineTo(17.59f, 19f)
                lineTo(19f, 17.59f)
                lineTo(13.41f, 12f)
                close()
            }
        }
    }

    val Checklist: ImageVector by lazy {
        vector("Checklist") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(22f, 7f)
                horizontalLineToRelative(-9f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(9f)
                verticalLineTo(7f)
                close()
                moveTo(22f, 15f)
                horizontalLineToRelative(-9f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(9f)
                verticalLineToRelative(-2f)
                close()
                moveTo(5.54f, 11f)
                lineTo(2f, 7.46f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(4.24f, -4.24f)
                lineToRelative(1.41f, 1.41f)
                lineTo(5.54f, 11f)
                close()
                moveTo(5.54f, 19f)
                lineTo(2f, 15.46f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(4.24f, -4.24f)
                lineToRelative(1.41f, 1.41f)
                lineTo(5.54f, 19f)
                close()
            }
        }
    }

    val Android: ImageVector by lazy {
        vector("Android") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17.6f, 9.48f)
                lineToRelative(1.84f, -3.18f)
                curveToRelative(0.16f, -0.31f, 0.04f, -0.69f, -0.26f, -0.85f)
                curveToRelative(-0.29f, -0.15f, -0.65f, -0.06f, -0.83f, 0.22f)
                lineToRelative(-1.88f, 3.24f)
                curveToRelative(-2.86f, -1.21f, -6.08f, -1.21f, -8.94f, 0f)
                lineTo(5.65f, 5.67f)
                curveToRelative(-0.19f, -0.29f, -0.58f, -0.38f, -0.87f, -0.2f)
                curveToRelative(-0.28f, 0.18f, -0.37f, 0.54f, -0.22f, 0.83f)
                lineToRelative(1.84f, 3.18f)
                curveTo(3.2f, 11.2f, 1.15f, 14.37f, 1f, 18f)
                horizontalLineToRelative(22f)
                curveToRelative(-0.15f, -3.63f, -2.2f, -6.8f, -5.4f, -8.52f)
                close()
                moveTo(7f, 15.25f)
                curveToRelative(-0.69f, 0f, -1.25f, -0.56f, -1.25f, -1.25f)
                reflectiveCurveTo(6.31f, 12.75f, 7f, 12.75f)
                reflectiveCurveToRelative(1.25f, 0.56f, 1.25f, 1.25f)
                reflectiveCurveToRelative(-0.56f, 1.25f, -1.25f, 1.25f)
                close()
                moveTo(17f, 15.25f)
                curveToRelative(-0.69f, 0f, -1.25f, -0.56f, -1.25f, -1.25f)
                reflectiveCurveToRelative(0.56f, -1.25f, 1.25f, -1.25f)
                reflectiveCurveToRelative(1.25f, 0.56f, 1.25f, 1.25f)
                reflectiveCurveToRelative(-0.56f, 1.25f, -1.25f, 1.25f)
                close()
            }
        }
    }

    val Check: ImageVector by lazy {
        vector("Check") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 16.17f)
                lineTo(4.83f, 12f)
                lineToRelative(-1.42f, 1.41f)
                lineTo(9f, 19f)
                lineTo(21f, 7f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }
    }

    val DarkMode: ImageVector by lazy {
        vector("DarkMode") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
                reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
                curveToRelative(0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
                curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
                curveToRelative(-2.98f, 0f, -5.4f, -2.42f, -5.4f, -5.4f)
                curveToRelative(0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
                curveTo(12.92f, 3.04f, 12.46f, 3f, 12f, 3f)
                close()
            }
        }
    }

    val LightMode: ImageVector by lazy {
        vector("LightMode") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7f)
                curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
                reflectiveCurveToRelative(2.24f, 5f, 5f, 5f)
                reflectiveCurveToRelative(5f, -2.24f, 5f, -5f)
                reflectiveCurveToRelative(-2.24f, -5f, -5f, -5f)
                close()
                moveTo(2f, 13f)
                horizontalLineToRelative(2f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineTo(2f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(20f, 13f)
                horizontalLineToRelative(2f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(-2f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(11f, 2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
                verticalLineTo(2f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                close()
                moveTo(11f, 20f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                close()
                moveTo(5.99f, 4.58f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0f)
                reflectiveCurveToRelative(0.39f, -1.03f, 0f, -1.41f)
                lineTo(5.99f, 4.58f)
                close()
                moveTo(18.36f, 16.95f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.41f)
                lineToRelative(-1.06f, -1.06f)
                close()
                moveTo(19.42f, 5.99f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0f)
                lineToRelative(1.06f, -1.06f)
                close()
                moveTo(7.05f, 18.36f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0f)
                lineToRelative(1.06f, -1.06f)
                close()
            }
        }
    }

    val Info: ImageVector by lazy {
        vector("Info") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(13f, 17f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(6f)
                close()
                moveTo(13f, 9f)
                horizontalLineToRelative(-2f)
                verticalLineTo(7f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val OpenInNew: ImageVector by lazy {
        vector("OpenInNew") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 19f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(7f)
                verticalLineTo(3f)
                horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-7f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(7f)
                close()
                moveTo(14f, 3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(3.59f)
                lineToRelative(-9.83f, 9.83f)
                lineToRelative(1.41f, 1.41f)
                lineTo(19f, 6.41f)
                verticalLineTo(10f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                horizontalLineToRelative(-7f)
                close()
            }
        }
    }

    val Tune: ImageVector by lazy {
        vector("Tune") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 17f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                verticalLineTo(5f)
                horizontalLineTo(3f)
                close()
                moveTo(13f, 21f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(2f)
                close()
                moveTo(7f, 9f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineTo(9f)
                horizontalLineTo(7f)
                close()
                moveTo(21f, 13f)
                verticalLineToRelative(-2f)
                horizontalLineTo(11f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                close()
                moveTo(15f, 9f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                close()
            }
        }
    }

    val ExpandMore: ImageVector by lazy {
        vector("ExpandMore") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16.59f, 8.59f)
                lineTo(12f, 13.17f)
                lineTo(7.41f, 8.59f)
                lineTo(6f, 10f)
                lineToRelative(6f, 6f)
                lineToRelative(6f, -6f)
                close()
            }
        }
    }

    val ExpandLess: ImageVector by lazy {
        vector("ExpandLess") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 8f)
                lineToRelative(-6f, 6f)
                lineToRelative(1.41f, 1.41f)
                lineTo(12f, 10.83f)
                lineToRelative(4.59f, 4.58f)
                lineTo(18f, 14f)
                close()
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        vector("ChevronRight") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 6f)
                lineTo(8.59f, 7.41f)
                lineTo(13.17f, 12f)
                lineToRelative(-4.58f, 4.59f)
                lineTo(10f, 18f)
                lineToRelative(6f, -6f)
                close()
            }
        }
    }

    val Shield: ImageVector by lazy {
        vector("Shield") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 1f)
                lineTo(3f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
                curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
                verticalLineTo(5f)
                lineToRelative(-9f, -4f)
                close()
                moveTo(10f, 17f)
                lineToRelative(-4f, -4f)
                lineToRelative(1.41f, -1.41f)
                lineTo(10f, 14.17f)
                lineToRelative(6.59f, -6.59f)
                lineTo(18f, 9f)
                lineToRelative(-8f, 8f)
                close()
            }
        }
    }

    val Warning: ImageVector by lazy {
        vector("Warning") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(1f, 21f)
                horizontalLineToRelative(22f)
                lineTo(12f, 2f)
                lineTo(1f, 21f)
                close()
                moveTo(13f, 18f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(13f, 14f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(4f)
                close()
            }
        }
    }

    val ContentCopy: ImageVector by lazy {
        vector("ContentCopy") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                horizontalLineToRelative(12f)
                verticalLineTo(1f)
                close()
                moveTo(19f, 5f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(11f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(7f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(14f)
                close()
            }
        }
    }

    private inline fun vector(
        name: String,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()
}
