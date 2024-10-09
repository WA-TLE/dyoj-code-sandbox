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

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

}
