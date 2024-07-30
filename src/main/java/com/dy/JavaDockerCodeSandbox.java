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
public class JavaDockerCodeSandbox extends CodeSandboxTemplate {



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
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    /**
     * 3. 运行程序代码
     * @param inputList    代码输入用例
     * @param userCodeFile 用户代码文件(用它来得到父目录)
     * @return
     */
    @Override
    public List<ExecuteMessage> runUserCode(List<String> inputList, File userCodeFile) {

        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

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

        //  创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        hostConfig.withMemory(256 * 1024 * 1024L); // 设置为 256MB 内存
        hostConfig.withCpuCount(1L);
        hostConfig.withMemorySwap(0L);

        // TODO: 7/29/24 使用 Linux 自带安全管理措施
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withAttachStdin(true)  // 把 Docker 容器和本地终端做一个连接
                .withAttachStderr(true) // 可以让 Docker 获取本地的输入, 获取 Docker 的输出
                .withAttachStdout(true)
                .withNetworkDisabled(true) // 限制程序访问网络
                .withReadonlyRootfs(true)
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

            final boolean[] timeout = {true};

            //  创建回调函数
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){

                @Override
                public void onComplete() {
                    //  如果正常执行完成, 表明未超时
                    timeout[0] = false;
                    super.onComplete();
                }

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
            try {
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS); //  超时控制
            } catch (InterruptedException e) {
                System.out.println("程序执行错误:");
                throw new RuntimeException(e);
            }
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

        return executeMessageList;

    }


}
