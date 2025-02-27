package com.bll.lnkrecorder.utils

import android.content.Context
import com.bll.lnkrecorder.MyApplication.Companion.mContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ToolUtils {

     fun timeToString(date: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmSS", Locale.CHINA)
        return sdf.format(Date(date))
    }

     fun secondToString(second:Int):String{
        val sdf = SimpleDateFormat("mm:ss", Locale.CHINA)
        return sdf.format(Date(second*1000L))
    }

    fun longToStringWeek(date: Long): String{
        val sdf = SimpleDateFormat("MM - dd  EE") // "yyyy-MM-dd HH:mm:ss"
        return sdf.format(Date(date))
    }

    fun getUserId():Long{
        val sharedPreferences = mContext.getSharedPreferences("config", Context.MODE_PRIVATE)
        return sharedPreferences.getLong("userId", 0L)
    }

    fun saveUserId(userId:Long){
        val  sharedPreferences = mContext.getSharedPreferences("config", Context.MODE_PRIVATE)
        val  editor = sharedPreferences.edit()
        editor.putLong("userId", userId)
        editor.apply()
    }
}