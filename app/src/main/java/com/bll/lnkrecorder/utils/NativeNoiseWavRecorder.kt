package com.bll.lnkrecorder.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NativeNoiseWavRecorder(private val context: Context) {
    // 录音核心配置（16k/16bit/单声道，WAV标准配置）
    private val SAMPLE_RATE = 16000 // 采样率（Hz）
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO // 单声道
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16bit位深
    private val BIT_DEPTH = 16 // 位深（与AUDIO_FORMAT对应）
    private val CHANNEL_COUNT = 1 // 声道数（单声道=1）
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2 // 缓冲区大小（避免卡顿）

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null // 原生降噪器
    private var isRecording = false
    private var recordExecutor: ExecutorService? = null
    private var timeExecutor: ExecutorService? = null
    private var wavFile: RandomAccessFile? = null // 替换 FileOutputStream
    private var outputFile: File? = null

    // 计时相关
    private var startTimeMillis: Long = 0
    private var recordTimeListener: OnRecordTimeListener? = null

    /**
     * 计时回调接口
     */
    interface OnRecordTimeListener {
        fun onTimeUpdate(totalMillis: Long, totalSeconds: Int, timeFormat: String)
    }

    /**
     * 设置计时回调
     */
    fun setOnRecordTimeListener(listener: OnRecordTimeListener) {
        this.recordTimeListener = listener
    }

    /**
     * 开始录音（原生降噪+计时+WAV保存）
     * @param saveDir 保存目录（如 externalCacheDir/Record/）
     * @return WAV文件，null表示初始化失败
     */
    fun startRecording(saveDir: String): File? {
        // 1. 检查录音权限
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("请先授予录音权限")
        }

        // 2. 初始化线程池
        if (recordExecutor?.isShutdown == false) {
            recordExecutor?.shutdownNow() // 强制关闭可能残留的线程
        }
        recordExecutor = Executors.newSingleThreadExecutor()

        if (timeExecutor?.isShutdown == false) {
            timeExecutor?.shutdownNow()
        }
        timeExecutor = Executors.newSingleThreadExecutor()

        // 3. 创建WAV文件（用 RandomAccessFile 打开，支持读写）
        outputFile = File(saveDir)
        if (outputFile?.exists() == true)
            outputFile?.delete()
        try {
            wavFile = RandomAccessFile(outputFile, "rw")
            // 关键：先写入WAV文件头（占位符，后续更新文件大小）
            writeWavHeader(wavFile!!)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

        // 4. 初始化AudioRecord（语音优化源+硬件降噪）
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // 硬件降噪基础
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        ).takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: run {
            throw IllegalStateException("AudioRecord初始化失败")
        }

        // 5. 初始化原生降噪器（绑定AudioRecord会话）
        initNativeNoiseSuppressor()

        // 6. 开始录音+计时
        isRecording = true
        startTimeMillis = System.currentTimeMillis()
        audioRecord?.startRecording()

        // 录音线程（写入PCM数据）
        recordExecutor?.execute {
            writePcmToWav()
        }

        // 计时线程
        timeExecutor?.execute {
            updateRecordTimeRun()
        }

        return outputFile
    }

    /**
     * 停止录音（更新WAV头+释放资源）
     */
    fun stopRecording() {
        isRecording = false

        // 停止录音并释放AudioRecord
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
                release()
            }
        }

        // 释放降噪器
        noiseSuppressor?.apply {
            release()
        }

        // 关键：更新WAV文件头（补充文件总大小）
        try {
            wavFile?.let { raf ->
                updateWavHeader(raf) // 用 RandomAccessFile 回写
                raf.close() // 关闭文件
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // 关闭线程池
        recordExecutor?.shutdownNow()
        timeExecutor?.shutdownNow()
        try {
            recordExecutor?.awaitTermination(1, TimeUnit.SECONDS)
            timeExecutor?.awaitTermination(1, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        updateTime()

        // 重置状态
        audioRecord = null
        noiseSuppressor = null
        wavFile = null
        outputFile = null
        startTimeMillis = 0
        recordExecutor = null
        timeExecutor = null
    }

    /**
     * 初始化原生降噪器
     */
    private fun initNativeNoiseSuppressor() {
        try {
            val audioSessionId = audioRecord?.audioSessionId ?: return
            // 检查设备是否支持原生降噪
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                noiseSuppressor?.enabled = true // 开启降噪
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 写入PCM数据到WAV文件（降噪已实时生效）
     */
    private fun writePcmToWav() {
        val pcmBuffer = ByteArray(BUFFER_SIZE)
        // 增益系数
        val gain = 1.5f
        while (isRecording) {
            val readSize = audioRecord?.read(pcmBuffer, 0, BUFFER_SIZE) ?: -1

            when (readSize) {
                AudioRecord.ERROR_INVALID_OPERATION,
                AudioRecord.ERROR_BAD_VALUE,
                AudioRecord.ERROR_DEAD_OBJECT -> {
                    isRecording = false
                    break
                }
                -1 -> continue
                else -> {
                    // 对PCM数据进行增益处理（16位PCM格式）
                    val shortBuffer = ShortArray(readSize / 2)
                    // 字节数组转short数组（16位PCM每个样本占2字节）
                    for (i in 0 until readSize step 2) {
                        val value = (pcmBuffer[i + 1].toInt() shl 8) or (pcmBuffer[i].toInt() and 0xFF)
                        shortBuffer[i / 2] = value.toShort()
                    }

                    // 应用增益（限制在Short的取值范围内，避免溢出）
                    for (i in shortBuffer.indices) {
                        var amplified = (shortBuffer[i] * gain).toInt()
                        if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE.toInt()
                        if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE.toInt()
                        shortBuffer[i] = amplified.toShort()
                    }

                    // 处理后的short数组转回字节数组
                    val amplifiedBuffer = ByteArray(readSize)
                    for (i in shortBuffer.indices) {
                        amplifiedBuffer[i * 2] = (shortBuffer[i].toInt() and 0xFF).toByte()
                        amplifiedBuffer[i * 2 + 1] = (shortBuffer[i].toInt() shr 8 and 0xFF).toByte()
                    }

                    try {
                        wavFile?.write(amplifiedBuffer, 0, readSize)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        isRecording = false
                        break
                    }
                }
            }
        }
    }

    /**
     * 写入WAV文件头（44字节固定结构，占位符）
     */
    private fun writeWavHeader(raf: RandomAccessFile) {
        val sampleRate = SAMPLE_RATE
        val channelCount = CHANNEL_COUNT
        val bitDepth = BIT_DEPTH
        val byteRate = sampleRate * channelCount * bitDepth / 8 // 字节率
        val blockAlign = channelCount * bitDepth / 8 // 块对齐（Int类型，无溢出）

        // WAV头数据（44字节）
        val header = ByteArray(44)
        // 1-4字节：RIFF标识
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        // 5-8字节：文件总大小（占位符0，后续更新）
        header[4] = 0; header[5] = 0; header[6] = 0; header[7] = 0
        // 9-12字节：WAVE标识
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // 13-16字节：fmt子块标识
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        // 17-20字节：fmt子块大小（PCM格式固定16）
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        // 21-22字节：音频格式（PCM=1）
        header[20] = 1; header[21] = 0
        // 23-24字节：声道数
        header[22] = channelCount.toByte(); header[23] = 0
        // 25-28字节：采样率（小端序）
        header[24] = (sampleRate and 0xFF).toByte()
        header[25] = (sampleRate shr 8 and 0xFF).toByte()
        header[26] = (sampleRate shr 16 and 0xFF).toByte()
        header[27] = (sampleRate shr 24 and 0xFF).toByte()
        // 29-32字节：字节率（小端序）
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = (byteRate shr 8 and 0xFF).toByte()
        header[30] = (byteRate shr 16 and 0xFF).toByte()
        header[31] = (byteRate shr 24 and 0xFF).toByte()
        // 33-34字节：块对齐（小端序）
        header[32] = (blockAlign and 0xFF).toByte()
        header[33] = (blockAlign shr 8 and 0xFF).toByte()
        // 35-36字节：位深（小端序）
        header[34] = bitDepth.toByte(); header[35] = 0
        // 37-40字节：data子块标识
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        // 41-44字节：数据大小（占位符0，后续更新）
        header[40] = 0; header[41] = 0; header[42] = 0; header[43] = 0

        raf.write(header) // 写入头信息
    }

    /**
     * 更新WAV文件头（用 RandomAccessFile 跳转修改文件大小）
     */
    private fun updateWavHeader(raf: RandomAccessFile) {
        val fileLength = raf.length() // 获取文件总长度（头+数据）
        val dataSize = fileLength - 44 // 数据大小 = 总长度 - 44字节头
        val fileSize = 44 + dataSize // WAV文件总大小（标准格式）

        // 避免溢出（WAV最大支持4GB）
        val fileSizeInt = if (fileSize > Int.MAX_VALUE) Int.MAX_VALUE else fileSize.toInt()
        val dataSizeInt = if (dataSize > Int.MAX_VALUE) Int.MAX_VALUE else dataSize.toInt()

        // 1. 更新文件总大小（第4-7字节，小端序）
        raf.seek(4) // 跳转到第4字节（RandomAccessFile支持seek）
        raf.write(byteArrayOf(
            (fileSizeInt and 0xFF).toByte(),
            (fileSizeInt shr 8 and 0xFF).toByte(),
            (fileSizeInt shr 16 and 0xFF).toByte(),
            (fileSizeInt shr 24 and 0xFF).toByte()
        ))

        // 2. 更新数据大小（第41-44字节，小端序）
        raf.seek(40) // 跳转到第40字节
        raf.write(byteArrayOf(
            (dataSizeInt and 0xFF).toByte(),
            (dataSizeInt shr 8 and 0xFF).toByte(),
            (dataSizeInt shr 16 and 0xFF).toByte(),
            (dataSizeInt shr 24 and 0xFF).toByte()
        ))
    }

    /**
     * 实时更新录音时长
     */
    private fun updateRecordTimeRun() {
        while (isRecording) {
            updateTime()
            // 每秒刷新一次
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
                break
            }
        }
    }


    private fun updateTime(){
        val totalMillis = System.currentTimeMillis() - startTimeMillis
        val totalSeconds = (totalMillis / 1000).toInt()
        val timeFormat = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)

        // 主线程更新UI
        (context as? android.app.Activity)?.runOnUiThread {
            recordTimeListener?.onTimeUpdate(totalMillis, totalSeconds, timeFormat)
        }
    }

    /**
     * 判断是否正在录音
     */
    fun isRecording(): Boolean = isRecording

}