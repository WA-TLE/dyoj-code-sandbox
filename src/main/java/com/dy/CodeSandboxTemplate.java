package com.dy;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.dy.model.ExecuteCodeRequest;
import com.dy.model.ExecuteCodeResponse;
import com.dy.model.ExecuteMessage;
import com.dy.model.JudgeInfo;
import com.dy.utils.ProcessUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @Author: dy
 * @Date: 2024/7/30 10:50
 * @Description:
 */
@Slf4j
public abstract class CodeSandboxTemplate  implements CodeSanBox {
    public static final String GLOBAL_CODE_PATH = "tempcode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static final long TIME_OUT = 1000 * 5;

    /**
     * 1. 保存用户代码文件
     *
     * @param userCode 用户代码
     * @return
     */
    public File saveUserCode(String userCode) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_PATH;

        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //  用户代码保存的文件
        return FileUtil.writeString(userCode, userCodePath, StandardCharsets.UTF_8);
    }

    /**
     * 2. 编译用户代码
     *
     * @param userCodeFile 用户代码文件
     */
    public void compileCode(File userCodeFile) {
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage compileMessage = ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(compileMessage);
        } catch (IOException e) {
            throw new RuntimeException("用户代码编译错误!");
        }
    }

    /**
     * 3. 执行用户代码, 获取执行结果
     *
     * @param inputList    代码输入用例
     * @param userCodeFile 用户代码文件(用它来得到父目录)
     * @return
     */
    public List<ExecuteMessage> runUserCode(List<String> inputList, File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
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
                }).start();
                ExecuteMessage runningMessage = ProcessUtil.runProcessAndGetMessage(runningProcess, "运行");
                System.out.println("runningMessage = " + runningMessage);
                executeMessageList.add(runningMessage);
            } catch (IOException e) {
                throw new RuntimeException("程序运行错误");
            }

        }

        return executeMessageList;
    }

    /**
     * 4. 整理程序输出结果
     *
     * @param executeMessageList 程序执行信息列表
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
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
        return executeCodeResponse;
    }

    /**
     * 5. 删除用户文件
     *
     * @param userCodeFile 用户代码文件
     * @return
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();

        //  1. 将用户代码保存为文件
        String code = executeCodeRequest.getCode();
        File userCodeFile = saveUserCode(code);

        //  2. 编译代码，得到 class 文件
        compileCode(userCodeFile);

        //  3. 执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = runUserCode(inputList, userCodeFile);

        //  4. 收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(executeMessageList);

        //  5. 文件清理，释放空间
        boolean flag = deleteFile(userCodeFile);
        if (!flag) {
            log.info("用户代码文件未成功删除: {}", userCodeFile.getParentFile().getParent());
        }

        return executeCodeResponse;

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
