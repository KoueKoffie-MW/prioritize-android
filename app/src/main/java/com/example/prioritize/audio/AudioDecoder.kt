package com.example.prioritize.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {
    private const val TAG = "AudioDecoder"
    private const val TARGET_SAMPLE_RATE = 16000

    fun decodeToPcm(audioPath: String): ByteArray {
        val file = File(audioPath)
        if (!file.exists()) {
            throw IllegalArgumentException("Audio file does not exist: $audioPath")
        }
        
        // If it's a WAV file, we read and resample/mono-mix it directly
        if (audioPath.endsWith(".wav", ignoreCase = true)) {
            try {
                val bytes = file.readBytes()
                if (bytes.size > 44 && String(bytes.sliceArray(0..3)) == "RIFF") {
                    val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
                    val channels = header.getShort(22).toInt()
                    val sampleRate = header.getInt(24)
                    val bitDepth = header.getShort(34).toInt()
                    
                    var pcmData = bytes.sliceArray(44 until bytes.size)
                    if (bitDepth == 8) {
                        pcmData = convert8BitTo16Bit(pcmData)
                    }
                    
                    val shortBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    var samples = ShortArray(shortBuffer.remaining())
                    shortBuffer.get(samples)
                    
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
            } catch (e: Exception) {
                Log.w(TAG, "Custom WAV decoding failed, falling back to MediaExtractor", e)
            }
        }

        // For M4A / MP3 and other codec formats
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val outputStream = ByteArrayOutputStream()

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
            // Track output format once codec signals it (may differ from input format)
            var outputFormat: MediaFormat? = null

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
                        outputFormat = activeCodec.outputFormat
                        Log.d(TAG, "Output format changed: $outputFormat")
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = activeCodec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && info.size > 0) {
                            val chunk = ByteArray(info.size)
                            outputBuffer.position(info.offset)
                            outputBuffer.get(chunk)
                            outputStream.write(chunk)
                        }
                        activeCodec.releaseOutputBuffer(outputBufferIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isOutputDone = true
                        }
                    }
                }
            }

            // Detect PCM encoding from the output format - Android can output float32 for AAC/M4A
            val pcmEncoding = outputFormat?.getIntegerOrDefault(MediaFormat.KEY_PCM_ENCODING, 2) ?: 2
            // pcmEncoding 2 = AudioFormat.ENCODING_PCM_16BIT, 4 = AudioFormat.ENCODING_PCM_FLOAT
            val isFloat32 = pcmEncoding == 4

            val rawBytes = outputStream.toByteArray()
            if (rawBytes.isEmpty()) {
                throw IllegalStateException("Decoded PCM data stream is empty: $audioPath")
            }

            var samples: ShortArray
            if (isFloat32) {
                // Convert float32 PCM to int16 PCM
                val floatBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                samples = ShortArray(floatBuf.remaining())
                for (i in samples.indices) {
                    val f = floatBuf.get().coerceIn(-1f, 1f)
                    samples[i] = (f * Short.MAX_VALUE).toInt().toShort()
                }
                Log.d(TAG, "Converted float32 PCM to int16: ${samples.size} samples")
            } else {
                val shortBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                samples = ShortArray(shortBuf.remaining())
                shortBuf.get(samples)
            }
            
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

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int {
        return try {
            getInteger(key)
        } catch (e: Exception) {
            default
        }
    }

    private fun convert8BitTo16Bit(eightBitData: ByteArray): ByteArray {
        val sixteenBitData = ByteArray(eightBitData.size * 2)
        val buffer = ByteBuffer.wrap(sixteenBitData).order(ByteOrder.LITTLE_ENDIAN)
        for (byte in eightBitData) {
            val unsignedByte = byte.toInt() and 0xFF
            val sixteenBitSample = ((unsignedByte - 128) * 256).toShort()
            buffer.putShort(sixteenBitSample)
        }
        return sixteenBitData
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
        val ratio = targetSampleRate.toDouble() / originalSampleRate
        val outputLength = (inputSamples.size * ratio).toInt()
        val resampledData = ShortArray(outputLength)
        
        for (i in resampledData.indices) {
            val position = i / ratio
            val index1 = position.toInt()
            val index2 = if (index1 + 1 < inputSamples.size) index1 + 1 else index1
            val fraction = position - index1
            
            val sample1 = inputSamples[index1].toDouble()
            val sample2 = inputSamples[index2].toDouble()
            resampledData[i] = (sample1 + fraction * (sample2 - sample1)).toInt().toShort()
        }
        return resampledData
    }
}
