package com.dy.controller;

import com.dy.sandbox.CodeSandBox;
import com.dy.sandbox.JavaNativeCodeSandbox;
import com.dy.model.ExecuteCodeRequest;
import com.dy.model.ExecuteCodeResponse;
import com.dy.strategy.SandBoxManager;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    //  定义鉴权请求头
    public static final String AUTH_REQUEST_HEADER = "auth";
    public static final String AUTH_REQUEST_SECRET = "dingyu";

//    @Resource
//    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
                                           HttpServletResponse response) {
        //  获取请求头
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
//        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
//            response.setStatus(403);
//            return null;
//        }

        String language = executeCodeRequest.getLanguage();
        CodeSandBox sandBos = SandBoxManager.getSandBos(language);

        log.info("远程代码沙箱调用: {}", executeCodeRequest);
        return sandBos.executeCode(executeCodeRequest);
    }
}
