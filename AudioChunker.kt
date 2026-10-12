package com.ultraplayer.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Decodes the audio track of any video (whatever codec the device decoder supports),
 * downmixes to mono 16 kHz and cuts it into WAV chunks so each file stays under API upload limits.
 */
object AudioChunker {
    private const val RATE = 16000

    private class Wav(val file: File) {
        private val raf = RandomAccessFile(file, "rw")
        private val buf = ByteArray(8192)
        private var bp = 0
        var samples = 0L
            private set

        init {
            raf.setLength(0)
            raf.write(ByteArray(44))
        }

        fun put(s: Short) {
            buf[bp++] = (s.toInt() and 0xff).toByte()
            buf[bp++] = ((s.toInt() shr 8) and 0xff).toByte()
            samples++
            if (bp >= buf.size) flush()
        }

        private fun flush() {
            raf.write(buf, 0, bp)
            bp = 0
        }

        fun close() {
            flush()
            val dataLen = samples * 2
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt((36 + dataLen).toInt())
            h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray())
            h.putInt(16); h.putShort(1); h.putShort(1)
            h.putInt(RATE); h.putInt(RATE * 2); h.putShort(2); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(dataLen.toInt())
            raf.seek(0); raf.write(h.array()); raf.close()
        }
    }

    suspend fun process(
        ctx: Context,
        uri: Uri,
        chunkSeconds: Int,
        onProgress: (Float) -> Unit,
        onChunk: suspend (index: Int, file: File, offsetMs: Long) -> Unit,
    ) {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var cur: Wav? = null
        try {
            ex.setDataSource(ctx, uri, null)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; fmt = f; break }
            }
            if (track < 0 || fmt == null) throw IllegalStateException("Video te audio track paoya jay nai")
            ex.selectTrack(track)

            val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec = dec
            dec.configure(fmt, null, null, 0)
            dec.start()

            var srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = maxOf(1, fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            var step = srcRate.toDouble() / RATE
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            val perChunk = chunkSeconds.toLong() * RATE

            var idx = 0
            var offsetSamples = 0L
            cur = Wav(File(ctx.cacheDir, "chunk_$idx.wav"))

            var acc = 0.0
            var cnt = 0
            var nextOut = 0.0
            var n = 0L
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false

            while (!outDone) {
                currentCoroutineContext().ensureActive()

                if (!inDone) {
                    val ii = dec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val b = dec.getInputBuffer(ii)!!
                        val size = ex.readSampleData(b, 0)
                        if (size < 0) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            if (durUs > 0) onProgress((ex.sampleTime.toFloat() / durUs).coerceIn(0f, 1f))
                            dec.queueInputBuffer(ii, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }

                val o = dec.dequeueOutputBuffer(info, 10_000)
                if (o >= 0) {
                    val ob = dec.getOutputBuffer(o)
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val frames = sb.remaining() / ch
                        val w = cur!!
                        for (f in 0 until frames) {
                            var sum = 0
                            for (c in 0 until ch) sum += sb.get()
                            val v = sum.toDouble() / ch
                            acc += v; cnt++; n++
                            while (n >= nextOut) {
                                val outV = if (cnt > 0) acc / cnt else v
                                acc = 0.0; cnt = 0; nextOut += step
                                w.put(outV.toInt().coerceIn(-32768, 32767).toShort())
                            }
                        }
                    }
                    dec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true

                    val w = cur!!
                    if (!outDone && w.samples >= perChunk) {
                        w.close(); cur = null
                        onChunk(idx, w.file, offsetSamples * 1000 / RATE)
                        offsetSamples += w.samples
                        idx++
                        cur = Wav(File(ctx.cacheDir, "chunk_$idx.wav"))
                    }
                } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = dec.outputFormat
                    srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = maxOf(1, of.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    step = srcRate.toDouble() / RATE
                    nextOut = n.toDouble()
                }
            }

            val last = cur!!
            last.close(); cur = null
            if (last.samples > RATE) onChunk(idx, last.file, offsetSamples * 1000 / RATE) else last.file.delete()
            onProgress(1f)
        } finally {
            runCatching { cur?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
        }
    }
}
