package com.vompom.media.effect

/**
 *
 * Created by @juliswang on 2025/12/16 21:56
 *
 * @Description 渲染线程的事件队列，支持将任务抛到 GL 线程执行
 */
interface IQueueEvent {

    /**
     * 将任务加入队列中待执行
     *
     * @param r
     */
    fun queueEvent(r: Runnable)
}
