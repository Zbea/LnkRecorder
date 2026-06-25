package com.bll.lnkrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.bll.lnkrecorder.MyApplication.Companion.mContext
import com.bll.lnkrecorder.greendao.RecorderBean
import com.bll.lnkrecorder.greendao.RecorderDaoManager
import com.bll.lnkrecorder.utils.NativeNoiseWavRecorder
import com.bll.lnkrecorder.utils.ToolUtils
import kotlinx.android.synthetic.main.ac_main.tv_list
import kotlinx.android.synthetic.main.ac_main.tv_mediaplayer
import kotlinx.android.synthetic.main.ac_main.tv_recorder
import kotlinx.android.synthetic.main.ac_main.tv_save
import kotlinx.android.synthetic.main.ac_main.tv_time
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.util.Timer
import java.util.TimerTask

class MainActivity : Activity(), EasyPermissions.PermissionCallbacks {

    private val RECORDER_PATH = mContext.getExternalFilesDir("Recorder")!!.path
    private var path: String? = null
    private var mPlayer: MediaPlayer? = null
    private var mRecorderBean: RecorderBean?=null
    private var isPlaying=false
    private var second=0
    private var timer:Timer?=null
    private var isSave=false
    private lateinit var recorder: NativeNoiseWavRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.ac_main)

        if (!EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )){
            EasyPermissions.requestPermissions(this,getString(R.string.permission_apply),1,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
        }

        initRecorderBean()

        initView()


    }

    private fun initView() {

        recorder = NativeNoiseWavRecorder(this)
        // 设置计时回调
        recorder.setOnRecordTimeListener(object : NativeNoiseWavRecorder.OnRecordTimeListener {
            override fun onTimeUpdate(totalMillis: Long, totalSeconds: Int, timeFormat: String) {
                tv_time.text= timeFormat
                second=totalSeconds
                mRecorderBean?.second=totalSeconds
            }
        })

        tv_recorder.setOnClickListener {
            if (recorder.isRecording()){
                recorder.stopRecording()
                tv_recorder.text="开始录音"
            }
            else{
                recorder.startRecording(path!!)
                tv_recorder.text="结束录音"
            }
        }

        tv_mediaplayer.setOnClickListener {
            if (recorder.isRecording()||!File(path!!).exists())
                return@setOnClickListener
            if (!isPlaying){
                isPlaying=true
                tv_mediaplayer.setText(R.string.pause_record)
                if (mPlayer==null){
                    mPlayer = MediaPlayer()
                    mPlayer?.setDataSource(mRecorderBean?.path)
                    mPlayer?.setOnCompletionListener{
                        isPlaying=false
                        timer!!.cancel()
                        tv_mediaplayer.setText(R.string.play_record)
                        second=mRecorderBean?.second!!
                        tv_time.text= ToolUtils().secondToString(second)

                        mPlayer?.reset()
                        mPlayer?.setDataSource(mRecorderBean?.path)
                        mPlayer?.prepare()
                    }
                    mPlayer?.prepare()
                }
                startTimer()
                mPlayer?.start()
            }
            else{
                pauseMediaPlayer()
            }
        }

        tv_save.setOnClickListener {
            if (recorder.isRecording()){
                recorder.stopRecording()
            }
            if (isPlaying){
                pauseMediaPlayer()
            }
            InputContentDialog(this).builder().setOnDialogClickListener{
                mRecorderBean?.title=it
                RecorderDaoManager.getInstance().insertOrReplace(mRecorderBean)
                tv_time.text="00:00"
                initRecorderBean()
            }
        }

        tv_list.setOnClickListener {
            if (recorder.isRecording())
                return@setOnClickListener
            if (isPlaying){
                pauseMediaPlayer()
            }
            startActivity(Intent(this,RecorderListActivity::class.java))
        }

    }

    private fun initRecorderBean(){
        mRecorderBean= RecorderBean()
        mRecorderBean?.time=System.currentTimeMillis()

        if (!File(RECORDER_PATH).exists())
            File(RECORDER_PATH).mkdirs()
        path=File(RECORDER_PATH,"${ToolUtils().timeToString(mRecorderBean?.time!!)}.wav").path

        mRecorderBean?.path=path
    }

    private fun startTimer(){
        timer= Timer()
        timer!!.schedule(object: TimerTask() {
            override fun run() {
                second-=1
                runOnUiThread {
                    tv_time.text= ToolUtils().secondToString(second)
                }
            }
        } ,1000,1000)
    }

    private fun pauseMediaPlayer(){
        isPlaying=false
        timer?.cancel()
        mPlayer?.pause()
        tv_mediaplayer.setText(R.string.play_record)
    }

    private fun releaseMediaPlayer(){
        isPlaying=false
        tv_mediaplayer.setText(R.string.play_record)
        timer?.cancel()
        mPlayer?.pause()
        mPlayer?.release()
        mPlayer=null
    }

    /**
     * 重写要申请权限的Activity或者Fragment的onRequestPermissionsResult()方法，
     * 在里面调用EasyPermissions.onRequestPermissionsResult()，实现回调。
     *
     * @param requestCode  权限请求的识别码
     * @param permissions  申请的权限
     * @param grantResults 授权结果
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }
    /**
     * 当权限被成功申请的时候执行回调
     *
     * @param requestCode 权限请求的识别码
     * @param perms       申请的权限的名字
     */
    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        Log.i("EasyPermissions", getString(R.string.permission_successfully)+perms)
    }
    /**
     * 当权限申请失败的时候执行的回调
     *
     * @param requestCode 权限请求的识别码
     * @param perms       申请的权限的名字
     */
    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        //处理权限名字字符串
        val sb = StringBuffer()
        for (str in perms) {
            sb.append(str)
            sb.append("\n")
        }
        sb.replace(sb.length - 2, sb.length, "")
        //用户点击拒绝并不在询问时候调用
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            Toast.makeText(this, getString(R.string.permission_denied) + sb + getString(R.string.permission_ask_no_more), Toast.LENGTH_SHORT).show()
            AppSettingsDialog.Builder(this)
                .setRationale(getString(R.string.permission_this_function_requires) + sb + getString(R.string.permission_unusable))
                .setPositiveButton(R.string.ok)
                .setNegativeButton(R.string.cancel)
                .build()
                .show()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (!isSave&&!path.isNullOrEmpty())
            File(path).delete()

        releaseMediaPlayer()

        if (recorder.isRecording())
            recorder.stopRecording()
    }

}