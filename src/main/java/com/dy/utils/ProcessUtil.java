package com.dy.utils;

import com.dy.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @Author: dy
 * @Date: 2024/7/21 13:44
 * @Description:
 */
public class ProcessUtil {
    public static ExecuteMessage runProcessAndGetMessage(Process process, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            int exitValue = process.waitFor();
            executeMessage.setExitValue(0);

            if (exitValue == 0) {
                System.out.println(opName + "成功");

                //  分批获取正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String compileOutputLine;
                StringBuilder compileOUtStringBuilder = new StringBuilder();
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOUtStringBuilder.append(compileOutputLine);
                }

                executeMessage.setMessage(compileOUtStringBuilder.toString());

                /*                // 分批获取错误输出（此处获取到的才是 `java -version` 的输出）
                                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream()));
                                String errorCompileOutputLine;
                                while ((errorCo mpileOutputLine = errorBufferedReader.readLine()) != null) {
                                    System.out.println("错误流信息: ---->  " + errorCompileOutputLine);
                                }*/

            } else {

                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());


                //  获取错误信息???
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String errorCompileOutputLine;

                StringBuilder errorCompileOUtStringBuilder = new StringBuilder();

                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorCompileOUtStringBuilder.append(errorCompileOutputLine).append("\n");
                }
                executeMessage.setErrorMessage(errorCompileOUtStringBuilder.toString());

                System.out.println("程序" + opName + "失败");

            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        stopWatch.stop();
        executeMessage.setTime(stopWatch.getLastTaskTimeMillis());


        return executeMessage;
    }
}
