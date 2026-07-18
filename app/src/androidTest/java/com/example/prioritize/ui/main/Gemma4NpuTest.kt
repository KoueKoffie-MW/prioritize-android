package com.example.prioritize.ui.main

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gemma4NpuTest {

    @Test
    fun testNpuInitialization() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filename = "gemma-4-E2B-it_Google_Tensor_G5.litertlm"
        
        // Find model file in external files dir
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val modelFile = File(dir, filename)
        
        Log.i("Gemma4NpuTest", "Checking model file at: ${modelFile.absolutePath}")
        assertTrue("Model file does not exist", modelFile.exists())

        // Load public edgetpu driver
        try {
            System.loadLibrary("edgetpu_litert")
            Log.i("Gemma4NpuTest", "Successfully loaded edgetpu_litert driver")
        } catch (e: Throwable) {
            Log.w("Gemma4NpuTest", "Failed to load edgetpu_litert library: ${e.message}")
        }

        // Try NPU candidates
        val candidates = listOf(
            context.applicationInfo.nativeLibraryDir,
            "/vendor/lib64",
            "/system/lib64"
        )

        for (candidate in candidates) {
            Log.i("Gemma4NpuTest", "Attempting NPU initialization with nativeLibraryDir: $candidate")
            try {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.NPU(nativeLibraryDir = candidate)
                )
                val engine = Engine(config)
                engine.initialize()
                Log.i("Gemma4NpuTest", "NPU initialization SUCCESS with candidate: $candidate")
                engine.close()
                return // Success!
            } catch (e: Exception) {
                Log.e("Gemma4NpuTest", "NPU initialization failed for candidate $candidate", e)
            }
        }

        throw RuntimeException("All NPU candidates failed to initialize!")
    }
}
