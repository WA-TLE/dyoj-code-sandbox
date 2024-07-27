package com.dy;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.dy.model.ExecuteCodeRequest;
import com.dy.model.ExecuteCodeResponse;
import com.dy.model.ExecuteMessage;
import com.dy.model.JudgeInfo;
import com.dy.utils.ProcessUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.okhttp.OkDockerHttpClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
public class JavaDockerCodeSandbox implements CodeSanBox {


    public static final String GLOBAL_CODE_PATH = "tempcode";
    public static final String CLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static Boolean FIRST_INTI = true;

    public static final long TIME_OUT = 1000 * 5;

    public static void main(String[] args) throws InterruptedException {
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
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
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) throws InterruptedException {
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

        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);

            ExecuteMessage compileMessage = ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(compileMessage);

        } catch (IOException e) {
            return getErrorResponse(e);
        }

        //  获取 OpenJdk 镜像
        URI uri = null;
        try {
            uri = new URI("unix:///var/run/docker.sock");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        //  todo 手动配置 Docker HTTP Client
        DockerClient dockerClient = DockerClientBuilder.getInstance()
                .withDockerHttpClient(new OkDockerHttpClient.Builder()
                        .dockerHost(uri) // 指定 Docker 主机地址
                        .build())
                .build();


        String image = "docker pull openjdk:17-alpine";

        if (FIRST_INTI) {
            //  拉取镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            pullImageCmd
                    .exec(pullImageResultCallback) //  这里的参数是一个回调函数?
                    .awaitCompletion(); //  如果程序没有下载完成, 它会一直阻塞在这里
            System.out.println("镜像拉取完成");
            FIRST_INTI = false;
        }


        //  创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withCpuCount(1L);
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withCmd("echo", "Hello Docker")
                .withAttachStdin(true)  //  把 Docker 容器和本地终端做一个连接
                .withAttachStderr(true) //  可以让 Docker 获取本地的输入, 获取 Docker 的输出
                .withAttachStdout(true)
                .withTty(true)  //  创建一个交互终端
                .exec();


        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

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