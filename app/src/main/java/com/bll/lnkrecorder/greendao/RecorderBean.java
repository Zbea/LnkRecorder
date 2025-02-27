package com.bll.lnkrecorder.greendao;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Transient;
import org.greenrobot.greendao.annotation.Unique;
import org.greenrobot.greendao.annotation.Generated;

@Entity
public class RecorderBean {
    @Id(autoincrement = true)
    @Unique
    public Long id;
    public long userId;
    public String title;
    public long time;
    public String path;
    public int second;
    @Transient
    public int state;
    @Transient
    public int currentSecond;

    @Generated(hash = 1369675094)
    public RecorderBean(Long id, long userId, String title, long time, String path,
            int second) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.time = time;
        this.path = path;
        this.second = second;
    }
    @Generated(hash = 1403160276)
    public RecorderBean() {
    }
    public Long getId() {
        return this.id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public long getUserId() {
        return this.userId;
    }
    public void setUserId(long userId) {
        this.userId = userId;
    }
    public String getTitle() {
        return this.title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public long getTime() {
        return this.time;
    }
    public void setTime(long time) {
        this.time = time;
    }
    public String getPath() {
        return this.path;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public int getSecond() {
        return this.second;
    }
    public void setSecond(int second) {
        this.second = second;
    }
}
