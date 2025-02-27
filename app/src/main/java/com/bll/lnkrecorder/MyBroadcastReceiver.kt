package com.bll.lnkrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bll.lnkrecorder.utils.ToolUtils


open class MyBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when(intent.action){
            "com.bll.lnkstudy.account.login","com.bll.lnkteacher.account.login","com.bll.lnkcommon.account.login",
            "com.bll.lnkstudy.account.logout","com.bll.lnkteacher.account.logout","com.bll.lnkcommon.account.logout"->{
                val userId=intent.getLongExtra("userId",0L)
                ToolUtils().saveUserId(userId)
            }
        }
    }
}