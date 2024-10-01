package com.dy.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
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
import java.util.List;
import java.util.UUID;

import static com.dy.constant.CodeBlackList.SENSITIVE_WORD_LIST;

/**
 * @Author: dy
 * @Date: 2024/7/30 10:50
 * @Description:
 */

@Slf4j
public abstract class CodeSandboxTemplate  implements CodeSandBox {

    public static final String GLOBAL_CODE_PATH = "tempcode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    public static final long TIME_OUT = 1000 * 5;

    /**
     * Java安全管理器类存放路径
     */
    private static final String SECURITY_MANAGER_PATH;

    /**
     * Java安全管理器类名
     */
    private static final String SECURITY_MANAGER_CLASS_NAME = "NowSecurityManager";

    /**
     * 使用 Hutool 的工具类，字典树，存放黑名单
     */
    private static final WordTree WORD_TREE;

    static {
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(SENSITIVE_WORD_LIST);
        // 初始安全配置文件路径
        SECURITY_MANAGER_PATH = System.getProperty("user.dir");
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();

        // 1. 将用户代码保存为文件
        String code = executeCodeRequest.getCode();

        // 校验用户代码安全性
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            log.info("用户代码包含禁止词: " + foundWord.getFoundWord());
            // 返回错误信息
            return getErrorResponse(new RuntimeException("用户代码包含禁止词: " + foundWord.getFoundWord()));
        }


        File userCodeFile = saveUserCode(code);

        // 2. 编译代码，得到 class 文件
        // TODO: 2024/9/18 对于编译失败后的处理不太优雅
        compileCode(userCodeFile);

        // 3. 执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = runUserCode(inputList, userCodeFile);

        // 4. 收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(executeMessageList);

        // 5. 文件清理，释放空间
        boolean flag = deleteFile(userCodeFile);
        if (!flag) {
            log.info("用户代码文件未成功删除: {}", userCodeFile.getParentFile().getParent());
        }
        return executeCodeResponse;

    }

    /**
     * 1. 保存用户代码文件
     *
     * @param userCode 用户代码
     * @return
     */
    public File saveUserCode(String userCode) {
        // 获取当前 Java 进程的工作目录
        String userDir = System.getProperty("user.dir");
        log.info("userDir: {}", userDir); //  D:\WorkSpace\OJ\dyoj-code-sandbox

        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_PATH;

        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 每个提交创建一个不同的文件夹(防止文件名冲突)
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //  使用 Hutool 工具类保存用户代码
        return FileUtil.writeString(userCode, userCodePath, StandardCharsets.UTF_8);
    }

    /**
     * 2. 编译用户代码
     *
     * @param userCodeFile 用户代码文件
     */
    public void compileCode(File userCodeFile) {
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        log.info("compileCmd: {}", compileCmd);
        //  javac -encoding utf-8 D:\WorkSpace\OJ\dyoj-code-sandbox\tempcode\7d225a77-3132-4724-aefd-bb357c8269f6\Main.java
        try {
            // 启动一个新进程来运行指定的命令
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

                ExecuteMessage runningMessage = ProcessUtil.runInteractProcessAndGetMessage(runningProcess, inputArgs);
                System.out.println("runningMessage = " + runningMessage);
                executeMessageList.add(runningMessage);
            } catch (IOException e) {
                throw new RuntimeException("程序运行错误");
            }

        }

        log.info("用户程序执行信息: {}", executeMessageList);

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
        log.info("程序输出结果: {}", executeCodeResponse);
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


    /**
     * 返回沙箱的错误信息
     * @param e
     * @return
     */
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
