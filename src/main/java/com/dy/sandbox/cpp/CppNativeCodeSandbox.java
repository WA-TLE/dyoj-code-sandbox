package com.dy.sandbox.cpp;


import com.dy.model.ExecuteCodeRequest;
import com.dy.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;


/**
 * Java 原生代码沙箱实现（直接复用模板方法）
 *
 * @author 15712
 */
@Component
public class CppNativeCodeSandbox extends CppCodeSandboxTemplate {

    /**
     * 执行代码
     *
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}

