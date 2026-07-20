package com.example.prioritize.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels

object AudioDecoder {
    private const val TAG = "AudioDecoder"
    private const val TARGET_SAMPLE_RATE = 16000

    fun decodeToPcm(audioPath: String): ByteArray {
        val file = File(audioPath)
        if (!file.exists()) {
            throw IllegalArgumentException("Audio file does not exist: $audioPath")
        }
        
        // If it's a WAV file, we read and resample/mono-mix it directly using streaming
        if (audioPath.endsWith(".wav", ignoreCase = true)) {
            try {
                FileInputStream(file).use { fis ->
                    val headerBytes = ByteArray(44)
                    if (fis.read(headerBytes) == 44 && String(headerBytes.sliceArray(0..3)) == "RIFF") {
                        val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val channels = header.getShort(22).toInt()
                        val sampleRate = header.getInt(24)
                        val bitDepth = header.getShort(34).toInt()
                        var dataSize = header.getInt(40)
                        
                        val fileLength = file.length()
                        if (dataSize <= 0 || dataSize > fileLength - 44) {
                            dataSize = (fileLength - 44).toInt()
                        }
                        
                        var samples = readWavSamples(file, dataSize, bitDepth)
                        
                        if (channels == 2) {
                            samples = downmixToMono(samples)
                        }
                        if (sampleRate != TARGET_SAMPLE_RATE) {
                            samples = resample(samples, sampleRate, TARGET_SAMPLE_RATE)
                        }
                        
                        val outputBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        outputBuffer.asShortBuffer().put(samples)
                        return outputBuffer.array()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Custom WAV decoding failed, falling back to MediaExtractor", e)
            }
        }

        // For M4A / MP3 and other codec formats
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val outputStream = ByteArrayOutputStream()
        val channel = Channels.newChannel(outputStream)

        try {
            extractor.setDataSource(audioPath)
            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            
            for (i in 0 until extractor.trackCount) {
                val trFormat = extractor.getTrackFormat(i)
                val mime = trFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    inputFormat = trFormat
                    break
                }
            }

            if (trackIndex < 0 || inputFormat == null) {
                throw IllegalArgumentException("No audio track found in file: $audioPath")
            }

            extractor.selectTrack(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val activeCodec = MediaCodec.createDecoderByType(mime)
            codec = activeCodec
            
            activeCodec.configure(inputFormat, null, null, 0)
            activeCodec.start()

            val info = MediaCodec.BufferInfo()
            var isInputDone = false
            var isOutputDone = false
            var outputFormat: MediaFormat? = null
            var isFloat32 = false

            while (!isOutputDone) {
                if (!isInputDone) {
                    val inputBufferIndex = activeCodec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = activeCodec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                activeCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputDone = true
                            } else {
                                activeCodec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = activeCodec.dequeueOutputBuffer(info, 10000)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = activeCodec.outputFormat
                        outputFormat = format
                        val pcmEncoding = format.getIntegerOrDefault(MediaFormat.KEY_PCM_ENCODING, 2)
                        isFloat32 = pcmEncoding == 4
                        Log.d(TAG, "Output format changed: $outputFormat, isFloat32=$isFloat32")
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = activeCodec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && info.size > 0) {
                            outputBuffer.position(info.offset)
                            if (isFloat32) {
                                val floatCount = info.size / 4
                                val floatBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                                val tempShorts = ShortArray(floatCount)
                                for (i in 0 until floatCount) {
                                    val f = floatBuffer.get().coerceIn(-1f, 1f)
                                    tempShorts[i] = (f * 32767f).toInt().toShort()
                                }
                                val tempBytes = ByteBuffer.allocate(floatCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                                tempBytes.asShortBuffer().put(tempShorts)
                                outputStream.write(tempBytes.array())
                            } else {
                                outputBuffer.limit(info.offset + info.size)
                                channel.write(outputBuffer)
                            }
                        }
                        activeCodec.releaseOutputBuffer(outputBufferIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isOutputDone = true
                        }
                    }
                }
            }

            val rawBytes = outputStream.toByteArray()
            if (rawBytes.isEmpty()) {
                throw IllegalStateException("Decoded PCM data stream is empty: $audioPath")
            }

            val shortBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            var samples = ShortArray(shortBuf.remaining())
            shortBuf.get(samples)
            
            if (channels == 2) {
                samples = downmixToMono(samples)
            }
            if (sampleRate != TARGET_SAMPLE_RATE) {
                samples = resample(samples, sampleRate, TARGET_SAMPLE_RATE)
            }
            
            val outputBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            outputBuffer.asShortBuffer().put(samples)
            return outputBuffer.array()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode audio file $audioPath", e)
            throw e
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {}
            try {
                extractor.release()
            } catch (e: Exception) {}
        }
    }

    private fun readWavSamples(file: File, dataSize: Int, bitDepth: Int): ShortArray {
        val bytesPerSample = bitDepth / 8
        val totalSamples = dataSize / bytesPerSample
        val samples = ShortArray(totalSamples)
        
        FileInputStream(file).use { fis ->
            fis.skip(44)
            val bis = BufferedInputStream(fis)
            val tempBuf = ByteArray(4096)
            var sampleIndex = 0
            
            if (bitDepth == 8) {
                var read: Int
                while (bis.read(tempBuf).also { read = it } != -1 && sampleIndex < totalSamples) {
                    for (i in 0 until read) {
                        if (sampleIndex >= totalSamples) break
                        val unsignedByte = tempBuf[i].toInt() and 0xFF
                        samples[sampleIndex++] = ((unsignedByte - 128) * 256).toShort()
                    }
                }
            } else if (bitDepth == 16) {
                var read: Int
                var byteAccumulator = 0
                var hasByte = false
                while (bis.read(tempBuf).also { read = it } != -1 && sampleIndex < totalSamples) {
                    var i = 0
                    while (i < read && sampleIndex < totalSamples) {
                        if (hasByte) {
                            val b1 = byteAccumulator
                            val b2 = tempBuf[i].toInt() and 0xFF
                            val sample = ((b2 shl 8) or b1).toShort()
                            samples[sampleIndex++] = sample
                            hasByte = false
                            i++
                        } else {
                            if (i + 1 < read) {
                                val b1 = tempBuf[i].toInt() and 0xFF
                                val b2 = tempBuf[i + 1].toInt() and 0xFF
                                val sample = ((b2 shl 8) or b1).toShort()
                                samples[sampleIndex++] = sample
                                i += 2
                            } else {
                                byteAccumulator = tempBuf[i].toInt() and 0xFF
                                hasByte = true
                                i++
                            }
                        }
                    }
                }
            } else {
                throw IllegalArgumentException("Unsupported bit depth: $bitDepth")
            }
        }
        return samples
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int {
        return try {
            getInteger(key)
        } catch (e: Exception) {
            default
        }
    }

    private fun downmixToMono(stereoSamples: ShortArray): ShortArray {
        val mono = ShortArray(stereoSamples.size / 2)
        for (i in mono.indices) {
            val left = stereoSamples[i * 2]
            val right = stereoSamples[i * 2 + 1]
            mono[i] = ((left.toInt() + right.toInt()) / 2).toShort()
        }
        return mono
    }

    private fun resample(
        inputSamples: ShortArray,
        originalSampleRate: Int,
        targetSampleRate: Int
    ): ShortArray {
        val ratio = targetSampleRate.toFloat() / originalSampleRate.toFloat()
        val invRatio = originalSampleRate.toFloat() / targetSampleRate.toFloat()
        val outputLength = (inputSamples.size * ratio).toInt()
        val resampledData = ShortArray(outputLength)
        
        for (i in resampledData.indices) {
            val position = i * invRatio
            val index1 = position.toInt()
            val index2 = if (index1 + 1 < inputSamples.size) index1 + 1 else index1
            val fraction = position - index1
            
            val sample1 = inputSamples[index1].toFloat()
            val sample2 = inputSamples[index2].toFloat()
            resampledData[i] = (sample1 + fraction * (sample2 - sample1)).toInt().toShort()
        }
        return resampledData
    }
}
