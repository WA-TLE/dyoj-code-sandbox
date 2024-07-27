package com.dy.model;

import lombok.Data;

/**
 * @Author: dy
 * @Date: 2024/7/21 13:45
 * @Description:
 */
@Data
public class ExecuteMessage {
    private Integer exitValue;
    private String message;
    private String errorMessage;
    /**
     * 程序执行时间
     */
    private Long time;
}
