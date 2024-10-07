package com.dy.strategy;

import com.dy.sandbox.CodeSandBox;
import com.dy.sandbox.java.JavaDockerCodeSandbox;
import com.dy.sandbox.java.JavaNativeCodeSandbox;
import com.dy.sandbox.cpp.CppNativeCodeSandbox;

/**
 * @Author: dy
 * @Date: 2024/10/1 12:45
 * @Description:
 */
public class SandBoxManager {
    public static CodeSandBox getSandBos(String language) {
        switch (language) {
            case "java":
                return new JavaNativeCodeSandbox();
            case "cpp":
                return new CppNativeCodeSandbox();
            default:
                throw new RuntimeException("不支持该语言!");
        }
    }
}
