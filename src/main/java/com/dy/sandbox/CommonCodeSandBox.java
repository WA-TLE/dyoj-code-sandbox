package com.dy.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.dy.model.ExecuteCodeResponse;
import com.dy.model.ExecuteMessage;
import com.dy.model.JudgeInfo;
import lombok.extern.slf4j.Slf4j;


import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.dy.sandbox.CodeSandboxTemplate.GLOBAL_CODE_PATH;
import static com.dy.sandbox.CodeSandboxTemplate.GLOBAL_JAVA_CLASS_NAME;

/**
 * @author 15712
 */
@Slf4j
public abstract class CommonCodeSandBox implements CodeSandBox {

    /**
     * 1.保存用户代码
     *
     * @param userCode     代码
     * @param language 语言
     * @return
     */
    public File saveCodeToFile(String userCode, String language , String globalCodePath, String fileName) {
//        String projectPath = System.getProperty("user.dir");
//        String globalCodePathName = projectPath + File.separator + globalCodePath;
//        if (!FileUtil.exist(globalCodePathName)) {
//            FileUtil.mkdir(globalCodePathName);
//        }
//        //把用户代码隔离
//        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
//        String userCodePath = userCodeParentPath + File.separator + fileName;
//        return FileUtil.writeUtf8String(code, userCodePath);


        // 获取当前 Java 进程的工作目录
        String userDir = System.getProperty("user.dir");
        log.info("userDir: {}", userDir); //  D:\WorkSpace\OJ\dyoj-code-sandbox

        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_PATH;

        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 每个提交创建一个不同的文件夹(防止文件名冲突)
        String userCodeParentPath = globalCodePathName + File.separator + java.util.UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //  使用 Hutool 工具类保存用户代码
        return FileUtil.writeString(userCode, userCodePath, StandardCharsets.UTF_8);
    }


    /**
     * 收集运行结果
     * @param executeMessageList 执行结果列表
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        //4. 收集整理输出结果
        //准备返回信息对象
     /*   ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        long maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                outputList.add(executeMessage.getMessage());
                //执行中出现错误
                // 用户提交的代码执行中存在错误,直接返回
                executeCodeResponse.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
                executeCodeResponse.setJudgeInfo(new JudgeInfo(errorMessage, null, null));
                break;
            }
            //如果没有错误信息就正常添加
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);

            }
            Long memory = executeMessage.getMemory();
            if (memory != null)
            {
                maxMemory += memory;
            }
        }
        //没有错误信息
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
            executeCodeResponse.setMessage(QuestionSubmitStatusEnum.SUCCEED.getText());
        }
        executeCodeResponse.setOutputList(outputList);
        //正常运行
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMemory(maxMemory);
        judgeInfo.setTime(maxTime);
        // 运行正常完成则不设置message，交由判题机判题
        executeCodeResponse.setJudgeInfo(judgeInfo);
        System.out.println(executeCodeResponse);
        return executeCodeResponse*/;
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
     * 删除文件
     * @param userCodeFile 用户代码文件
     * @return
     */
    public boolean delCodeFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeFile.getParentFile().getAbsolutePath());
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

}
