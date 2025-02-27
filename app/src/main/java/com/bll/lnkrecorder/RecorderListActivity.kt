package com.bll.lnkrecorder

import android.app.Activity
import android.media.MediaPlayer
import android.os.Bundle
import android.os.FileUtils
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.bll.lnkrecorder.greendao.RecorderBean
import com.bll.lnkrecorder.greendao.RecorderDaoManager
import com.bll.lnkrecorder.utils.ToolUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import kotlinx.android.synthetic.main.ac_list.btn_page_down
import kotlinx.android.synthetic.main.ac_list.btn_page_up
import kotlinx.android.synthetic.main.ac_list.iv_back
import kotlinx.android.synthetic.main.ac_list.ll_page_number
import kotlinx.android.synthetic.main.ac_list.rv_list
import kotlinx.android.synthetic.main.ac_list.tv_page_current
import kotlinx.android.synthetic.main.ac_list.tv_page_total
import java.io.File
import java.util.Timer
import java.util.TimerTask
import kotlin.math.ceil

class RecorderListActivity: Activity() {
    private var mAdapter:RecordAdapter?=null
    private var recorders= mutableListOf<RecorderBean>()
    private var pageSize=12
    private var pageIndex=1
    private var pageCount=0
    private var position=0
    private var currentPos = -1//当前点击位置
    private var mediaPlayer: MediaPlayer? = null
    private var timer:Timer?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ac_list)

        initView()
        initRecyclerView()
        fetchData()
    }

    private fun initView() {
        iv_back.setOnClickListener {
            finish()
        }

        btn_page_up?.setOnClickListener {
            if(pageIndex>1){
                pageIndex-=1
                fetchData()
            }
        }

        btn_page_down?.setOnClickListener {
            if(pageIndex<pageCount){
                pageIndex+=1
                fetchData()
            }
        }

    }

    private fun initRecyclerView(){
        rv_list.layoutManager = LinearLayoutManager(this)//创建布局管理
        mAdapter = RecordAdapter(R.layout.item_record, null)
        rv_list.adapter = mAdapter
        mAdapter?.bindToRecyclerView(rv_list)
        mAdapter?.setEmptyView(R.layout.common_empty)
        mAdapter?.setOnItemChildClickListener { adapter, view, position ->
            this.position =position
            val item=recorders[position]
            when(view.id){
                R.id.iv_delete->{
                    File(item.path).delete()
                    RecorderDaoManager.getInstance().deleteBean(item)
                    mAdapter?.remove(position)
                    if (recorders.size==0){
                        if (pageIndex>1)
                            pageIndex-=1
                        fetchData()
                    }
                }
                R.id.iv_record->{
                    if (currentPos == position) {
                        if (mediaPlayer!!.isPlaying) {
                            pause(position)
                        } else {
                            mediaPlayer?.start()
                            item.state=1
                            mAdapter?.notifyItemChanged(position)//刷新为播放状态
                            startTimer()
                        }
                    } else {
                        if (currentPos!=-1){
                            if (mediaPlayer!!.isPlaying) {
                                pause(currentPos)
                            }
                            recorders[currentPos].currentSecond=recorders[currentPos].second
                            mAdapter?.notifyItemChanged(currentPos)
                            release()
                        }
                        play()
                    }
                    currentPos = position
                }
            }
        }
    }

    private fun release(){
        if (mediaPlayer!=null){
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private fun play(){
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setDataSource(recorders[position].path)
        mediaPlayer?.setOnCompletionListener {
            recorders[position].state=0
            recorders[position].currentSecond=recorders[position].second
            mAdapter?.notifyItemChanged(position)//刷新为结束状态
            timer?.cancel()

            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(recorders[position].path)
            mediaPlayer?.prepare()
        }
        mediaPlayer?.prepare()
        mediaPlayer?.start()
        recorders[position].state=1
        mAdapter?.notifyItemChanged(position)//刷新为播放状态
        startTimer()
    }

    private fun pause(pos:Int){
        mediaPlayer?.pause()
        recorders[pos].state=0
        mAdapter?.notifyItemChanged(pos)//刷新为结束状态
        timer?.cancel()
    }

    private fun startTimer(){
        timer= Timer()
        timer!!.schedule(object: TimerTask() {
            override fun run() {
                recorders[position].currentSecond-=1
                runOnUiThread {
                    mAdapter?.notifyItemChanged(position)
                }
            }
        } ,1000,1000)
    }

    /**
     * 设置翻页
     */
    private fun setPageNumber(total:Int){
        ll_page_number.visibility=if (total==0) View.GONE else View.VISIBLE
        pageCount = ceil(total.toDouble() / pageSize).toInt()
        tv_page_current.text = pageIndex.toString()
        tv_page_total.text = pageCount.toString()
    }

    private fun fetchData(){
        val total=RecorderDaoManager.getInstance().queryAll().size
        recorders=RecorderDaoManager.getInstance().queryAll(pageIndex,pageSize)
        for (item in recorders){
            item.currentSecond=item.second
        }
        setPageNumber(total)
        mAdapter?.setNewData(recorders)
    }

    class RecordAdapter(layoutResId: Int, data: MutableList<RecorderBean>?) : BaseQuickAdapter<RecorderBean, BaseViewHolder>(layoutResId, data) {
        override fun convert(helper: BaseViewHolder, item: RecorderBean) {
            helper.apply {
                setText(R.id.tv_title,item.title)
                setText(R.id.tv_date, ToolUtils().longToStringWeek(item.time))
                setText(R.id.tv_second,ToolUtils().secondToString(item.currentSecond))
                setImageResource(R.id.iv_record,if (item.state==0) R.mipmap.icon_record_play else R.mipmap.icon_record_pause)
                addOnClickListener(R.id.iv_record,R.id.iv_delete)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer=null
        release()
    }

}