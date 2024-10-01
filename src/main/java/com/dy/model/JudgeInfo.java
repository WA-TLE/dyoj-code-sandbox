package com.dy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: dy
 * @Date: 2024/7/14 10:59
 * @Description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JudgeInfo {

    /**
     * 程序执行信息
     */
    private String message;


    /**
     * 程序执行用时(ms)
     */
    private Long time;

    /**
     * 程序执行消耗内存(kb)
     */
    private Long memory;

}
