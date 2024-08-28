package com.dy.utils;

import com.dy.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @Author: dy
 * @Date: 2024/7/21 13:44
 * @Description:
 */
public class ProcessUtil {

    public static ExecuteMessage runProcessAndGetMessage(Process process, String opName) {
        // 创建一个ExecuteMessage对象来保存执行结果
        ExecuteMessage executeMessage = new ExecuteMessage();

        // 创建一个StopWatch对象来记录执行时间
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();  // 开始计时

        try {
            // 等待进程结束并获取退出状态码
            int exitValue = process.waitFor();
            executeMessage.setExitValue(exitValue);  // 设置ExecuteMessage的退出状态码

            // 读取进程的标准输出流
            String output = readStream(process.getInputStream());
            executeMessage.setMessage(output);  // 设置ExecuteMessage的消息内容

            // 根据退出状态码判断是否成功
            if (exitValue == 0) {
                System.out.println(opName + "成功");
            } else {
                // 如果失败，则读取错误输出流
                String errorOutput = readStream(process.getErrorStream());
                executeMessage.setErrorMessage(errorOutput);  // 设置ExecuteMessage的错误消息
                System.out.println("程序" + opName + "失败");
            }

        } catch (InterruptedException | IOException e) {
            // 捕获可能发生的异常，并抛出运行时异常
            throw new RuntimeException("进程执行失败", e);
        } finally {
            // 停止计时器
            stopWatch.stop();

            // 将执行时间添加到ExecuteMessage对象中
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        }

        // 返回封装了执行结果的ExecuteMessage对象
        return executeMessage;
    }

    // 用于读取InputStream并转换为字符串的方法
    private static String readStream(InputStream inputStream) throws IOException {
        // 创建BufferedReader来读取输入流
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        // 创建StringBuilder来构建输出字符串
        StringBuilder outputStringBuilder = new StringBuilder();

        String line;
        // 循环读取每一行数据
        while ((line = bufferedReader.readLine()) != null) {
            // 将读取到的行添加到StringBuilder中，并加上换行符
            outputStringBuilder.append(line).append("\n");
        }

        // 返回构造好的字符串
        return outputStringBuilder.toString();
    }
}
