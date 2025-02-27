package com.bll.lnkrecorder

import android.app.Application
import android.content.Context
import com.bll.lnkrecorder.greendao.DaoMaster
import com.bll.lnkrecorder.greendao.DaoMaster.DevOpenHelper
import com.bll.lnkrecorder.greendao.DaoSession
import kotlin.properties.Delegates


class MyApplication : Application(){



    companion object {

        private const val TAG = "MyApplication"

        var mContext: Context by Delegates.notNull()
            private set
        var mDaoSession: DaoSession?=null
    }

    override fun onCreate() {
        super.onCreate()
        mContext = applicationContext

        setDatabase()

    }

    /**
     * 配置greenDao
     */
    private fun setDatabase() {
        val mHelper = DevOpenHelper(this, "lnkrecorder.db" , null)
        val  db = mHelper.writableDatabase
        val mDaoMaster = DaoMaster(db)
        mDaoSession = mDaoMaster.newSession()
    }

}
