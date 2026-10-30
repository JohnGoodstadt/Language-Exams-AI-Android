package com.goodstadt.john.language.exams.screens.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.ui.theme.LocalExtendedColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom loading spinners for the app-start "Connecting..." screen (MainScreen, AuthUiState.Loading).
 *
 * All three use the primary colours (red, yellow and the app's button blue) and are driven by a running
 * clock fed into sine waves whose speeds are deliberately irrational multiples of each other (√2, √3, π…),
 * so the movement never lines up into a visible loop - over a few seconds of loading it never repeats.
 *
 * To try one, change [LAUNCH_SPINNER_STYLE].
 */
enum class LaunchSpinnerStyle { MATERIAL, WAVEFORM, ORBIT, COLOUR_MIX }

/** Which spinner the app-start screen shows. */
// The launch window (res/drawable/launch_background.xml) shows this spinner's first frame as a still image
// before the app draws, so keep the two in step: ORBIT <-> launch_orbit.xml.
val LAUNCH_SPINNER_STYLE = LaunchSpinnerStyle.ORBIT

// Sampled from the app icon (the face red and the "de" yellow).
private val SpinnerRed = Color(0xFFFD0535)
private val SpinnerYellow = Color(0xFFFFBE02)

private const val SQRT2 = 1.41421356f
private const val SQRT3 = 1.73205081f
private const val PI_F = PI.toFloat()

@Composable
fun LaunchSpinner(
    modifier: Modifier = Modifier,
    style: LaunchSpinnerStyle = LAUNCH_SPINNER_STYLE,
    size: Dp = 72.dp
) {
    Box(modifier) {
        when (style) {
            LaunchSpinnerStyle.MATERIAL -> CircularProgressIndicator(Modifier.size(size))
            LaunchSpinnerStyle.WAVEFORM -> WaveformSpinner(size)
            LaunchSpinnerStyle.ORBIT -> OrbitSpinner(size)
            LaunchSpinnerStyle.COLOUR_MIX -> ColourMixSpinner(size)
        }
    }
}

/** Seconds since first shown, updated every frame. Read it inside a draw lambda so only drawing reruns. */
@Composable
private fun rememberSpinnerClock(): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return seconds
}

/**
 * 1. Waveform - five rounded bars rising and falling like a voice speaking (the app is all about hearing
 * sentences). Each bar mixes two sine waves at unrelated speeds, so the "speech" never loops.
 */
@Composable
fun WaveformSpinner(size: Dp = 72.dp) {
    val clock = rememberSpinnerClock()
    val blue = LocalExtendedColors.current.buttonBlue
    val colours = listOf(blue, SpinnerRed, SpinnerYellow, blue, SpinnerRed)
    Canvas(Modifier.size(size)) {
        val t = clock.value
        val count = colours.size
        val gap = this.size.width * 0.08f
        val barWidth = (this.size.width - gap * (count - 1)) / count
        colours.forEachIndexed { i, colour ->
            val wave = (sin(t * (2.3f + i * SQRT2 * 0.7f) + i * 1.7f) +
                    sin(t * (1.1f + i * SQRT3 * 0.45f) + i * 0.9f)) / 2f
            val fraction = 0.22f + 0.78f * abs(wave)
            val barHeight = this.size.height * fraction
            drawRoundRect(
                color = colour,
                topLeft = Offset(i * (barWidth + gap), (this.size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

/**
 * 2. Orbit - a red, a yellow and a blue dot circling the centre at different speeds (one the other way),
 * each drifting in and out from the centre, with a short fading trail.
 */
@Composable
fun OrbitSpinner(size: Dp = 72.dp) {
    val clock = rememberSpinnerClock()
    val dots = listOf(
        Triple(SpinnerRed, 2.4f, 1.3f),                      // colour, angular speed, in/out speed
        Triple(SpinnerYellow, -2.4f * SQRT2 / 1.6f, 1.3f * SQRT3 / 1.4f),
        Triple(LocalExtendedColors.current.buttonBlue, 2.4f * PI_F / 4.1f, 1.3f * SQRT2)
    )
    Canvas(Modifier.size(size)) {
        val t = clock.value
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = this.size.minDimension * 0.38f
        val dotRadius = this.size.minDimension * 0.085f
        dots.forEachIndexed { i, (colour, spin, breathe) ->
            // Draw the trail first (older = smaller, fainter), then the dot itself.
            for (step in 6 downTo 0) {
                val ts = t - step * 0.035f
                val angle = ts * spin + i * 2.1f
                val r = maxRadius * (0.55f + 0.45f * sin(ts * breathe + i * 1.3f))
                val p = Offset(centre.x + r * cos(angle), centre.y + r * sin(angle))
                val fade = 1f - step / 7f
                drawCircle(colour.copy(alpha = fade * fade), radius = dotRadius * (0.45f + 0.55f * fade), center = p)
            }
        }
    }
}

/**
 * 3. Colour mix - three see-through primary-colour circles drifting around each other. Where they overlap
 * the colours mix like paint (red + yellow = orange, yellow + blue = green, all three = dark), so the
 * picture keeps changing. Drawn on its own layer so the mixing works on light and dark backgrounds.
 */
@Composable
fun ColourMixSpinner(size: Dp = 72.dp) {
    val clock = rememberSpinnerClock()
    val blue = LocalExtendedColors.current.buttonBlue
    Canvas(
        Modifier
            .size(size)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val t = clock.value
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        val drift = this.size.minDimension * 0.2f
        val radius = this.size.minDimension * 0.27f
        val circles = listOf(
            Triple(SpinnerRed, 1.1f, 0f),
            Triple(SpinnerYellow, 1.1f * SQRT2, 2.1f),
            Triple(blue, 1.1f * SQRT3, 4.2f)
        )
        circles.forEach { (colour, speed, phase) ->
            // Lissajous-style drift: x and y move at unrelated speeds, so the paths never close.
            val p = Offset(
                centre.x + drift * cos(t * speed + phase),
                centre.y + drift * sin(t * speed * 0.83f * SQRT2 + phase * 1.3f)
            )
            val r = radius * (0.9f + 0.1f * sin(t * speed * 1.7f + phase))
            drawCircle(colour.copy(alpha = 0.9f), radius = r, center = p, blendMode = BlendMode.Multiply)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WaveformSpinnerPreview() = WaveformSpinner()

@Preview(showBackground = true)
@Composable
private fun OrbitSpinnerPreview() = OrbitSpinner()

@Preview(showBackground = true)
@Composable
private fun ColourMixSpinnerPreview() = ColourMixSpinner()
