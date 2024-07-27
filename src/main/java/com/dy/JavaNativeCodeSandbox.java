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
public class JavaNativeCodeSandbox implements CodeSanBox {


    public static final String GLOBAL_CODE_PATH = "tempcode";
    public static final String CLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static final long TIME_OUT = 1000 * 5;

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
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();

        //  1. 把用户的代码保存为文件
        //   1.2 获取当前用户的系统属性
        String userDir = System.getProperty("user.dir");

        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_PATH;

        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + CLOBAL_JAVA_CLASS_NAME;

        //  用户代码保存的文件
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        //  2. 编译代码，得到 class 文件
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
//        String compileCmd = String.format("java -version");
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);

            ExecuteMessage compileMessage = ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(compileMessage);

        } catch (IOException e) {
            return getErrorResponse(e);
        }

        //  3. 执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runningCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process runningProcess = Runtime.getRuntime().exec(runningCmd);

                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        if (runningProcess.isAlive()) {
                            runningProcess.destroy();
                            throw new RuntimeException("程序超出时间限制");
                        }
                    } catch (InterruptedException e) {

                        throw new RuntimeException(e);
                    }
                    System.out.println("!!!!!!!!!");

                }).start();

                ExecuteMessage runningMessage = ProcessUtil.runProcessAndGetMessage(runningProcess, "运行");
                System.out.println("runningMessage = " + runningMessage);
                executeMessageList.add(runningMessage);
            } catch (IOException e) {
                return getErrorResponse(e);
            }

        }

        //  4. 收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        ArrayList<String> outputList = new ArrayList<>();
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (errorMessage != null) {
                //  代码执行错误!
                executeCodeResponse.setStatus(3);
                executeCodeResponse.setMessage(errorMessage);
                break;
            }
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max((maxTime), time);
            }
            outputList.add(executeMessage.getMessage());
        }

        if (outputList.size() == executeMessageList.size()) {
            //  程序正常退出
            executeCodeResponse.setStatus(1);
            executeCodeResponse.setOutputList(outputList);
            executeCodeResponse.setMessage("程序执行成功!");
        }

        JudgeInfo judgeInfo = new JudgeInfo();

        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(0L);

        executeCodeResponse.setJudgeInfo(judgeInfo);


        //  5. 文件清理，释放空间
        if (userCodeFile.getParentFile() != null) {
            FileUtil.del(userCodeParentPath);
        }


        return executeCodeResponse;
        //  6. 错误处理，提升程序健壮性
    }

    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        //  表示代码沙箱错误
        executeCodeResponse.setStatus(2);

        return executeCodeResponse;
    }

}
