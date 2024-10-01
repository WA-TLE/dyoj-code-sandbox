package com.dy.utils;

import com.dy.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.StringUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: dy
 * @Date: 2024/7/21 13:44
 * @Description:
 */
@Slf4j
public class ProcessUtil {

//    public static ExecuteMessage runProcessAndGetMessage(Process process, String opName) {
//        // 创建一个ExecuteMessage对象来保存执行结果
//        ExecuteMessage executeMessage = new ExecuteMessage();
//
//        // 创建一个StopWatch对象来记录执行时间
//        StopWatch stopWatch = new StopWatch();
//        stopWatch.start();  // 开始计时
//
//        try {
//            // 等待进程结束并获取退出状态码
//            int exitValue = process.waitFor();
//            executeMessage.setExitCode(exitValue);  // 设置ExecuteMessage的退出状态码
//
//            // 读取进程的标准输出流
//            String output = readStream(process.getInputStream()).trim();
//            executeMessage.setMessage(output);  // 设置ExecuteMessage的消息内容
//
//            // 根据退出状态码判断是否成功
//            if (exitValue == 0) {
//                System.out.println(opName + "成功");
//            } else {
//                // 如果失败，则读取错误输出流
//                String errorOutput = readStream(process.getErrorStream());
//                executeMessage.setErrorMessage(errorOutput);  // 设置ExecuteMessage的错误消息
//                System.out.println("程序" + opName + "失败");
//            }
//
//        } catch (InterruptedException | IOException e) {
//            // 捕获可能发生的异常，并抛出运行时异常
//            throw new RuntimeException("进程执行失败", e);
//        } finally {
//            // 停止计时器
//            stopWatch.stop();
//
//            // 将执行时间添加到ExecuteMessage对象中
//            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
//        }
//
//        // 返回封装了执行结果的ExecuteMessage对象
//        return executeMessage;
//    }
//
//    // 用于读取InputStream并转换为字符串的方法
//    private static String readStream(InputStream inputStream) throws IOException {
//        // 创建BufferedReader来读取输入流
//        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
//
//        // 创建StringBuilder来构建输出字符串
//        StringBuilder outputStringBuilder = new StringBuilder();
//
//        String line;
//        // 循环读取每一行数据
//        while ((line = bufferedReader.readLine()) != null) {
//            // 将读取到的行添加到StringBuilder中，并加上换行符
//            outputStringBuilder.append(line).append("\n");
//        }
//
//        // 返回构造好的字符串
//        return outputStringBuilder.toString();
//    }


    /**
     * @param runProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        //记录程序还未执行的内存使用量
        long initialMemory = getUsedMemory();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            //等待程序执行获取错误码
            int exitCode = runProcess.waitFor();
            executeMessage.setExitCode(exitCode);
            //正常退出
            if (exitCode == 0) {
                System.out.println(opName + "成功");
                //运行正常输出流
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream(), StandardCharsets.UTF_8));
                List<String> outputStrList = new ArrayList<>();
                //进行逐行读取
                String complieOutLine;
                while ((complieOutLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(complieOutLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList, '\n'));
            } else {
                //异常退出
                System.out.println(opName + "失败：错误码：" + exitCode);
                //运行正常输出流
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream(), StandardCharsets.UTF_8));
                List<String> outputStrList = new ArrayList<>();
                //进行逐行读取
                String complieOutLine;
                while ((complieOutLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(complieOutLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList, '\n'));
                //分批获取错误输出
                BufferedReader bufferedReaderError = new BufferedReader(new InputStreamReader(runProcess.getErrorStream(), StandardCharsets.UTF_8));
                //逐行读取
                List<String> errorOutputStrList = new ArrayList<>();
                String complieOutLineError;
                while ((complieOutLineError = bufferedReaderError.readLine()) != null) {
                    errorOutputStrList.add(complieOutLineError);
                }
                executeMessage.setErrorMessage(StringUtils.join(errorOutputStrList, '\n'));
            }
            long finalMemory = getUsedMemory();
            // 计算内存使用量，单位字节，转换成kb需要除以1024
            long memoryUsage = finalMemory - initialMemory;
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getTotalTimeMillis());
            executeMessage.setMemory(memoryUsage);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return executeMessage;
    }

    /**
     * 执行交互式进程并获取信息
     *
     * @param runProcess
     * @param args
     * @return
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess, String args) throws IOException {
        // 向控制台输入程序
        ExecuteMessage executeMessage = new ExecuteMessage();
        //记录程序还未执行的内存使用量
        long initialMemory = getUsedMemory();
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(runProcess.getOutputStream())) {
            String[] arguments = args.split(" ");
            for (String arg : arguments) {
                outputStreamWriter.write(arg);
                outputStreamWriter.write("\n");
            }
            // 相当于按了回车，执行输入的发送
            outputStreamWriter.flush();
            //记录程序开始执行时间
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            int exitCode = runProcess.waitFor();
            stopWatch.stop();
            executeMessage.setExitCode(exitCode);
            if (exitCode == 0) {
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                List<String> outputStrList = new ArrayList<>();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(compileOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList, '\n'));
            } else {
                //异常退出
                System.out.println("失败：错误码：" + exitCode);
                //运行正常输出流
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream(), StandardCharsets.UTF_8));
                List<String> outputStrList = new ArrayList<>();
                //进行逐行读取
                String complieOutLine;
                while ((complieOutLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(complieOutLine);
                }
                executeMessage.setErrorMessage(StringUtils.join(outputStrList, '\n'));
                //分批获取错误输出
                BufferedReader bufferedReaderError = new BufferedReader(new InputStreamReader(runProcess.getErrorStream(), StandardCharsets.UTF_8));
                //逐行读取
                List<String> errorOutputStrList = new ArrayList<>();
                String complieOutLineError;
                while ((complieOutLineError = bufferedReaderError.readLine()) != null) {
                    errorOutputStrList.add(complieOutLineError);
                }
                executeMessage.setErrorMessage(StringUtils.join(errorOutputStrList, '\n'));

            }
            long finalMemory = getUsedMemory();
            // 计算内存使用量，单位字节，转换成kb需要除以1024
            long memoryUsage = finalMemory - initialMemory;
            executeMessage.setTime(stopWatch.getTotalTimeMillis());
            executeMessage.setMemory(memoryUsage);
        } catch (Exception e) {
            // 使用日志框架记录异常
            log.error("执行交互式进程出错", e);
        } finally {
            // 记得资源的释放，否则会卡死
            runProcess.destroy();
        }
        return executeMessage;
    }

    /**
     * 获取当前已使用的内存量
     * 单位是byte
     *
     * @return
     */
    public static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

}
