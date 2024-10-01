package com.dy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: dy
 * @Date: 2024/7/21 13:45
 * @Description:
 */
@Data
public class ExecuteMessage {
    private Integer exitCode;
    private String message;
    private String errorMessage;
    /**
     * 程序执行时间
     */
    private Long time;

    private Long memory;
}
