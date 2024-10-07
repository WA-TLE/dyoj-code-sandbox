package com.dy.sandbox.java;

import cn.hutool.core.io.resource.ResourceUtil;
import com.dy.model.ExecuteCodeRequest;
import com.dy.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @Author: dy
 * @Date: 2024/7/20 16:42
 * @Description:
 */
@Component
public class JavaNativeCodeSandbox extends CodeSandboxTemplate {

    /**
     * 用于测试
     * @param args
     */
    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setLanguage("java");
        String code = ResourceUtil.readStr("tempcode/Main.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("tempcode/timeout/TimeoutMain.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("15 12", "1 3"));
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }



}
