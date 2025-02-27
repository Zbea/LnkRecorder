package com.bll.lnkrecorder.greendao;


import android.content.Context;
import android.content.SharedPreferences;

import com.bll.lnkrecorder.MyApplication;
import com.bll.lnkrecorder.utils.ToolUtils;

import org.greenrobot.greendao.query.WhereCondition;

import java.util.List;
import java.util.Objects;

public class RecorderDaoManager {

    /**
     * DaoSession
     */
    private DaoSession mDaoSession;
    /**
     *
     */
    private static RecorderDaoManager mDbController;

    private final RecorderBeanDao dao;
    private static WhereCondition whereUser;
    /**
     * 构造初始化
     */
    public RecorderDaoManager() {
        mDaoSession= MyApplication.Companion.getMDaoSession();
        dao = mDaoSession.getRecorderBeanDao();
    }

    /**
     * 获取单例（context 最好用application的context  防止内存泄漏）
     */
    public static RecorderDaoManager getInstance() {
        if (mDbController == null) {
            synchronized (RecorderDaoManager.class) {
                if (mDbController == null) {
                    mDbController = new RecorderDaoManager();
                }
            }
        }

        long userId = new ToolUtils().getUserId();
        whereUser= RecorderBeanDao.Properties.UserId.eq(userId);
        return mDbController;
    }

    public void insertOrReplace(RecorderBean bean) {
        dao.insertOrReplace(bean);
    }

    public List<RecorderBean> queryAll() {
        return dao.queryBuilder().where(whereUser).build().list();
    }

    public List<RecorderBean> queryAll(int page, int pageSize) {
        return dao.queryBuilder().where(whereUser)
                .orderDesc(RecorderBeanDao.Properties.Time).offset((page-1)*pageSize).limit(pageSize).build().list();
    }

    public void deleteBean(RecorderBean bean){
        dao.delete(bean);
    }

}
