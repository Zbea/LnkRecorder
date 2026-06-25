package com.bll.lnkrecorder.greendao;


import com.bll.lnkrecorder.MyApplication;

import java.util.List;

public class RecorderDaoManager {

    private DaoSession mDaoSession;
    private static RecorderDaoManager mDbController;
    private final RecorderBeanDao dao;

    public RecorderDaoManager() {
        mDaoSession = MyApplication.Companion.getMDaoSession();
        dao = mDaoSession.getRecorderBeanDao();
    }

    public static RecorderDaoManager getInstance() {
        if (mDbController == null) {
            synchronized (RecorderDaoManager.class) {
                if (mDbController == null) {
                    mDbController = new RecorderDaoManager();
                }
            }
        }
        return mDbController;
    }

    public void insertOrReplace(RecorderBean bean) {
        dao.insertOrReplace(bean);
    }

    public List<RecorderBean> queryAll() {
        return dao.queryBuilder().build().list();
    }

    public List<RecorderBean> queryAll(int page, int pageSize) {
        return dao.queryBuilder()
                .orderDesc(RecorderBeanDao.Properties.Time).offset((page - 1) * pageSize).limit(pageSize).build().list();
    }

    public void deleteBean(RecorderBean bean) {
        dao.delete(bean);
    }

}
