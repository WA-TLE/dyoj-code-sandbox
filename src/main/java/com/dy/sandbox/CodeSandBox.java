package com.dy.sandbox;


import com.dy.model.ExecuteCodeRequest;
import com.dy.model.ExecuteCodeResponse;

/**
 * @Author: dy
 * @Date: 2024/7/16 17:08
 * @Description:
 */
public interface CodeSandBox {
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) ;
}
