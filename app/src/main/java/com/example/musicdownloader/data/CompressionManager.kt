package com.example.musicdownloader.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.musicdownloader.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

enum class CompressionQuality(val displayName: String, val bitrateVal: String, val approxBitrateKbps: Int) {
    MAX_COMPRESSION("Max Compression (Space Saver)", "48K", 48), // ~48kbps
    BALANCED("Balanced", "96K", 96),       // ~96kbps
    HIGH_QUALITY("High Quality", "128K", 128); // ~128kbps
}

object CompressionManager {

    /**
     * Compresses the given song file to the target bitrate using native Android APIs.
     * Returns the new File if successful, or null if failed.
     * Note: This does NOT update the database; the caller must do that.
     */
    suspend fun compressSong(
        context: Context,
        song: Song,
        quality: CompressionQuality
    ): File? = withContext(Dispatchers.IO) {
        val inputFile = File(song.filePath)
        if (!inputFile.exists()) {
            AppLogger.log("[Compression] Input file not found: ${song.filePath}")
            return@withContext null
        }

        // Output file: Use a temp name first
        val tempFileName = "${song.id}_compressed_${System.currentTimeMillis()}.m4a"
        val outputDir = inputFile.parentFile ?: context.filesDir
        val tempFile = File(outputDir, tempFileName)

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxerStarted = false
        var pendingDecodedBufferIndex = -1

        try {
            AppLogger.log("[Compression] Starting native compression for ${song.title} to ${quality.approxBitrateKbps}kbps")

            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    trackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (trackIndex < 0 || inputFormat == null) {
                AppLogger.log("[Compression] No audio track found.")
                return@withContext null
            }

            extractor.selectTrack(trackIndex)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // Initialize Muxer
            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1

            // State variables
            var inputDone = false // Extractor -> Decoder
            var decoderDone = false // Decoder -> Encoder (EOS sent to Encoder)
            var outputDone = false // Encoder -> Muxer (EOS received from Encoder)

            val pendingBufferInfo = MediaCodec.BufferInfo()
            val muxerBufferInfo = MediaCodec.BufferInfo() // Separate buffer info for muxer/encoder output

            val timeoutUs = 5000L // 5ms timeout

            while (!outputDone) {
                var activity = false

                // 1. Feed Decoder (if not done)
                if (!inputDone) {
                    val idx = decoder.dequeueInputBuffer(timeoutUs)
                    if (idx >= 0) {
                        val buffer = decoder.getInputBuffer(idx)!!
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(idx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        activity = true
                    }
                }

                // 2. Drain Decoder (Prepare data for encoder)
                // We only dequeue if we don't have a pending buffer
                if (pendingDecodedBufferIndex == -1 && !decoderDone) {
                    val idx = decoder.dequeueOutputBuffer(pendingBufferInfo, timeoutUs)
                    if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // Decoder format known. Configure Encoder.
                        val decFormat = decoder.getOutputFormat()
                        val sampleRate = decFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channelCount = if (decFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                            decFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

                        AppLogger.log("[Compression] Decoder format changed. Configuring encoder: ${sampleRate}Hz, $channelCount channels")

                        val bitrate = quality.approxBitrateKbps * 1000
                        val outputFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
                        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                        outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        outputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)

                        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                        encoder!!.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        encoder!!.start()
                        activity = true
                    } else if (idx >= 0) {
                        pendingDecodedBufferIndex = idx
                        // If this buffer is EOS, we will pass it to encoder
                        // If it's pure EOS (size 0), we still pass it.
                        activity = true
                    }
                }

                // 3. Feed Encoder (from pending buffer)
                if (pendingDecodedBufferIndex >= 0 && encoder != null) {
                    val idx = encoder!!.dequeueInputBuffer(timeoutUs)
                    if (idx >= 0) {
                        val encoderInputBuffer = encoder!!.getInputBuffer(idx)!!
                        val decoderOutputBuffer = decoder.getOutputBuffer(pendingDecodedBufferIndex)!!

                        // Check EOS on pending buffer
                        val isEos = (pendingBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        if (pendingBufferInfo.size > 0) {
                            decoderOutputBuffer.position(pendingBufferInfo.offset)
                            decoderOutputBuffer.limit(pendingBufferInfo.offset + pendingBufferInfo.size)
                            encoderInputBuffer.put(decoderOutputBuffer)
                        }

                        encoder!!.queueInputBuffer(
                            idx,
                            0,
                            pendingBufferInfo.size,
                            pendingBufferInfo.presentationTimeUs,
                            if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        )

                        decoder.releaseOutputBuffer(pendingDecodedBufferIndex, false)
                        pendingDecodedBufferIndex = -1

                        if (isEos) {
                            decoderDone = true
                        }
                        activity = true
                    }
                }

                // 4. Drain Encoder -> Muxer
                if (encoder != null) {
                    val idx = encoder!!.dequeueOutputBuffer(muxerBufferInfo, timeoutUs)
                    if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) throw RuntimeException("Muxer format changed twice")
                        val newFormat = encoder!!.getOutputFormat()
                        muxerTrackIndex = muxer!!.addTrack(newFormat)
                        muxer!!.start()
                        muxerStarted = true
                        activity = true
                    } else if (idx >= 0) {
                        val encodedData = encoder!!.getOutputBuffer(idx)!!

                        if ((muxerBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            muxerBufferInfo.size = 0
                        }

                        if (muxerBufferInfo.size != 0) {
                            if (!muxerStarted) {
                                // Should not happen for AAC if INFO_OUTPUT_FORMAT_CHANGED fired first
                                // But if it happens, we can't write.
                                throw RuntimeException("Muxer not started before data available")
                            }
                            encodedData.position(muxerBufferInfo.offset)
                            encodedData.limit(muxerBufferInfo.offset + muxerBufferInfo.size)
                            muxer!!.writeSampleData(muxerTrackIndex, encodedData, muxerBufferInfo)
                        }

                        encoder!!.releaseOutputBuffer(idx, false)

                        if ((muxerBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                        activity = true
                    }
                }

                // If no activity in any codec, small sleep to avoid tight loop
                if (!activity) {
                     // We rely on timeoutUs in dequeue calls to prevent CPU spinning,
                     // but if all return TRY_AGAIN quickly (or we are in a state waiting for something),
                     // we might spin.
                     // Since we used non-zero timeoutUs (5ms), if they return immediately, it means buffers are available or empty.
                     // If they wait, they consume time.
                     // However, dequeueInputBuffer waits. dequeueOutputBuffer waits.
                     // If we call multiple dequeues, the wait sums up.
                     // Actually, if we just loop, it's fine.
                }
            }

            AppLogger.log("[Compression] Success. New size: ${tempFile.length()} vs Old: ${inputFile.length()}")
            return@withContext tempFile

        } catch (e: Exception) {
            AppLogger.log("[Compression] Error: ${e.message}")
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
            return@withContext null
        } finally {
            try {
                if (pendingDecodedBufferIndex >= 0) {
                    decoder?.releaseOutputBuffer(pendingDecodedBufferIndex, false)
                }
                decoder?.stop()
                decoder?.release()
                encoder?.stop()
                encoder?.release()
                extractor?.release()
                if (muxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
            } catch (ex: Exception) {
                AppLogger.log("[Compression] Cleanup Error: ${ex.message}")
            }
        }
    }

    /**
     * Helper to estimate new size in bytes
     * (Bitrate kbps * 1000 / 8) * duration_seconds
     */
    fun estimateSize(song: Song, quality: CompressionQuality): Long {
        val durationSecs = parseDurationInSeconds(song.duration)
        if (durationSecs == 0L) return 0L
        val bytesPerSec = (quality.approxBitrateKbps * 1000) / 8
        return bytesPerSec * durationSecs
    }

    private fun parseDurationInSeconds(durationStr: String): Long {
        return try {
            if (durationStr.contains(":")) {
                val parts = durationStr.split(":").map { it.toLongOrNull() ?: 0L }
                when (parts.size) {
                    2 -> parts[0] * 60 + parts[1]
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    else -> 0L
                }
            } else {
                durationStr.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
