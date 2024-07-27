package com.dy.model;

import lombok.Data;

/**
 * @Author: dy
 * @Date: 2024/7/14 10:59
 * @Description:
 */
@Data
public class JudgeInfo {

    /**
     * 程序执行信息
     */
    private String message;


    /**
     * 程序执行用时(ms)
     */
    private long time;

    /**
     * 程序执行消耗内存(kb)
     */
    private long memory;

}
