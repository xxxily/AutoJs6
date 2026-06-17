package com.benjaminwan.ocrlibrary

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class OcrEngine(context: Context) {
    companion object {
        const val numThread: Int = 4
        private val nativeLock = ReentrantLock()
    }

    init {
        System.loadLibrary("RapidOcr")
        val ret = nativeLock.withLock {
            init(
                context.assets, numThread,
                "models/ch_PP-OCRv3_det_infer.onnx",
                "models/ch_ppocr_mobile_v2.0_cls_infer.onnx",
                "models/ch_PP-OCRv3_rec_infer.onnx",
                "models/ppocr_keys_v1.txt",
            )
        }
        if (!ret) throw IllegalStateException("RapidOCR native engine failed to initialize. Check bundled model assets.")
    }

    var padding: Int = 50
    var boxScoreThresh: Float = 0.5f
    var boxThresh: Float = 0.3f
    var unClipRatio: Float = 1.6f
    var doAngle: Boolean = true
    var mostAngle: Boolean = true

    fun detect(input: Bitmap, output: Bitmap, maxSideLen: Int) =
        nativeLock.withLock {
            detect(
                input, output, padding, maxSideLen,
                boxScoreThresh, boxThresh,
                unClipRatio, doAngle, mostAngle
            )
        }

    external fun init(
        assetManager: AssetManager,
        numThread: Int, detName: String,
        clsName: String, recName: String, keysName: String
    ): Boolean

    external fun detect(
        input: Bitmap, output: Bitmap, padding: Int, maxSideLen: Int,
        boxScoreThresh: Float, boxThresh: Float,
        unClipRatio: Float, doAngle: Boolean, mostAngle: Boolean
    ): OcrResult

    external fun benchmark(input: Bitmap, loop: Int): Double

    fun benchmarkLocked(input: Bitmap, loop: Int): Double = nativeLock.withLock { benchmark(input, loop) }

}
