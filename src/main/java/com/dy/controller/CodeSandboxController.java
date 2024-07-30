package com.dy.controller;

import com.dy.JavaNativeCodeSandbox;
import com.dy.model.ExecuteCodeRequest;
import com.dy.model.ExecuteCodeResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: dy
 * @Date: 2024/7/30 17:29
 * @Description:
 */
@RestController("/")
@Slf4j
public class CodeSandboxController {

    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest) {
        log.info("远程代码沙箱调用: {}", executeCodeRequest);
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }
}
