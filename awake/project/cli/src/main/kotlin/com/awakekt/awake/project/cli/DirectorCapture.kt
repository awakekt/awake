/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.OfflineAudioMixer
import com.awakekt.awake.core.audio.WavDecoder
import com.awakekt.awake.core.camera.CameraOptics
import com.awakekt.awake.core.input.InputTake
import com.awakekt.awake.director.AudioCueSpec
import com.awakekt.awake.director.DirectorPlayer
import com.awakekt.awake.director.DirectorScript
import com.awakekt.awake.director.FramingAnalyzer
import com.awakekt.awake.director.FramingSpec
import com.awakekt.awake.director.InputTakeJson
import com.awakekt.awake.director.ShotFramingMetrics
import com.awakekt.awake.director.ShotRigSpec
import com.awakekt.awake.director.ShotSize
import com.awakekt.awake.director.ShotSpec
import com.awakekt.awake.director.TrailerReel
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.project.runtime.DirectorRuntimeOverride
import com.awakekt.awake.project.runtime.loadProject
import com.awakekt.awake.project.runtime.runProject
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Standard trailer and social capture presets naming resolution and frame rate cleanly.
 */
internal enum class CapturePreset(
    val label: String,
    val width: Int,
    val height: Int,
    val fps: Int,
) {
    Youtube1080p("youtube-1080p", 1920, 1080, 60),
    Youtube720p("youtube-720p", 1280, 720, 60),
    Shorts1080p("shorts-1080p", 1080, 1920, 60),
    Cinemascope1080p("cinemascope-1080p", 1920, 804, 24),
    GifPreview("gif-preview", 640, 360, 24),
    ;

    companion object {
        fun named(label: String): CapturePreset = entries.firstOrNull { it.label == label.lowercase() }
            ?: throw UsageException("no preset '$label'; use ${entries.joinToString { it.label }}")
    }
}

/** Output container format for `awake capture`. */
internal enum class CaptureFormat(val label: String) {
    Mp4("mp4"),
    Webm("webm"),
    Gif("gif"),
    PngSequence("png-sequence"),
    ;

    companion object {
        fun fromOutputOrLabel(output: File, explicit: String?): CaptureFormat {
            if (explicit != null) {
                return entries.firstOrNull { it.label == explicit.lowercase() }
                    ?: throw UsageException("no capture format '$explicit'; use ${entries.joinToString { it.label }}")
            }
            return when {
                output.name.endsWith(".mp4", ignoreCase = true) -> Mp4
                output.name.endsWith(".webm", ignoreCase = true) -> Webm
                output.name.endsWith(".gif", ignoreCase = true) -> Gif
                else -> PngSequence
            }
        }
    }
}

/** Parameters for rendering a storyboard contact sheet or single shot preview. */
internal class StoryboardRequest(
    val scene: File,
    val script: DirectorScript,
    val inputTake: InputTake? = null,
    val tileWidth: Int = DEFAULT_TILE_WIDTH,
    val tileHeight: Int = DEFAULT_TILE_HEIGHT,
    val samples: List<Float> = DEFAULT_SAMPLES,
    val drawGuides: Boolean = true,
    val backend: RenderBackend = RenderBackend.default(),
    val shotFilterId: String? = null,
) {
    private companion object {
        const val DEFAULT_TILE_WIDTH = 480
        const val DEFAULT_TILE_HEIGHT = 270
        val DEFAULT_SAMPLES = listOf(0.05f, 0.50f, 0.95f)
    }
}

/** Structured storyboard telemetry emitted alongside the rendered contact sheet PNG. */
@Serializable
internal data class StoryboardReport(
    val scene: String,
    val scriptName: String,
    val durationSeconds: Float,
    val width: Int,
    val height: Int,
    val samples: List<ShotFramingMetrics>,
)

/** Rendered storyboard contact sheet PNG plus per-sample cinematography telemetry. */
internal class StoryboardResult(
    val pngBytes: ByteArray,
    val report: StoryboardReport,
)

/** One playable segment inside a single-scene or multi-scene trailer capture. */
internal class CaptureSegment(
    val sceneFile: File,
    val script: DirectorScript,
    val inputTake: InputTake? = null,
)

/** Parameters for deterministic headless gameplay trailer capture (`awake capture`). */
internal class TrailerCaptureRequest(
    val segments: List<CaptureSegment>,
    val output: File,
    val format: CaptureFormat,
    val width: Int,
    val height: Int,
    val fps: Int,
    val backend: RenderBackend = RenderBackend.default(),
    val audioOverride: File? = null,
    val hudOverride: Boolean? = null,
    val keepFrames: Boolean = false,
)

/** Structured result returned by `awake capture` and MCP `capture_trailer`. */
@Serializable
internal data class TrailerCaptureReport(
    val output: String,
    val format: String,
    val encoder: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val framesCaptured: Int,
    val durationSeconds: Float,
    val averageLuminance: Float,
    val audioPeakAmplitude: Float,
    val audioWav: String? = null,
    val keptFramesDir: String? = null,
)

/**
 * Renders a multi-shot storyboard contact sheet (or single-shot preview) by stepping a real
 * headless Awake project with [DirectorRuntimeOverride], measuring rendered luminance and
 * [FramingAnalyzer] diagnostics, and optionally annotating tiles with Rule-of-Thirds and
 * action-safe overlays.
 */
internal class StoryboardRenderer(private val project: ProjectScenes) {

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun render(request: StoryboardRequest): StoryboardResult = runBlocking {
        val sceneName = project.relative(request.scene)
        val probePlayer = DirectorPlayer(request.script)
        val shotIndices = if (request.shotFilterId != null) {
            val idx = probePlayer.findShotIndex(request.shotFilterId)
            if (idx < 0) {
                val names = request.script.shots.joinToString { it.id }
                throw CommandFailure("no shot '${request.shotFilterId}' in script '${request.script.name}'; available: $names")
            }
            listOf(idx)
        } else {
            request.script.shots.indices.toList()
        }
        if (shotIndices.isEmpty()) throw CommandFailure("director script '${request.script.name}' has no shots")

        val sampleFractions = request.samples.ifEmpty { listOf(HALF) }.map { it.coerceIn(0f, 1f) }
        val schedule = buildList {
            for (rowIndex in shotIndices.indices) {
                val shotIdx = shotIndices[rowIndex]
                val start = probePlayer.shotStartTime(shotIdx)
                val dur = probePlayer.shotDuration(shotIdx)
                val shot = request.script.shots[shotIdx]
                sampleFractions.forEachIndexed { colIndex, frac ->
                    val target = (start + dur * frac).coerceAtMost(probePlayer.totalDurationSeconds)
                    add(ScheduledSample(rowIndex, colIndex, shot, target))
                }
            }
        }.sortedBy { it.targetTime }

        val loaded = try {
            loadProject(ProjectRender(project).sceneAsEntry(sceneName), physicsWorld = { createJoltPhysicsWorld() })
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            throw CommandFailure("$sceneName can't be played: ${error.message}", error)
        }

        val tiles = Array(shotIndices.size) { arrayOfNulls<BufferedImage>(sampleFractions.size) }
        val metrics = MutableList<ShotFramingMetrics?>(schedule.size) { null }
        val aspect = request.tileWidth.toFloat() / request.tileHeight.toFloat()

        loaded.use {
            val override = DirectorRuntimeOverride(
                script = request.script,
                inputTake = request.inputTake,
                aspectRatioProvider = { aspect },
                loop = false,
            )
            val game = app { scene("storyboard") { runProject(loaded, directorOverride = override) } }
            val renderHelper = ProjectRender(project)
            renderHelper.openSession(request.tileWidth, request.tileHeight, request.backend, game).use { session ->
                try {
                    game.ready(session.renderer)
                    val runtime = game.requireService<SceneAppLifecycleRuntime>()
                    val step = STORYBOARD_STEP
                    var nextIndex = 0
                    val maxTime = schedule.last().targetTime
                    val maxFrames = ceil((maxTime + step) / step).toInt().coerceAtLeast(1) + EXTRA_SAFETY_FRAMES

                    // Frame 0 initial advance so scene systems + DirectorSystem initialize cleanly.
                    var elapsed = 0f
                    var frame = 0
                    while (nextIndex < schedule.size && frame <= maxFrames) {
                        game.update(step, request.tileWidth.toFloat(), request.tileHeight.toFloat())
                        elapsed += step
                        frame++
                        val dirSystem = override.activeDirectorSystem
                        val currentTime = dirSystem?.activePlayer?.currentTimeSeconds ?: elapsed

                        while (nextIndex < schedule.size && (currentTime + step * HALF >= schedule[nextIndex].targetTime || dirSystem?.activePlayer?.isFinished == true)) {
                            val item = schedule[nextIndex]
                            val rgba = renderHelper.readbackRgba(runtime, request.tileWidth, request.tileHeight, sceneName)
                            val pose = dirSystem?.lastPose
                            if (pose != null) {
                                applyFrameOverlays(rgba, request.tileWidth, request.tileHeight, pose.fadeBlack, pose.letterboxAspect)
                            }
                            val lum = measureAverageLuminance(rgba, request.tileWidth, request.tileHeight)
                            val framing = shotFraming(item.shot)
                            val beat = request.script.clock.beatAt(currentTime)
                            val shotMetric = if (dirSystem != null && pose != null) {
                                FramingAnalyzer.analyze(
                                    shotId = item.shot.id,
                                    timeSeconds = currentTime,
                                    beat = beat,
                                    pose = pose,
                                    aspectRatio = aspect,
                                    subjects = framing?.subjects.orEmpty(),
                                    targetShotSize = framing?.shotSize,
                                    stage = dirSystem.stageQuery,
                                    averageLuminance = lum,
                                )
                            } else {
                                ShotFramingMetrics(
                                    shotId = item.shot.id,
                                    timeSeconds = currentTime,
                                    beat = beat,
                                    averageLuminance = lum,
                                )
                            }
                            metrics[nextIndex] = shotMetric
                            val tileImg = RenderedImage(request.tileWidth, request.tileHeight, rgba).toBufferedImage()
                            if (request.drawGuides) {
                                val focalMm = pose?.let { CameraOptics.fovYRadiansToFocalLengthMm(it.fovYRadians) }
                                drawStoryboardGuides(tileImg, item.shot, shotMetric, focalMm)
                            }
                            tiles[item.row][item.col] = tileImg
                            nextIndex++
                        }
                    }
                } finally {
                    session.renderer.waitIdle()
                    game.dispose()
                }
            }
        }

        val sheet = composeContactSheet(tiles, request.tileWidth, request.tileHeight)
        val outBytes = ByteArrayOutputStream()
        if (!ImageIO.write(sheet, "png", outBytes)) throw CommandFailure("no PNG writer")
        val report = StoryboardReport(
            scene = sceneName,
            scriptName = request.script.name,
            durationSeconds = probePlayer.totalDurationSeconds,
            width = sheet.width,
            height = sheet.height,
            samples = metrics.filterNotNull(),
        )
        StoryboardResult(outBytes.toByteArray(), report)
    }

    private fun composeContactSheet(
        tiles: Array<Array<BufferedImage?>>,
        tileWidth: Int,
        tileHeight: Int,
    ): BufferedImage {
        val rows = tiles.size
        val cols = tiles.first().size
        if (rows == 1 && cols == 1 && tiles[0][0] != null) {
            return tiles[0][0]!!
        }
        val gutter = SHEET_GUTTER
        val sheetW = cols * tileWidth + (cols + 1) * gutter
        val sheetH = rows * tileHeight + (rows + 1) * gutter
        val sheet = BufferedImage(sheetW, sheetH, BufferedImage.TYPE_INT_ARGB)
        val g = sheet.createGraphics()
        try {
            g.color = Color(SHEET_BG_R, SHEET_BG_G, SHEET_BG_B)
            g.fillRect(0, 0, sheetW, sheetH)
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val tile = tiles[r][c] ?: continue
                    val x = gutter + c * (tileWidth + gutter)
                    val y = gutter + r * (tileHeight + gutter)
                    g.drawImage(tile, x, y, null)
                }
            }
        } finally {
            g.dispose()
        }
        return sheet
    }

    @Suppress("MagicNumber")
    private fun drawStoryboardGuides(
        image: BufferedImage,
        shot: ShotSpec,
        metrics: ShotFramingMetrics,
        focalLengthMm: Float?,
    ) {
        val g = image.createGraphics()
        try {
            val w = image.width
            val h = image.height
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Subtle Rule-of-Thirds lines.
            g.color = Color(255, 255, 255, 70)
            g.stroke = BasicStroke(1f)
            g.drawLine(w / 3, 0, w / 3, h)
            g.drawLine((w * 2) / 3, 0, (w * 2) / 3, h)
            g.drawLine(0, h / 3, w, h / 3)
            g.drawLine(0, (h * 2) / 3, w, (h * 2) / 3)

            // Action-safe 5% inset frame.
            val insetX = (w * 0.05f).roundToInt()
            val insetY = (h * 0.05f).roundToInt()
            g.color = Color(255, 255, 255, 45)
            g.drawRect(insetX, insetY, w - insetX * 2, h - insetY * 2)

            // Projected subject bounding box when available and visible.
            val hasVisibleSubject = metrics.subjectName != null && metrics.subjectVisible
            if (hasVisibleSubject && metrics.coverageWidth > 0f && metrics.coverageHeight > 0f) {
                val boxW = (metrics.coverageWidth * w).roundToInt().coerceAtLeast(4)
                val boxH = (metrics.coverageHeight * h).roundToInt().coerceAtLeast(4)
                val boxX = (metrics.screenCenterX * w - boxW * 0.5f).roundToInt()
                val boxY = (metrics.screenCenterY * h - boxH * 0.5f).roundToInt()
                g.color = when {
                    metrics.occlusionFraction >= 0.4f -> Color(255, 70, 70, 210)
                    metrics.warnings.isNotEmpty() -> Color(255, 195, 40, 210)
                    else -> Color(70, 235, 130, 200)
                }
                g.stroke = BasicStroke(2f)
                g.drawRect(boxX, boxY, boxW, boxH)
            }

            // Top translucent telemetry header banner.
            val bannerH = (h / 11).coerceIn(18, 28)
            g.color = Color(12, 14, 18, 185)
            g.fillRect(0, 0, w, bannerH)
            val fontSize = (bannerH * 0.55f).roundToInt().coerceIn(10, 14)
            g.font = Font(Font.MONOSPACED, Font.BOLD, fontSize)
            val lensStr = focalLengthMm?.let { "${it.roundToInt()}mm" } ?: ""
            val covStr = if (metrics.subjectName != null) " cov=${(metrics.coverageHeight * 100f).roundToInt()}%" else ""
            val warnStr = if (metrics.warnings.isNotEmpty()) " [!${metrics.warnings.size}]" else ""
            val label = "${shot.id} t=${formatTenths(metrics.timeSeconds)}s $lensStr$covStr$warnStr".trim()
            g.color = if (metrics.warnings.isEmpty()) Color(235, 240, 245) else Color(255, 200, 70)
            g.drawString(label, 8, bannerH - (bannerH - fontSize) / 2 - 2)
        } finally {
            g.dispose()
        }
    }

    private data class ScheduledSample(
        val row: Int,
        val col: Int,
        val shot: ShotSpec,
        val targetTime: Float,
    )

    private companion object {
        const val STORYBOARD_STEP = 1f / 60f
        const val EXTRA_SAFETY_FRAMES = 4
        const val HALF = 0.5f
        const val SHEET_GUTTER = 4
        const val SHEET_BG_R = 16
        const val SHEET_BG_G = 18
        const val SHEET_BG_B = 22
    }
}

/**
 * Deterministic fixed-step headless trailer recorder stepping game simulation, `DirectorSystem`,
 * and [OfflineAudioMixer] in frame-exact lockstep, exporting PNG sequences + WAV or streaming raw
 * RGBA frames directly into `ffmpeg` / `gifski` for MP4, WebM, and GIF delivery.
 */
internal class TrailerCapture(private val project: ProjectScenes) {

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun capture(request: TrailerCaptureRequest): TrailerCaptureReport = runBlocking {
        if (request.segments.isEmpty()) throw CommandFailure("capture needs at least one scene segment")
        val step = 1f / request.fps.toFloat()
        val aspect = request.width.toFloat() / request.height.toFloat()
        val mixer = OfflineAudioMixer(sampleRate = AUDIO_SAMPLE_RATE)

        // Start external --audio override on the shared offline mixer if supplied.
        request.audioOverride?.let { overrideFile ->
            if (!overrideFile.isFile) throw UsageException("no audio file ${overrideFile.path}")
            val clip = WavDecoder.decode(overrideFile.name, overrideFile.name, overrideFile.readBytes())
                ?: throw CommandFailure("unsupported audio format (expected uncompressed PCM WAV): ${overrideFile.path}")
            mixer.playMusic(clip, volume = 1f, loop = false)
        }

        val tempDir = Files.createTempDirectory("awake-capture").toFile()
        var videoProcess: Process? = null
        var videoStdin: OutputStream? = null
        var rawVideoTarget: File? = null
        var framesDir: File? = null
        var encoderLabel = "png-sequence"

        var totalFrames = 0
        var luminanceSum = 0.0

        try {
            when (request.format) {
                CaptureFormat.PngSequence -> {
                    request.output.mkdirs()
                    framesDir = request.output
                    encoderLabel = "png-sequence"
                }

                CaptureFormat.Gif -> {
                    framesDir = if (request.keepFrames) {
                        request.output.absoluteFile.parentFile.resolve("${request.output.nameWithoutExtension}-frames").also(File::mkdirs)
                    } else {
                        tempDir.resolve("frames").also(File::mkdirs)
                    }
                    encoderLabel = if (findExecutable("gifski") != null) "gifski" else "ffmpeg"
                }

                CaptureFormat.Mp4, CaptureFormat.Webm -> {
                    val ffmpeg = findExecutable("ffmpeg")
                        ?: throw CommandFailure("ffmpeg was not found on PATH; install ffmpeg or capture to a directory (--format png-sequence)")
                    encoderLabel = "ffmpeg"
                    if (request.keepFrames) {
                        framesDir = request.output.absoluteFile.parentFile
                            .resolve("${request.output.nameWithoutExtension}-frames")
                            .also(File::mkdirs)
                    }
                    rawVideoTarget = tempDir.resolve("video_only.${request.format.label}")
                    val cmd = buildRawVideoFfmpegCommand(ffmpeg, request, rawVideoTarget)
                    val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
                    videoProcess = proc
                    videoStdin = proc.outputStream
                }
            }

            val renderHelper = ProjectRender(project)
            for (segment in request.segments) {
                val sceneName = project.relative(segment.sceneFile)
                val clips = loadDirectorAudioClips(project.root, segment.script)
                val scriptForRun = if (request.audioOverride != null && segment.script.music != null) {
                    segment.script.copy(music = null)
                } else {
                    segment.script
                }
                val expectedFrames = ceil(scriptForRun.totalDurationSeconds() * request.fps).toInt().coerceAtLeast(1)

                val loaded = try {
                    loadProject(renderHelper.sceneAsEntry(sceneName), physicsWorld = { createJoltPhysicsWorld() })
                } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                    throw CommandFailure("$sceneName can't be played: ${error.message}", error)
                }

                loaded.use {
                    val override = DirectorRuntimeOverride(
                        script = scriptForRun,
                        inputTake = segment.inputTake,
                        audioPlayer = mixer,
                        extraAudioClips = clips,
                        aspectRatioProvider = { aspect },
                        loop = false,
                    )
                    val game = app { scene("capture") { runProject(loaded, directorOverride = override) } }
                    renderHelper.openSession(request.width, request.height, request.backend, game).use { session ->
                        try {
                            game.ready(session.renderer)
                            val runtime = game.requireService<SceneAppLifecycleRuntime>()
                            request.hudOverride?.let { runtime.drawsSceneCanvas = it }

                            for (frameIdx in 0 until expectedFrames) {
                                request.hudOverride?.let { runtime.drawsSceneCanvas = it }
                                game.update(step, request.width.toFloat(), request.height.toFloat())

                                val dirSystem = override.activeDirectorSystem
                                val pitchScale = dirSystem?.activePlayer?.currentAudioPitchScale ?: 1f
                                mixer.advance(step, sfxPitchScale = pitchScale)

                                val rgba = renderHelper.readbackRgba(runtime, request.width, request.height, sceneName)
                                val pose = dirSystem?.lastPose
                                if (pose != null) {
                                    applyFrameOverlays(rgba, request.width, request.height, pose.fadeBlack, pose.letterboxAspect)
                                }
                                luminanceSum += measureAverageLuminance(rgba, request.width, request.height).toDouble()
                                totalFrames++

                                videoStdin?.write(rgba)
                                framesDir?.let { dir ->
                                    val pngFile = dir.resolve("frame_${totalFrames.toString().padStart(FRAME_PAD, '0')}.png")
                                    RenderedImage(request.width, request.height, rgba).writePng(pngFile)
                                }
                            }
                        } finally {
                            session.renderer.waitIdle()
                            game.dispose()
                        }
                    }
                }
            }

            // Close raw video stdin and check ffmpeg encoding exit status.
            videoStdin?.flush()
            videoStdin?.close()
            videoStdin = null
            videoProcess?.let { proc ->
                val log = proc.inputStream.bufferedReader().readText()
                val exit = proc.waitFor()
                if (exit != 0) throw CommandFailure("ffmpeg failed (exit $exit): $log")
            }

            val peakAudio = mixer.peakAmplitude()
            val hasAudio = peakAudio > AUDIO_SILENCE_EPSILON
            val wavFile = if (hasAudio) {
                val target = when (request.format) {
                    CaptureFormat.PngSequence -> request.output.resolve("audio.wav")
                    else -> tempDir.resolve("audio.wav")
                }
                target.writeBytes(mixer.encodeWav())
                target
            } else {
                null
            }

            finalizeOutput(request, rawVideoTarget, framesDir, wavFile, tempDir, encoderLabel)

            val savedWavPath = if (request.format == CaptureFormat.PngSequence && wavFile != null) wavFile.path else null
            val keptDirPath = if (request.keepFrames && request.format != CaptureFormat.PngSequence) framesDir?.path else null
            TrailerCaptureReport(
                output = request.output.path,
                format = request.format.label,
                encoder = encoderLabel,
                width = request.width,
                height = request.height,
                fps = request.fps,
                framesCaptured = totalFrames,
                durationSeconds = totalFrames * step,
                averageLuminance = if (totalFrames > 0) (luminanceSum / totalFrames).toFloat() else 0f,
                audioPeakAmplitude = peakAudio,
                audioWav = savedWavPath,
                keptFramesDir = keptDirPath,
            )
        } finally {
            runCatching { videoStdin?.close() }
            runCatching { videoProcess?.destroyForcibly() }
            tempDir.deleteRecursively()
        }
    }

    private fun finalizeOutput(
        request: TrailerCaptureRequest,
        rawVideoTarget: File?,
        framesDir: File?,
        wavFile: File?,
        tempDir: File,
        encoderLabel: String,
    ) {
        when (request.format) {
            CaptureFormat.PngSequence -> Unit
            CaptureFormat.Mp4, CaptureFormat.Webm -> finalizeVideoOutput(request, rawVideoTarget, wavFile)
            CaptureFormat.Gif -> finalizeGifOutput(request, framesDir, tempDir, encoderLabel)
        }
    }

    private fun finalizeVideoOutput(
        request: TrailerCaptureRequest,
        rawVideoTarget: File?,
        wavFile: File?,
    ) {
        val source = rawVideoTarget ?: throw CommandFailure("internal capture error: missing encoded video")
        request.output.absoluteFile.parentFile?.mkdirs()
        if (wavFile != null) {
            val ffmpeg = findExecutable("ffmpeg") ?: throw CommandFailure("ffmpeg not found on PATH")
            val audioCodec = if (request.format == CaptureFormat.Webm) listOf("libopus", "-b:a", "160k") else listOf("aac", "-b:a", "192k")
            val muxCmd = listOf(
                ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
                "-i", source.absolutePath,
                "-i", wavFile.absolutePath,
                "-c:v", "copy",
                "-c:a",
            ) + audioCodec + listOf("-shortest", request.output.absolutePath)
            runProcessOrThrow(muxCmd, "ffmpeg audio mux")
        } else {
            source.copyTo(request.output, overwrite = true)
        }
    }

    private fun finalizeGifOutput(
        request: TrailerCaptureRequest,
        framesDir: File?,
        tempDir: File,
        encoderLabel: String,
    ) {
        val dir = framesDir ?: throw CommandFailure("internal capture error: missing frames directory")
        request.output.absoluteFile.parentFile?.mkdirs()
        if (encoderLabel == "gifski") {
            val gifski = requireExecutable("gifski", "gifski not found on PATH")
            val pngs = dir.listFiles { f -> f.extension == "png" }?.sortedBy(File::getName).orEmpty()
            val cmd = listOf(
                gifski,
                "--fps", request.fps.toString(),
                "--width", request.width.toString(),
                "--quality", "90",
                "-o", request.output.absolutePath,
            ) + pngs.map(File::getAbsolutePath)
            runProcessOrThrow(cmd, "gifski")
        } else {
            val ffmpeg = requireExecutable("ffmpeg", "neither gifski nor ffmpeg found on PATH for GIF export")
            val pattern = dir.resolve("frame_%06d.png").absolutePath
            val palette = tempDir.resolve("palette.png")
            runProcessOrThrow(
                listOf(
                    ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
                    "-framerate", request.fps.toString(),
                    "-i", pattern,
                    "-vf", "palettegen=stats_mode=diff",
                    palette.absolutePath,
                ),
                "ffmpeg palettegen",
            )
            runProcessOrThrow(
                listOf(
                    ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
                    "-framerate", request.fps.toString(),
                    "-i", pattern,
                    "-i", palette.absolutePath,
                    "-lavfi", "paletteuse=dither=sierra2_4a",
                    request.output.absolutePath,
                ),
                "ffmpeg paletteuse",
            )
        }
    }

    private fun requireExecutable(name: String, missingMessage: String): String =
        findExecutable(name) ?: throw CommandFailure(missingMessage)

    private fun buildRawVideoFfmpegCommand(
        ffmpeg: String,
        request: TrailerCaptureRequest,
        target: File,
    ): List<String> {
        val base = listOf(
            ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
            "-f", "rawvideo",
            "-pix_fmt", "rgba",
            "-s", "${request.width}x${request.height}",
            "-r", request.fps.toString(),
            "-i", "pipe:0",
        )
        val codec = when (request.format) {
            CaptureFormat.Webm -> listOf("-c:v", "libvpx-vp9", "-crf", "24", "-b:v", "0", "-pix_fmt", "yuv420p")
            else -> listOf("-c:v", "libx264", "-crf", "18", "-preset", "medium", "-pix_fmt", "yuv420p", "-movflags", "+faststart")
        }
        return base + codec + listOf(target.absolutePath)
    }

    private fun runProcessOrThrow(command: List<String>, label: String) {
        val proc = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        if (exit != 0) throw CommandFailure("$label failed (exit $exit): $output")
    }

    private companion object {
        const val AUDIO_SAMPLE_RATE = 48_000
        const val AUDIO_SILENCE_EPSILON = 1e-5f
        const val FRAME_PAD = 6
    }
}

/** Loads all uncompressed WAV clips referenced by [script] under [projectRoot]. */
internal fun loadDirectorAudioClips(projectRoot: File, script: DirectorScript): Map<String, AudioClip> {
    val paths = LinkedHashSet<String>()
    script.music?.path?.let(paths::add)
    script.audioTrack.filterIsInstance<AudioCueSpec.PlaySfx>().forEach { paths.add(it.path) }
    val loaded = LinkedHashMap<String, AudioClip>()
    for (path in paths) {
        val file = projectRoot.resolve(path)
        if (!file.isFile) continue
        WavDecoder.decode(path, path, file.readBytes())?.let { loaded[path] = it }
    }
    return loaded
}

/** Loads an optional recorded [InputTake] from `--take` or [script.inputTake]. */
internal fun loadDirectorInputTake(projectRoot: File, workingDir: File, script: DirectorScript, explicitTake: String?): InputTake? {
    val file = when {
        explicitTake != null -> workingDir.resolve(explicitTake).takeIf(File::isFile) ?: projectRoot.resolve(explicitTake)
        script.inputTake != null -> projectRoot.resolve(script.inputTake!!)
        else -> return null
    }
    if (!file.isFile) throw CommandFailure("input take file not found: ${file.path}")
    return try {
        InputTakeJson.decode(file.readText())
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        throw CommandFailure("could not decode input take ${file.path}: ${error.message}", error)
    }
}

/** Resolves a trailer [TrailerReel] into playable [CaptureSegment]s, expanding any reel-wide soundtrack. */
internal fun resolveReelSegments(project: ProjectScenes, workingDir: File, reelFile: File): List<CaptureSegment> {
    val reel = try {
        TrailerReel.decode(reelFile.readText())
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        throw CommandFailure("could not decode trailer reel ${reelFile.path}: ${error.message}", error)
    }
    val issues = reel.validate()
    if (issues.isNotEmpty()) {
        throw CommandFailure("invalid trailer reel ${reelFile.path}: ${issues.joinToString { "${it.path}: ${it.message}" }}")
    }
    return reel.segments.mapIndexed { index, seg ->
        val sceneFile = project.resolve(seg.scene)
        val rawScript = resolveReelSegmentScript(project.root, workingDir, index, seg.inlineDirector, seg.directorPath)
        // Attach reel-level continuous music to the first segment if it doesn't declare its own.
        val script = if (index == 0 && reel.music != null && rawScript.music == null) {
            rawScript.copy(music = reel.music)
        } else {
            rawScript
        }
        val take = loadDirectorInputTake(project.root, workingDir, script, seg.inputTake)
        CaptureSegment(sceneFile, script, take)
    }
}

private fun resolveReelSegmentScript(
    projectRoot: File,
    workingDir: File,
    index: Int,
    inlineDirector: DirectorScript?,
    directorPath: String?,
): DirectorScript {
    if (inlineDirector != null) return inlineDirector
    val path = directorPath ?: throw CommandFailure("segment $index has neither directorPath nor inlineDirector")
    val file = projectRoot.resolve(path).takeIf(File::isFile) ?: workingDir.resolve(path)
    if (!file.isFile) throw CommandFailure("segment $index script not found: $path")
    return DirectorScript.decode(file.readText())
}

/** Applies fade-to-black and optional widescreen letterbox bars to an RGBA buffer in place. */
internal fun applyFrameOverlays(
    rgba: ByteArray,
    width: Int,
    height: Int,
    fadeBlack: Float,
    letterboxAspect: Float?,
) {
    val fade = fadeBlack.coerceIn(0f, 1f)
    if (fade > OVERLAY_EPSILON) {
        val gain = 1f - fade
        var idx = 0
        val total = width * height * RGBA_CHANNELS
        while (idx < total) {
            rgba[idx] = ((rgba[idx].toInt() and BYTE_MASK) * gain + HALF).toInt().toByte()
            rgba[idx + 1] = ((rgba[idx + 1].toInt() and BYTE_MASK) * gain + HALF).toInt().toByte()
            rgba[idx + 2] = ((rgba[idx + 2].toInt() and BYTE_MASK) * gain + HALF).toInt().toByte()
            idx += RGBA_CHANNELS
        }
    }
    if (letterboxAspect != null && letterboxAspect > 0f && height > 0) {
        val currentAspect = width.toFloat() / height.toFloat()
        if (letterboxAspect > currentAspect + OVERLAY_EPSILON) {
            val activeHeight = (width / letterboxAspect).roundToInt().coerceIn(0, height)
            val barHeight = ((height - activeHeight) / 2).coerceAtLeast(0)
            for (y in 0 until barHeight) {
                zeroRgbRow(rgba, width, y)
                zeroRgbRow(rgba, width, height - 1 - y)
            }
        }
    }
}

private fun zeroRgbRow(rgba: ByteArray, width: Int, y: Int) {
    val rowStart = y * width * RGBA_CHANNELS
    for (x in 0 until width) {
        val p = rowStart + x * RGBA_CHANNELS
        rgba[p] = 0
        rgba[p + 1] = 0
        rgba[p + 2] = 0
        rgba[p + 3] = FULL_ALPHA
    }
}

/** Measures mean Rec.709 perceived brightness in `0..1` over a tightly packed RGBA image. */
internal fun measureAverageLuminance(rgba: ByteArray, width: Int, height: Int): Float {
    val pixels = width * height
    if (pixels == 0) return 0f
    var sum = 0.0
    var idx = 0
    val total = pixels * RGBA_CHANNELS
    while (idx < total) {
        val r = (rgba[idx].toInt() and BYTE_MASK) / MAX_BYTE_D
        val g = (rgba[idx + 1].toInt() and BYTE_MASK) / MAX_BYTE_D
        val b = (rgba[idx + 2].toInt() and BYTE_MASK) / MAX_BYTE_D
        sum += REC709_R * r + REC709_G * g + REC709_B * b
        idx += RGBA_CHANNELS
    }
    return (sum / pixels).toFloat().coerceIn(0f, 1f)
}

/** Searches the system PATH for an executable named [name] (checking `.exe` on Windows). */
internal fun findExecutable(name: String): String? {
    val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    val candidates = if (isWindows) listOf("$name.exe", "$name.cmd", "$name.bat", name) else listOf(name)
    val pathEnv = System.getenv("PATH").orEmpty()
    for (dir in pathEnv.split(File.pathSeparatorChar)) {
        if (dir.isBlank()) continue
        for (candidate in candidates) {
            val file = File(dir, candidate)
            if (file.isFile && file.canExecute()) return file.absolutePath
        }
    }
    return null
}

private fun shotFraming(shot: ShotSpec): FramingSpec? = when (val rig = shot.rig) {
    is ShotRigSpec.Static -> rig.framing
    is ShotRigSpec.Orbit -> rig.framing
    is ShotRigSpec.Dolly -> rig.framing
    is ShotRigSpec.Crane -> rig.framing
    is ShotRigSpec.SplineRail -> rig.framing
    is ShotRigSpec.ChaseFollow -> rig.framing
    is ShotRigSpec.DollyZoom -> rig.framing
    is ShotRigSpec.WhipPan -> rig.framing
    is ShotRigSpec.NamedCamera -> null
}

private fun formatTenths(seconds: Float): String {
    val tenths = (seconds * TEN).roundToInt()
    return "${tenths / TEN}.${tenths % TEN}"
}

private const val RGBA_CHANNELS = 4
private const val BYTE_MASK = 0xFF
private const val FULL_ALPHA: Byte = (-1).toByte()
private const val HALF = 0.5f
private const val OVERLAY_EPSILON = 1e-4f
private const val MAX_BYTE_D = 255.0
private const val REC709_R = 0.2126
private const val REC709_G = 0.7152
private const val REC709_B = 0.0722
private const val TEN = 10
