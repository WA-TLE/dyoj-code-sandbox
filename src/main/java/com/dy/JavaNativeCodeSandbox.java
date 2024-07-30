package com.dy;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.dy.model.ExecuteCodeRequest;
import com.dy.model.ExecuteCodeResponse;
import com.dy.model.ExecuteMessage;
import com.dy.model.JudgeInfo;
import com.dy.utils.ProcessUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @Author: dy
 * @Date: 2024/7/20 16:42
 * @Description:
 */
public class JavaNativeCodeSandbox extends CodeSandboxTemplate {

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
