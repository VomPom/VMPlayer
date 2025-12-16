package com.vompom.media

/**
 *
 * Created by @juliswang on 2025/12/16 21:56
 *
 * @Description
 */

interface IQueueEvent {


    /**
     * 将任务加入队列中待执行
     *
     * @param r
     */
    fun queueEvent(r: Runnable)
}