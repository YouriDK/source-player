package com.source.player.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's icon set — Hugeicons, stroke-rounded, 24x24 grid.
 *
 * Source: the free icon set of https://github.com/hugeicons/hugeicons, MIT licensed
 * (see that repository's LICENSE.md; the MIT grant covers the free icons and package
 * source, Pro packs do not apply here). Only the icons the app actually uses are
 * vendored, as raw SVG path data.
 *
 * Icons are held as path strings and parsed into an [ImageVector] on first use, then
 * cached — the alternative, hand-expanding every curve into Compose path-builder
 * calls, is the same data spread over twenty times the source. Parsing happens once
 * per icon for the life of the process.
 *
 * Call sites read the same as before: `Icon(HugeIcons.Play, ...)`. Tinting works as
 * usual — [androidx.compose.material3.Icon] applies a colour filter over the whole
 * vector, which recolours strokes just as it does fills.
 */
object HugeIcons {
    // Generated from the upstream SVGs; each doc comment names its source file.

    /** Hugeicons `play` */
    val Play: ImageVector
        get() = cached("Play") {
            listOf(
                stroked("M18.8906 12.846C18.5371 14.189 16.8667 15.138 13.5257 17.0361C10.296 18.8709 8.6812 19.7884 7.37983 19.4196C6.8418 19.2671 6.35159 18.9776 5.95624 18.5787C5 17.6139 5 15.7426 5 12C5 8.2574 5 6.3861 5.95624 5.42132C6.35159 5.02245 6.8418 4.73288 7.37983 4.58042C8.6812 4.21165 10.296 5.12907 13.5257 6.96393C16.8667 8.86197 18.5371 9.811 18.8906 11.154C19.0365 11.7084 19.0365 12.2916 18.8906 12.846Z", join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `pause` */
    val Pause: ImageVector
        get() = cached("Pause") {
            listOf(
                stroked("M4 7C4 5.58579 4 4.87868 4.43934 4.43934C4.87868 4 5.58579 4 7 4C8.41421 4 9.12132 4 9.56066 4.43934C10 4.87868 10 5.58579 10 7V17C10 18.4142 10 19.1213 9.56066 19.5607C9.12132 20 8.41421 20 7 20C5.58579 20 4.87868 20 4.43934 19.5607C4 19.1213 4 18.4142 4 17V7Z"),
                stroked("M14 7C14 5.58579 14 4.87868 14.4393 4.43934C14.8787 4 15.5858 4 17 4C18.4142 4 19.1213 4 19.5607 4.43934C20 4.87868 20 5.58579 20 7V17C20 18.4142 20 19.1213 19.5607 19.5607C19.1213 20 18.4142 20 17 20C15.5858 20 14.8787 20 14.4393 19.5607C14 19.1213 14 18.4142 14 17V7Z"),
            )
        }

    /** Hugeicons `next` */
    val Next: ImageVector
        get() = cached("Next") {
            listOf(
                stroked("M15.9351 12.6258C15.6807 13.8374 14.327 14.7077 11.6198 16.4481C8.67528 18.3411 7.20303 19.2876 6.01052 18.9229C5.60662 18.7994 5.23463 18.5823 4.92227 18.2876C4 17.4178 4 15.6118 4 12C4 8.38816 4 6.58224 4.92227 5.71235C5.23463 5.41773 5.60662 5.20057 6.01052 5.07707C7.20304 4.71243 8.67528 5.6589 11.6198 7.55186C14.327 9.29233 15.6807 10.1626 15.9351 11.3742C16.0216 11.7865 16.0216 12.2135 15.9351 12.6258Z", join = StrokeJoin.Round),
                stroked("M20 5V19", cap = StrokeCap.Round),
            )
        }

    /** Hugeicons `previous` */
    val Previous: ImageVector
        get() = cached("Previous") {
            listOf(
                stroked("M8.06492 12.6258C8.31931 13.8374 9.67295 14.7077 12.3802 16.4481C15.3247 18.3411 16.797 19.2876 17.9895 18.9229C18.3934 18.7994 18.7654 18.5823 19.0777 18.2876C20 17.4178 20 15.6118 20 12C20 8.38816 20 6.58224 19.0777 5.71235C18.7654 5.41773 18.3934 5.20057 17.9895 5.07707C16.797 4.71243 15.3247 5.6589 12.3802 7.55186C9.67295 9.29233 8.31931 10.1626 8.06492 11.3742C7.97836 11.7865 7.97836 12.2135 8.06492 12.6258Z", join = StrokeJoin.Round),
                stroked("M4 4L4 20", cap = StrokeCap.Round),
            )
        }

    /** Hugeicons `shuffle` */
    val Shuffle: ImageVector
        get() = cached("Shuffle") {
            listOf(
                stroked("M19.5576 4L20.4551 4.97574C20.8561 5.41165 21.0566 5.62961 20.9861 5.81481C20.9155 6 20.632 6 20.0649 6C18.7956 6 17.2771 5.79493 16.1111 6.4733C15.3903 6.89272 14.8883 7.62517 14.0392 9M3 18H4.58082C6.50873 18 7.47269 18 8.2862 17.5267C9.00708 17.1073 9.50904 16.3748 10.3582 15", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M19.5576 20L20.4551 19.0243C20.8561 18.5883 21.0566 18.3704 20.9861 18.1852C20.9155 18 20.632 18 20.0649 18C18.7956 18 17.2771 18.2051 16.1111 17.5267C15.2976 17.0534 14.7629 16.1815 13.6935 14.4376L10.7038 9.5624C9.63441 7.81853 9.0997 6.9466 8.2862 6.4733C7.47269 6 6.50873 6 4.58082 6H3", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `repeat` */
    val Repeat: ImageVector
        get() = cached("Repeat") {
            listOf(
                stroked("M16.3884 3L17.3913 3.97574C17.8393 4.41165 18.0633 4.62961 17.9844 4.81481C17.9056 5 17.5888 5 16.9552 5H9.19422C5.22096 5 2 8.13401 2 12C2 13.4872 2.47668 14.8662 3.2895 16", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M7.61156 21L6.60875 20.0243C6.16074 19.5883 5.93673 19.3704 6.01557 19.1852C6.09441 19 6.4112 19 7.04478 19H14.8058C18.779 19 22 15.866 22 12C22 10.5128 21.5233 9.13383 20.7105 8", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `repeat-one-01` */
    val RepeatOne: ImageVector
        get() = cached("RepeatOne") {
            listOf(
                stroked("M16.3884 3L17.3913 3.97574C17.8393 4.41165 18.0633 4.62961 17.9844 4.81481C17.9056 5 17.5888 5 16.9552 5H9.19422C5.22096 5 2 8.13401 2 12C2 13.4872 2.47668 14.8662 3.2895 16", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M7.61156 21L6.60875 20.0243C6.16074 19.5883 5.93673 19.3704 6.01557 19.1852C6.09441 19 6.4112 19 7.04478 19H14.8058C18.779 19 22 15.866 22 12C22 10.5128 21.5233 9.13383 20.7105 8", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M13 15L13 9.31633C13 9.05613 12.7178 8.90761 12.52 9.06373L11 10.2636", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `volume-high` */
    val VolumeHigh: ImageVector
        get() = cached("VolumeHigh") {
            listOf(
                stroked("M14 14.8135V9.18646C14 6.04126 14 4.46866 13.0747 4.0773C12.1494 3.68593 11.0603 4.79793 8.88232 7.02192C7.75439 8.17365 7.11085 8.42869 5.50604 8.42869C4.10257 8.42869 3.40084 8.42869 2.89675 8.77262C1.85035 9.48655 2.00852 10.882 2.00852 12C2.00852 13.118 1.85035 14.5134 2.89675 15.2274C3.40084 15.5713 4.10257 15.5713 5.50604 15.5713C7.11085 15.5713 7.75439 15.8264 8.88232 16.9781C11.0603 19.2021 12.1494 20.3141 13.0747 19.9227C14 19.5313 14 17.9587 14 14.8135Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M17 9C17.6254 9.81968 18 10.8634 18 12C18 13.1366 17.6254 14.1803 17 15", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M20 7C21.2508 8.36613 22 10.1057 22 12C22 13.8943 21.2508 15.6339 20 17", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `volume-low` */
    val VolumeLow: ImageVector
        get() = cached("VolumeLow") {
            listOf(
                stroked("M19 9C19.6254 9.81968 20 10.8634 20 12C20 13.1366 19.6254 14.1803 19 15", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M16 14.8135V9.18646C16 6.04126 16 4.46866 15.0747 4.0773C14.1494 3.68593 13.0604 4.79793 10.8823 7.02192C9.7544 8.17365 9.11086 8.42869 7.50605 8.42869C6.10259 8.42869 5.40086 8.42869 4.89677 8.77262C3.85036 9.48655 4.00854 10.882 4.00854 12C4.00854 13.118 3.85036 14.5134 4.89677 15.2274C5.40086 15.5713 6.10259 15.5713 7.50605 15.5713C9.11086 15.5713 9.7544 15.8264 10.8823 16.9781C13.0604 19.2021 14.1494 20.3141 15.0747 19.9227C16 19.5313 16 17.9587 16 14.8135Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `music-note-01` */
    val MusicNote: ImageVector
        get() = cached("MusicNote") {
            listOf(
                stroked("M7 9.5C7 10.8807 5.88071 12 4.5 12C3.11929 12 2 10.8807 2 9.5C2 8.11929 3.11929 7 4.5 7C5.88071 7 7 8.11929 7 9.5ZM7 9.5V2C7.33333 2.5 7.6 4.6 10 5", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M13 19.5L13 11C13 10.09 13 9.63502 13.2466 9.35248C13.4932 9.06993 13.9938 9.00163 14.9949 8.86504C18.0085 8.45385 20.2013 7.19797 21.3696 6.42937C21.6498 6.24509 21.7898 6.15295 21.8949 6.20961C22 6.26627 22 6.43179 22 6.76283V17.9259", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M13 13C17.8 13 21 10.6667 22 10", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `album-01` */
    val Album: ImageVector
        get() = cached("Album") {
            listOf(
                stroked("M6 17.9745C6.1287 19.2829 6.41956 20.1636 7.07691 20.8209C8.25596 22 10.1536 22 13.9489 22C17.7442 22 19.6419 22 20.8209 20.8209C22 19.6419 22 17.7442 22 13.9489C22 10.1536 22 8.25596 20.8209 7.07691C20.1636 6.41956 19.2829 6.1287 17.9745 6"),
                stroked("M2 10C2 6.22876 2 4.34315 3.17157 3.17157C4.34315 2 6.22876 2 10 2C13.7712 2 15.6569 2 16.8284 3.17157C18 4.34315 18 6.22876 18 10C18 13.7712 18 15.6569 16.8284 16.8284C15.6569 18 13.7712 18 10 18C6.22876 18 4.34315 18 3.17157 16.8284C2 15.6569 2 13.7712 2 10Z"),
                stroked("M5 18C8.42061 13.2487 12.2647 6.9475 18 11.6734"),
            )
        }

    /** Hugeicons `library` */
    val Library: ImageVector
        get() = cached("Library") {
            listOf(
                stroked("M2 7C2 5.59987 2 4.8998 2.27248 4.36502C2.51217 3.89462 2.89462 3.51217 3.36502 3.27248C3.8998 3 4.59987 3 6 3C7.40013 3 8.1002 3 8.63498 3.27248C9.10538 3.51217 9.48783 3.89462 9.72752 4.36502C10 4.8998 10 5.59987 10 7V17C10 18.4001 10 19.1002 9.72752 19.635C9.48783 20.1054 9.10538 20.4878 8.63498 20.7275C8.1002 21 7.40013 21 6 21C4.59987 21 3.8998 21 3.36502 20.7275C2.89462 20.4878 2.51217 20.1054 2.27248 19.635C2 19.1002 2 18.4001 2 17V7Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M6 17H6.00898", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M2 7H10", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M11.4486 8.26843C11.0937 6.93838 10.9163 6.27336 11.0385 5.69599C11.146 5.18812 11.4108 4.72747 11.7951 4.38005C12.2319 3.98508 12.8942 3.80689 14.2187 3.4505C15.5432 3.09412 16.2055 2.91593 16.7804 3.03865C17.2862 3.1466 17.7449 3.41256 18.0909 3.79841C18.4842 4.23706 18.6617 4.90209 19.0166 6.23213L21.5514 15.7316C21.9063 17.0616 22.0837 17.7266 21.9615 18.304C21.854 18.8119 21.5892 19.2725 21.2049 19.62C20.7681 20.0149 20.1058 20.1931 18.7813 20.5495C17.4568 20.9059 16.7945 21.0841 16.2196 20.9614C15.7138 20.8534 15.2551 20.5874 14.9091 20.2016C14.5158 19.7629 14.3383 19.0979 13.9834 17.7679L11.4486 8.26843Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M17.7812 16.6953L17.7899 16.693", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M12 8.00019L18.5001 6", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `playlist-01` */
    val Playlist: ImageVector
        get() = cached("Playlist") {
            listOf(
                stroked("M3 15C3 12.1911 3 10.7866 3.67412 9.77772C3.96596 9.34096 4.34096 8.96596 4.77772 8.67412C5.78661 8 7.19108 8 10 8H14C16.8089 8 18.2134 8 19.2223 8.67412C19.659 8.96596 20.034 9.34096 20.3259 9.77772C21 10.7866 21 12.1911 21 15C21 17.8089 21 19.2134 20.3259 20.2223C20.034 20.659 19.659 21.034 19.2223 21.3259C18.2134 22 16.8089 22 14 22H10C7.19108 22 5.78661 22 4.77772 21.3259C4.34096 21.034 3.96596 20.659 3.67412 20.2223C3 19.2134 3 17.8089 3 15Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M12.5 16.5C12.5 17.3284 11.8284 18 11 18C10.1716 18 9.5 17.3284 9.5 16.5C9.5 15.6716 10.1716 15 11 15C11.8284 15 12.5 15.6716 12.5 16.5ZM12.5 16.5V11.5C12.5 11.5 12.9 13.2333 14.5 13.5", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M19 8C18.9821 6.76022 18.89 6.05733 18.4182 5.58579C17.8321 5 16.8888 5 15.0022 5H8.99783C7.11118 5 6.16786 5 5.58176 5.58579C5.10996 6.05733 5.01794 6.76022 5 8", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M17 5C17 4.06812 17 3.60218 16.8478 3.23463C16.6448 2.74458 16.2554 2.35523 15.7654 2.15224C15.3978 2 14.9319 2 14 2H10C9.06812 2 8.60218 2 8.23463 2.15224C7.74458 2.35523 7.35523 2.74458 7.15224 3.23463C7 3.60218 7 4.06812 7 5", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `play-list-add` */
    val PlaylistAdd: ImageVector
        get() = cached("PlaylistAdd") {
            listOf(
                stroked("M1.99988 7H20.9999", join = StrokeJoin.Round),
                stroked("M16.4999 2L13.4999 7", join = StrokeJoin.Round),
                stroked("M9.49988 2L6.49988 7", join = StrokeJoin.Round),
                stroked("M11.5001 21C7.02172 21 4.78255 21 3.3913 19.6088C2.00006 18.2175 2.00006 15.9783 2.00006 11.5C2.00006 7.02166 2.00006 4.78249 3.3913 3.39124C4.78255 2 7.02172 2 11.5001 2C15.9784 2 18.2176 2 19.6088 3.39124C21.0001 4.78249 21.0001 7.02166 21.0001 11.5", cap = StrokeCap.Round),
                stroked("M14 18H22M18 22L18 14", cap = StrokeCap.Round),
            )
        }

    /** Hugeicons `play-list` */
    val PlaylistPlay: ImageVector
        get() = cached("PlaylistPlay") {
            listOf(
                stroked("M2.50012 7.5H21.5001", join = StrokeJoin.Round),
                stroked("M17.0001 2.5L14.0001 7.5", join = StrokeJoin.Round),
                stroked("M10.0001 2.5L7.00012 7.5", join = StrokeJoin.Round),
                stroked("M2.5 12C2.5 7.52166 2.5 5.28249 3.89124 3.89124C5.28249 2.5 7.52166 2.5 12 2.5C16.4783 2.5 18.7175 2.5 20.1088 3.89124C21.5 5.28249 21.5 7.52166 21.5 12C21.5 16.4783 21.5 18.7175 20.1088 20.1088C18.7175 21.5 16.4783 21.5 12 21.5C7.52166 21.5 5.28249 21.5 3.89124 20.1088C2.5 18.7175 2.5 16.4783 2.5 12Z"),
                stroked("M14.9531 14.8948C14.8016 15.5215 14.0857 15.9644 12.6539 16.8502C11.2697 17.7064 10.5777 18.1346 10.0199 17.9625C9.78934 17.8913 9.57925 17.7562 9.40982 17.57C9 17.1198 9 16.2465 9 14.5C9 12.7535 9 11.8802 9.40982 11.4299C9.57925 11.2438 9.78934 11.1087 10.0199 11.0375C10.5777 10.8654 11.2697 11.2936 12.6539 12.1498C14.0857 13.0356 14.8016 13.4785 14.9531 14.1052C15.0156 14.3639 15.0156 14.6361 14.9531 14.8948Z", join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `queue-01` */
    val Queue: ImageVector
        get() = cached("Queue") {
            listOf(
                stroked("M21 14C21 15.4001 21 16.1002 20.7275 16.635C20.4878 17.1054 20.1054 17.4878 19.635 17.7275C19.1002 18 18.4001 18 17 18H7C5.59987 18 4.8998 18 4.36502 17.7275C3.89462 17.4878 3.51217 17.1054 3.27248 16.635C3 16.1002 3 15.4001 3 14", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M6 14H18", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M6 10H18", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M6 6H18", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `plus-sign` */
    val Add: ImageVector
        get() = cached("Add") {
            listOf(
                stroked("M12 4V20M20 12H4", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `cancel-01` */
    val Close: ImageVector
        get() = cached("Close") {
            listOf(
                stroked("M19.0005 4.99988L5.00049 18.9999M5.00049 4.99988L19.0005 18.9999", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `tick-02` */
    val Check: ImageVector
        get() = cached("Check") {
            listOf(
                stroked("M5 14L8.5 17.5L19 6.5", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `checkmark-circle-01` */
    val CheckCircle: ImageVector
        get() = cached("CheckCircle") {
            listOf(
                stroked("M22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12Z"),
                stroked("M8 12.75C8 12.75 9.6 13.6625 10.4 15C10.4 15 12.8 9.75 16 8", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `minus-sign-circle` */
    val RemoveCircle: ImageVector
        get() = cached("RemoveCircle") {
            listOf(
                stroked("M16 12H8", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `edit-02` */
    val Edit: ImageVector
        get() = cached("Edit") {
            listOf(
                stroked("M14.0737 3.88545C14.8189 3.07808 15.1915 2.6744 15.5874 2.43893C16.5427 1.87076 17.7191 1.85309 18.6904 2.39232C19.0929 2.6158 19.4769 3.00812 20.245 3.79276C21.0131 4.5774 21.3972 4.96972 21.6159 5.38093C22.1438 6.37312 22.1265 7.57479 21.5703 8.5507C21.3398 8.95516 20.9446 9.33578 20.1543 10.097L10.7506 19.1543C9.25288 20.5969 8.504 21.3182 7.56806 21.6837C6.63212 22.0493 5.6032 22.0224 3.54536 21.9686L3.26538 21.9613C2.63891 21.9449 2.32567 21.9367 2.14359 21.73C1.9615 21.5234 1.98636 21.2043 2.03608 20.5662L2.06308 20.2197C2.20301 18.4235 2.27297 17.5255 2.62371 16.7182C2.97444 15.9109 3.57944 15.2555 4.78943 13.9445L14.0737 3.88545Z", join = StrokeJoin.Round),
                stroked("M13 4L20 11", join = StrokeJoin.Round),
                stroked("M14 22L22 22", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `refresh` */
    val Refresh: ImageVector
        get() = cached("Refresh") {
            listOf(
                stroked("M20.0092 2V5.13219C20.0092 5.42605 19.6418 5.55908 19.4537 5.33333C17.6226 3.2875 14.9617 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `reload` */
    val Restart: ImageVector
        get() = cached("Restart") {
            listOf(
                stroked("M15.1667 0.999756L15.7646 2.11753C16.1689 2.87322 16.371 3.25107 16.2374 3.41289C16.1037 3.57471 15.6635 3.44402 14.7831 3.18264C13.9029 2.92131 12.9684 2.78071 12 2.78071C6.75329 2.78071 2.5 6.90822 2.5 11.9998C2.5 13.6789 2.96262 15.2533 3.77093 16.6093M8.83333 22.9998L8.23536 21.882C7.83108 21.1263 7.62894 20.7484 7.7626 20.5866C7.89627 20.4248 8.33649 20.5555 9.21689 20.8169C10.0971 21.0782 11.0316 21.2188 12 21.2188C17.2467 21.2188 21.5 17.0913 21.5 11.9998C21.5 10.3206 21.0374 8.74623 20.2291 7.39023", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `search-01` */
    val Search: ImageVector
        get() = cached("Search") {
            listOf(
                stroked("M17 17L21 21", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M19 11C19 6.58172 15.4183 3 11 3C6.58172 3 3 6.58172 3 11C3 15.4183 6.58172 19 11 19C15.4183 19 19 15.4183 19 11Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `arrow-down-01` */
    val ChevronDown: ImageVector
        get() = cached("ChevronDown") {
            listOf(
                stroked("M18 9.00005C18 9.00005 13.5811 15 12 15C10.4188 15 6 9 6 9", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `arrow-right-01` */
    val ChevronRight: ImageVector
        get() = cached("ChevronRight") {
            listOf(
                stroked("M9.00005 6C9.00005 6 15 10.4189 15 12C15 13.5812 9 18 9 18", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `arrow-left-01` */
    val ArrowLeft: ImageVector
        get() = cached("ArrowLeft") {
            listOf(
                stroked("M15 6C15 6 9.00001 10.4189 9 12C8.99999 13.5812 15 18 15 18", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `home-01` */
    val Home: ImageVector
        get() = cached("Home") {
            listOf(
                stroked("M15.0002 17C14.2007 17.6224 13.1504 18 12.0002 18C10.8499 18 9.79971 17.6224 9.00018 17", cap = StrokeCap.Round),
                stroked("M2.35157 13.2135C1.99855 10.9162 1.82204 9.76763 2.25635 8.74938C2.69065 7.73112 3.65421 7.03443 5.58132 5.64106L7.02117 4.6C9.41847 2.86667 10.6171 2 12.0002 2C13.3832 2 14.5819 2.86667 16.9792 4.6L18.419 5.64106C20.3462 7.03443 21.3097 7.73112 21.744 8.74938C22.1783 9.76763 22.0018 10.9162 21.6488 13.2135L21.3478 15.1724C20.8473 18.4289 20.5971 20.0572 19.4292 21.0286C18.2613 22 16.5538 22 13.139 22H10.8614C7.44652 22 5.73909 22 4.57118 21.0286C3.40327 20.0572 3.15305 18.4289 2.65261 15.1724L2.35157 13.2135Z", join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `settings-01` */
    val Settings: ImageVector
        get() = cached("Settings") {
            listOf(
                stroked("M21.3175 7.14139L20.8239 6.28479C20.4506 5.63696 20.264 5.31305 19.9464 5.18388C19.6288 5.05472 19.2696 5.15664 18.5513 5.36048L17.3311 5.70418C16.8725 5.80994 16.3913 5.74994 15.9726 5.53479L15.6357 5.34042C15.2766 5.11043 15.0004 4.77133 14.8475 4.37274L14.5136 3.37536C14.294 2.71534 14.1842 2.38533 13.9228 2.19657C13.6615 2.00781 13.3143 2.00781 12.6199 2.00781H11.5051C10.8108 2.00781 10.4636 2.00781 10.2022 2.19657C9.94085 2.38533 9.83106 2.71534 9.61149 3.37536L9.27753 4.37274C9.12465 4.77133 8.84845 5.11043 8.48937 5.34042L8.15249 5.53479C7.73374 5.74994 7.25259 5.80994 6.79398 5.70418L5.57375 5.36048C4.85541 5.15664 4.49625 5.05472 4.17867 5.18388C3.86109 5.31305 3.67445 5.63696 3.30115 6.28479L2.80757 7.14139C2.45766 7.74864 2.2827 8.05227 2.31666 8.37549C2.35061 8.69871 2.58483 8.95918 3.05326 9.48012L4.0843 10.6328C4.3363 10.9518 4.51521 11.5078 4.51521 12.0077C4.51521 12.5078 4.33636 13.0636 4.08433 13.3827L3.05326 14.5354C2.58483 15.0564 2.35062 15.3168 2.31666 15.6401C2.2827 15.9633 2.45766 16.2669 2.80757 16.8741L3.30114 17.7307C3.67443 18.3785 3.86109 18.7025 4.17867 18.8316C4.49625 18.9608 4.85542 18.8589 5.57377 18.655L6.79394 18.3113C7.25263 18.2055 7.73387 18.2656 8.15267 18.4808L8.4895 18.6752C8.84851 18.9052 9.12464 19.2442 9.2775 19.6428L9.61149 20.6403C9.83106 21.3003 9.94085 21.6303 10.2022 21.8191C10.4636 22.0078 10.8108 22.0078 11.5051 22.0078H12.6199C13.3143 22.0078 13.6615 22.0078 13.9228 21.8191C14.1842 21.6303 14.294 21.3003 14.5136 20.6403L14.8476 19.6428C15.0004 19.2442 15.2765 18.9052 15.6356 18.6752L15.9724 18.4808C16.3912 18.2656 16.8724 18.2055 17.3311 18.3113L18.5513 18.655C19.2696 18.8589 19.6288 18.9608 19.9464 18.8316C20.264 18.7025 20.4506 18.3785 20.8239 17.7307L21.3175 16.8741C21.6674 16.2669 21.8423 15.9633 21.8084 15.6401C21.7744 15.3168 21.5402 15.0564 21.0718 14.5354L20.0407 13.3827C19.7887 13.0636 19.6098 12.5078 19.6098 12.0077C19.6098 11.5078 19.7888 10.9518 20.0407 10.6328L21.0718 9.48012C21.5402 8.95918 21.7744 8.69871 21.8084 8.37549C21.8423 8.05227 21.6674 7.74864 21.3175 7.14139Z", cap = StrokeCap.Round),
                stroked("M15.5195 12C15.5195 13.933 13.9525 15.5 12.0195 15.5C10.0865 15.5 8.51953 13.933 8.51953 12C8.51953 10.067 10.0865 8.5 12.0195 8.5C13.9525 8.5 15.5195 10.067 15.5195 12Z"),
            )
        }

    /** Hugeicons `folder-01` */
    val Folder: ImageVector
        get() = cached("Folder") {
            listOf(
                stroked("M8 7H16.75C18.8567 7 19.91 7 20.6667 7.50559C20.9943 7.72447 21.2755 8.00572 21.4944 8.33329C22 9.08996 22 10.1433 22 12.25C22 15.7612 22 17.5167 21.1573 18.7779C20.7926 19.3238 20.3238 19.7926 19.7779 20.1573C18.5167 21 16.7612 21 13.25 21H12C7.28595 21 4.92893 21 3.46447 19.5355C2 18.0711 2 15.714 2 11V7.94427C2 6.1278 2 5.21956 2.38032 4.53806C2.65142 4.05227 3.05227 3.65142 3.53806 3.38032C4.21956 3 5.1278 3 6.94427 3C8.10802 3 8.6899 3 9.19926 3.19101C10.3622 3.62712 10.8418 4.68358 11.3666 5.73313L12 7", cap = StrokeCap.Round),
            )
        }

    /** Hugeicons `folder-block` */
    val FolderOff: ImageVector
        get() = cached("FolderOff") {
            listOf(
                stroked("M13 21H12C7.28595 21 4.92893 21 3.46447 19.5355C2 18.0711 2 15.714 2 11V7.94427C2 6.1278 2 5.21956 2.38032 4.53806C2.65142 4.05227 3.05227 3.65142 3.53806 3.38032C4.21956 3 5.1278 3 6.94427 3C8.10802 3 8.6899 3 9.19926 3.19101C10.3622 3.62712 10.8418 4.68358 11.3666 5.73313L12 7M8 7H16.75C18.8567 7 19.91 7 20.6667 7.50559C20.9943 7.72447 21.2755 8.00572 21.4944 8.33329C21.9796 9.05942 21.9992 10.0588 22 12", cap = StrokeCap.Round),
                stroked("M20.9749 19.9749C21.6082 19.3415 22 18.4665 22 17.5C22 15.567 20.433 14 18.5 14C17.5335 14 16.6585 14.3918 16.0251 15.0251M20.9749 19.9749C20.3415 20.6082 19.4665 21 18.5 21C16.567 21 15 19.433 15 17.5C15 16.5335 15.3918 15.6585 16.0251 15.0251M20.9749 19.9749L16.0251 15.0251"),
            )
        }

    /** Hugeicons `image-01` */
    val Image: ImageVector
        get() = cached("Image") {
            listOf(
                stroked("M2.5 12C2.5 7.52166 2.5 5.28249 3.89124 3.89124C5.28249 2.5 7.52166 2.5 12 2.5C16.4783 2.5 18.7175 2.5 20.1088 3.89124C21.5 5.28249 21.5 7.52166 21.5 12C21.5 16.4783 21.5 18.7175 20.1088 20.1088C18.7175 21.5 16.4783 21.5 12 21.5C7.52166 21.5 5.28249 21.5 3.89124 20.1088C2.5 18.7175 2.5 16.4783 2.5 12Z"),
                stroked("M5 21C9.37246 15.775 14.2741 8.88406 21.4975 13.5424"),
            )
        }

    /** Hugeicons `paint-board` */
    val Palette: ImageVector
        get() = cached("Palette") {
            listOf(
                stroked("M22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C12.8417 22 14 22.1163 14 21C14 20.391 13.6832 19.9212 13.3686 19.4544C12.9082 18.7715 12.4523 18.0953 13 17C13.6667 15.6667 14.7778 15.6667 16.4815 15.6667C17.3334 15.6667 18.3334 15.6667 19.5 15.5C21.601 15.1999 22 13.9084 22 12Z"),
                stroked("M7 15.002L7.00868 14.9996", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `moon-02` */
    val DarkMode: ImageVector
        get() = cached("DarkMode") {
            listOf(
                stroked("M21.5 14.0784C20.3003 14.7189 18.9301 15.0821 17.4751 15.0821C12.7491 15.0821 8.91792 11.2509 8.91792 6.52485C8.91792 5.06986 9.28105 3.69968 9.92163 2.5C5.66765 3.49698 2.5 7.31513 2.5 11.8731C2.5 17.1899 6.8101 21.5 12.1269 21.5C16.6849 21.5 20.503 18.3324 21.5 14.0784Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `calendar-01` */
    val Calendar: ImageVector
        get() = cached("Calendar") {
            listOf(
                stroked("M16 2V6M8 2V6", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M13 4H11C7.22876 4 5.34315 4 4.17157 5.17157C3 6.34315 3 8.22876 3 12V14C3 17.7712 3 19.6569 4.17157 20.8284C5.34315 22 7.22876 22 11 22H13C16.7712 22 18.6569 22 19.8284 20.8284C21 19.6569 21 17.7712 21 14V12C21 8.22876 21 6.34315 19.8284 5.17157C18.6569 4 16.7712 4 13 4Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M3 10H21", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M10 18.5002L9.99999 13.8474C9.99999 13.6557 9.86325 13.5002 9.69458 13.5002H9M14 18.4983L15.4855 13.8923C15.4951 13.8626 15.5 13.8315 15.5 13.8002C15.5 13.6346 15.3657 13.5002 15.2 13.5002L13 13.5", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `bookmark-01` */
    val Bookmark: ImageVector
        get() = cached("Bookmark") {
            listOf(
                stroked("M4 17.9808V9.70753C4 6.07416 4 4.25748 5.17157 3.12874C6.34315 2 8.22876 2 12 2C15.7712 2 17.6569 2 18.8284 3.12874C20 4.25748 20 6.07416 20 9.70753V17.9808C20 20.2867 20 21.4396 19.2272 21.8523C17.7305 22.6514 14.9232 19.9852 13.59 19.1824C12.8168 18.7168 12.4302 18.484 12 18.484C11.5698 18.484 11.1832 18.7168 10.41 19.1824C9.0768 19.9852 6.26947 22.6514 4.77285 21.8523C4 21.4396 4 20.2867 4 17.9808Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M4 7H20"),
            )
        }

    /** Hugeicons `square-lock-01` */
    val Lock: ImageVector
        get() = cached("Lock") {
            listOf(
                stroked("M4.26781 18.8447C4.49269 20.515 5.87613 21.8235 7.55966 21.9009C8.97627 21.966 10.4153 22 12 22C13.5847 22 15.0237 21.966 16.4403 21.9009C18.1239 21.8235 19.5073 20.515 19.7322 18.8447C19.879 17.7547 20 16.6376 20 15.5C20 14.3624 19.879 13.2453 19.7322 12.1553C19.5073 10.485 18.1239 9.17649 16.4403 9.09909C15.0237 9.03397 13.5847 9 12 9C10.4153 9 8.97627 9.03397 7.55966 9.09909C5.87613 9.17649 4.49269 10.485 4.26781 12.1553C4.12104 13.2453 4 14.3624 4 15.5C4 16.6376 4.12104 17.7547 4.26781 18.8447Z"),
                stroked("M7.5 9V6.5C7.5 4.01472 9.51472 2 12 2C14.4853 2 16.5 4.01472 16.5 6.5V9", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M11.9961 15.5H12.0051", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `text-font` */
    val TextFont: ImageVector
        get() = cached("TextFont") {
            listOf(
                stroked("M14 19L11.1069 10.7479C9.76348 6.91597 9.09177 5 8 5C6.90823 5 6.23652 6.91597 4.89309 10.7479L2 19M4.5 12H11.5", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M21.9692 13.9392V18.4392M21.9692 13.9392C22.0164 13.1161 22.0182 12.4891 21.9194 11.9773C21.6864 10.7709 20.4258 10.0439 19.206 9.89599C18.0385 9.75447 17.1015 10.055 16.1535 11.4363M21.9692 13.9392L19.1256 13.9392C18.6887 13.9392 18.2481 13.9603 17.8272 14.0773C15.2545 14.7925 15.4431 18.4003 18.0233 18.845C18.3099 18.8944 18.6025 18.9156 18.8927 18.9026C19.5703 18.8724 20.1955 18.545 20.7321 18.1301C21.3605 17.644 21.9692 16.9655 21.9692 15.9392V13.9392Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `audio-wave-01` */
    val Equalizer: ImageVector
        get() = cached("Equalizer") {
            listOf(
                stroked("M9 3V21", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M6 7V17", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M12 6V18", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M15 9L15 15", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M18 7L18 17", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M21 11L21 13", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M3 11L3 13", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `radio-01` */
    val Radio: ImageVector
        get() = cached("Radio") {
            listOf(
                stroked("M14 5C17.7712 5 19.6569 5 20.8284 6.2448C22 7.48959 22 9.49306 22 13.5C22 17.5069 22 19.5104 20.8284 20.7552C19.6569 22 17.7712 22 14 22H10C6.22876 22 4.34315 22 3.17157 20.7552C2 19.5104 2 17.5069 2 13.5C2 9.49306 2 7.48959 3.17157 6.2448C4.34315 5 6.22876 5 10 5L14 5Z", cap = StrokeCap.Round),
                stroked("M17.3965 11.2504C18.6389 13.4023 17.9016 16.154 15.7496 17.3965C13.5977 18.6389 10.846 17.9016 9.60354 15.7496M17.3965 11.2504C16.154 9.09842 13.4023 8.3611 11.2504 9.60354C9.09842 10.846 8.3611 13.5977 9.60354 15.7496M17.3965 11.2504L9.60354 15.7496"),
                stroked("M17 2L7 5", cap = StrokeCap.Round),
                stroked("M6 9H6.00898", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `user` */
    val Person: ImageVector
        get() = cached("Person") {
            listOf(
                stroked("M17 8.5C17 5.73858 14.7614 3.5 12 3.5C9.23858 3.5 7 5.73858 7 8.5C7 11.2614 9.23858 13.5 12 13.5C14.7614 13.5 17 11.2614 17 8.5Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M19 20.5C19 16.634 15.866 13.5 12 13.5C8.13401 13.5 5 16.634 5 20.5", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `user-circle` */
    val Account: ImageVector
        get() = cached("Account") {
            listOf(
                stroked("M15 9C15 7.34315 13.6569 6 12 6C10.3431 6 9 7.34315 9 9C9 10.6569 10.3431 12 12 12C13.6569 12 15 10.6569 15 9Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M17 17C17 14.2386 14.7614 12 12 12C9.23858 12 7 14.2386 7 17", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `speaker-01` */
    val Speaker: ImageVector
        get() = cached("Speaker") {
            listOf(
                stroked("M3.5 10C3.5 6.22876 3.5 4.34315 4.7448 3.17157C5.98959 2 7.99306 2 12 2C16.0069 2 18.0104 2 19.2552 3.17157C20.5 4.34315 20.5 6.22876 20.5 10V14C20.5 17.7712 20.5 19.6569 19.2552 20.8284C18.0104 22 16.0069 22 12 22C7.99306 22 5.98959 22 4.7448 20.8284C3.5 19.6569 3.5 17.7712 3.5 14V10Z"),
                stroked("M10 6H14", cap = StrokeCap.Round),
            )
        }

    /** Hugeicons `speaker` */
    val SpeakerGroup: ImageVector
        get() = cached("SpeakerGroup") {
            listOf(
                stroked("M20.5 13.5V10.5C20.5 6.74142 20.5 4.86213 19.4472 3.60746C19.2788 3.40678 19.0932 3.22119 18.8925 3.0528C17.6379 2 15.7586 2 12 2C8.24142 2 6.36213 2 5.10746 3.0528C4.90678 3.22119 4.72119 3.40678 4.5528 3.60746C3.5 4.86213 3.5 6.74142 3.5 10.5V13.5C3.5 17.2586 3.5 19.1379 4.5528 20.3925C4.72119 20.5932 4.90678 20.7788 5.10746 20.9472C6.36213 22 8.24142 22 12 22C15.7586 22 17.6379 22 18.8925 20.9472C19.0932 20.7788 19.2788 20.5932 19.4472 20.3925C20.5 19.1379 20.5 17.2586 20.5 13.5Z", cap = StrokeCap.Round),
                stroked("M15.5 15C15.5 16.933 13.933 18.5 12 18.5C10.067 18.5 8.5 16.933 8.5 15C8.5 13.067 10.067 11.5 12 11.5C13.933 11.5 15.5 13.067 15.5 15Z"),
                stroked("M13.5 7C13.5 7.82843 12.8284 8.5 12 8.5C11.1716 8.5 10.5 7.82843 10.5 7C10.5 6.17157 11.1716 5.5 12 5.5C12.8284 5.5 13.5 6.17157 13.5 7Z"),
            )
        }

    /** Hugeicons `smart-phone-01` */
    val Phone: ImageVector
        get() = cached("Phone") {
            listOf(
                stroked("M12 19H12.01", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M13.5 2H10.5C8.14298 2 6.96447 2 6.23223 2.73223C5.5 3.46447 5.5 4.64298 5.5 7V17C5.5 19.357 5.5 20.5355 6.23223 21.2678C6.96447 22 8.14298 22 10.5 22H13.5C15.857 22 17.0355 22 17.7678 21.2678C18.5 20.5355 18.5 19.357 18.5 17V7C18.5 4.64298 18.5 3.46447 17.7678 2.73223C17.0355 2 15.857 2 13.5 2Z", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `headphones` */
    val Headphones: ImageVector
        get() = cached("Headphones") {
            listOf(
                stroked("M17 14.3045C17 13.9588 17 13.786 17.052 13.632C17.2032 13.1844 17.6018 13.0108 18.0011 12.8289C18.45 12.6244 18.6744 12.5222 18.8968 12.5042C19.1493 12.4838 19.4022 12.5382 19.618 12.6593C19.9041 12.8198 20.1036 13.1249 20.3079 13.373C21.2513 14.5188 21.7229 15.0918 21.8955 15.7236C22.0348 16.2334 22.0348 16.7666 21.8955 17.2764C21.6438 18.1979 20.8485 18.9704 20.2598 19.6854C19.9587 20.0511 19.8081 20.234 19.618 20.3407C19.4022 20.4618 19.1493 20.5162 18.8968 20.4958C18.6744 20.4778 18.45 20.3756 18.0011 20.1711C17.6018 19.9892 17.2032 19.8156 17.052 19.368C17 19.214 17 19.0412 17 18.6955V14.3045Z"),
                stroked("M7 14.3046C7 13.8694 6.98778 13.4782 6.63591 13.1722C6.50793 13.0609 6.33825 12.9836 5.99891 12.829C5.55001 12.6246 5.32556 12.5224 5.10316 12.5044C4.43591 12.4504 4.07692 12.9058 3.69213 13.3732C2.74875 14.519 2.27706 15.0919 2.10446 15.7237C1.96518 16.2336 1.96518 16.7668 2.10446 17.2766C2.3562 18.1981 3.15152 18.9705 3.74021 19.6856C4.11129 20.1363 4.46577 20.5475 5.10316 20.496C5.32556 20.478 5.55001 20.3757 5.99891 20.1713C6.33825 20.0167 6.50793 19.9394 6.63591 19.8281C6.98778 19.5221 7 19.131 7 18.6957V14.3046Z"),
                stroked("M19 12.5V10.5C19 6.63401 15.866 3.5 12 3.5C8.13401 3.5 5 6.63401 5 10.5V12.5", cap = StrokeCap.Square, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `bluetooth` */
    val Bluetooth: ImageVector
        get() = cached("Bluetooth") {
            listOf(
                stroked("M11.9994 12V6.66586C11.9994 4.80386 11.9994 3.87286 12.5847 3.578C13.17 3.28313 13.9092 3.84173 15.3877 4.95893L16.0935 5.49234C17.1288 6.27468 17.6465 6.66586 17.6465 7.19927C17.6465 7.73268 17.1288 8.12386 16.0935 8.9062L11.9994 12ZM11.9994 12V17.3341C11.9994 19.1961 11.9994 20.1271 12.5847 20.422C13.17 20.7169 13.9092 20.1583 15.3877 19.0411L16.0935 18.5077C17.1288 17.7253 17.6465 17.3341 17.6465 16.8007C17.6465 16.2673 17.1288 15.8761 16.0935 15.0938L11.9994 12ZM11.9994 12L5.64648 7.19927M11.9994 12L5.64648 16.8007", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M18.9998 12H19.0088", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M4.99981 12H5.00879", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `wifi-01` */
    val Wifi: ImageVector
        get() = cached("Wifi") {
            listOf(
                stroked("M8.25 14.5C10.25 12.5 13.75 12.5 15.75 14.5", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M18.5 11.5C14.7324 8.16667 9.5 8.16667 5.5 11.5", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M2 8.5C8.31579 3.16669 15.6842 3.16668 22 8.49989", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `usb` */
    val Usb: ImageVector
        get() = cached("Usb") {
            listOf(
                stroked("M15.5 6V5.5C15.5 4.09554 15.5 3.39331 15.1629 2.88886C15.017 2.67048 14.8295 2.48298 14.6111 2.33706C14.1067 2 13.4045 2 12 2C10.5955 2 9.89331 2 9.38886 2.33706C9.17048 2.48298 8.98298 2.67048 8.83706 2.88886C8.5 3.39331 8.5 4.09554 8.5 5.5V6"),
                stroked("M6.00446 7.11331C5.93719 6.24273 6.63957 5.5 7.53014 5.5H16.4699C17.3604 5.5 18.0628 6.24273 17.9955 7.11331L17.8117 9.49197C17.6796 11.2019 17.1011 12.8498 16.132 14.2773L15.5312 15.1622C14.9638 15.9979 14.0077 16.5 12.9838 16.5H11.0162C9.99228 16.5 9.03617 15.9979 8.46881 15.1622L7.86803 14.2773C6.89885 12.8498 6.32041 11.2019 6.18827 9.49197L6.00446 7.11331Z"),
                stroked("M12 17V22", cap = StrokeCap.Round, join = StrokeJoin.Round),
                stroked("M11 8.5H13", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

    /** Hugeicons `tv-01` */
    val Tv: ImageVector
        get() = cached("Tv") {
            listOf(
                stroked("M2 14C2 10.2288 2 8.34315 3.17157 7.17157C4.34315 6 6.22876 6 10 6H14C17.7712 6 19.6569 6 20.8284 7.17157C22 8.34315 22 10.2288 22 14C22 17.7712 22 19.6569 20.8284 20.8284C19.6569 22 17.7712 22 14 22H10C6.22876 22 4.34315 22 3.17157 20.8284C2 19.6569 2 17.7712 2 14Z", cap = StrokeCap.Round),
                stroked("M9 3L12 6L16 2", cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
}

/** Nominal size; every call site that cares sets its own via `Modifier.size(...)`. */
private val ICON_SIZE = 24.dp

/**
 * Hugeicons draws on a 24px grid at 1.5px. The stroke scales with the icon, so this
 * is the one number to change to make the whole set lighter or heavier.
 */
private const val STROKE_WIDTH = 1.5f

private class HugePath(
        val data: String,
        val cap: StrokeCap = StrokeCap.Butt,
        val join: StrokeJoin = StrokeJoin.Miter,
        val filled: Boolean = false,
)

private fun stroked(
        data: String,
        cap: StrokeCap = StrokeCap.Butt,
        join: StrokeJoin = StrokeJoin.Miter,
) = HugePath(data, cap, join)

private fun filled(data: String) = HugePath(data, filled = true)

private val cache = ConcurrentHashMap<String, ImageVector>()

private fun cached(name: String, paths: () -> List<HugePath>): ImageVector =
        cache.computeIfAbsent(name) { key -> build(key, paths()) }

private fun build(name: String, paths: List<HugePath>): ImageVector =
        ImageVector.Builder(
                        name = name,
                        defaultWidth = ICON_SIZE,
                        defaultHeight = ICON_SIZE,
                        viewportWidth = 24f,
                        viewportHeight = 24f,
                )
                .apply {
                    for (path in paths) {
                        val nodes = PathParser().parsePathString(path.data).toNodes()
                        // Black is a placeholder: Icon() tints over it. It only shows
                        // through if a vector is drawn without a colour filter.
                        if (path.filled) {
                            addPath(pathData = nodes, fill = SolidColor(Color.Black))
                        } else {
                            addPath(
                                    pathData = nodes,
                                    stroke = SolidColor(Color.Black),
                                    strokeLineWidth = STROKE_WIDTH,
                                    strokeLineCap = path.cap,
                                    strokeLineJoin = path.join,
                            )
                        }
                    }
                }
                .build()
