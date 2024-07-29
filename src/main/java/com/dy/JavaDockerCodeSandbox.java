package com.dy;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.dy.model.ExecuteCodeRequest;
import com.dy.model.ExecuteCodeResponse;
import com.dy.model.ExecuteMessage;
import com.dy.model.JudgeInfo;
import com.dy.utils.ProcessUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.okhttp.OkDockerHttpClient;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author: dy
 * @Date: 2024/7/20 16:42
 * @Description:
 */
public class JavaDockerCodeSandbox implements CodeSanBox {


    public static final String GLOBAL_CODE_PATH = "tempcode";
    public static final String CLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static Boolean FIRST_INTI = false;

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


        String image = "openjdk:17-alpine";

        if (FIRST_INTI) {
            System.out.println("开始尝试拉取镜像");
            //  拉取镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            System.out.println("镜像准备");
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            System.out.println("镜像准备完成");
            pullImageCmd
                    .exec(pullImageResultCallback) //  这里的参数是一个回调函数?
                    .awaitCompletion(); //  如果程序没有下载完成, 它会一直阻塞在这里
            System.out.println("镜像拉取完成");
            FIRST_INTI = false;
        }

        System.out.println("创建容器");

        //  创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        hostConfig.withMemory(100 * 1024 * 1024L); // 设置为 100MB 内存
        hostConfig.withCpuCount(1L);

        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withAttachStdin(true)  // 把 Docker 容器和本地终端做一个连接
                .withAttachStderr(true) // 可以让 Docker 获取本地的输入, 获取 Docker 的输出
                .withAttachStdout(true)
                .withTty(true)  // 创建一个交互终端
                .withCmd("/bin/sh")  // 设置容器启动时运行的命令
                .exec();
        String containerId = createContainerResponse.getId();

        System.out.println("容器 ID: " + containerId);

        // 启动容器, 并获取执行信息
        //  执行 docker exec zealous_buck java -cp /app Main 7 8, 将输入喂给 Docker 容器
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        dockerClient.startContainerCmd(containerId).exec();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            System.out.println("执行的命令" + Arrays.toString(cmdArray));

            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd("/bin/sh")
                    .withCmd(cmdArray)
                    .withAttachStdin(true)  // 把 Docker 容器和本地终端做一个连接
                    .withAttachStderr(true) // 可以让 Docker 获取本地的输入, 获取 Docker 的输出
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令: " + execCreateCmdResponse);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            String execId = execCreateCmdResponse.getId();

            //  创建回调函数
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    //  获取输出结果
                    if (streamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果: " + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出正确结果: " + message[0]);
                    }
                    super.onNext(frame);
                }
            };
            //  获取占用的内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            final long[] maxMemory = {0L};

            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    long memory = statistics.getMemoryStats().getUsage();
                    maxMemory[0] = Math.max(maxMemory[0], memory);
                    System.out.println("内存占用: " + maxMemory[0]);
                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });

            statsCmd.exec(statisticsResultCallback);


            stopWatch.start();
            //  执行程序
            dockerClient.execStartCmd(execId)
                    .exec(execStartResultCallback)
                    .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS); //  超时控制
            stopWatch.stop();
            long time = stopWatch.getLastTaskTimeMillis();
            statsCmd.close(); //  记得关闭

            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            // TODO: 7/29/24 内存可能还没更新
            System.out.println("内存占用!!!!!!!: " + maxMemory[0]);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);

            System.out.println("程序执行信息: " + executeMessage);



        }



        //  4. 收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        ArrayList<String> outputList = new ArrayList<>();
        long maxTime = 0;
        long memory = 0L;
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
            Long memory1 = executeMessage.getMemory();
            if (memory1 != null) {
                memory = Math.max((memory), memory1);
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
        judgeInfo.setMemory(memory);

        executeCodeResponse.setJudgeInfo(judgeInfo);


        //  5. 文件清理，释放空间
        if (userCodeFile.getParentFile() != null) {
            FileUtil.del(userCodeParentPath);
        }


        System.out.println("最终得到的结果: " + executeCodeResponse);

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
