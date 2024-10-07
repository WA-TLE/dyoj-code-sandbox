package com.dy.sandbox.java;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.ArrayUtil;
import com.dy.model.ExecuteCodeRequest;
import com.dy.model.ExecuteCodeResponse;
import com.dy.model.ExecuteMessage;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @Author: dy
 * @Date: 2024/7/20 16:42
 * @Description:
 */
@Component
public class JavaDockerCodeSandbox extends CodeSandboxTemplate {

    private static final long TIME_OUT = 10000L; // 超时时间，毫秒

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    /**
     * 重新执行代码，得到输出结果
     * @param inputList    代码输入用例
     * @param userCodeFile 用户代码文件(用它来得到父目录)
     * @return
     */
    @Override
    public List<ExecuteMessage> runUserCode(List<String> inputList, File userCodeFile) {

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        DockerClient dockerClient = createDockerClient();

        // 创建容器
        String containerId = createContainer(dockerClient, userCodeParentPath);

        System.out.println("容器 ID: " + containerId);

        dockerClient.startContainerCmd(containerId).exec();
        StatsCmd statsCmd = dockerClient.statsCmd(containerId);
        final long[] maxMemory = {0L};


        CountDownLatch latch = new CountDownLatch(5);
        ResultCallback<Statistics> statisticsResultCallback = createStatisticsCallback(maxMemory, latch);
        // 使用 CountDownLatch 同步统计数据的获取
        statsCmd.exec(statisticsResultCallback);
        try {
            latch.await(); // 等待统计数据的获取完成
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            for (String inputArgs : inputList) {
                executeUserCode(dockerClient, containerId, inputArgs, executeMessageList, maxMemory);
            }
        } finally {
            // 停止容器
            dockerClient.stopContainerCmd(containerId).exec();
            // 删除容器
            dockerClient.removeContainerCmd(containerId).exec();
            statsCmd.close();
        }

        return executeMessageList;
    }

    private DockerClient createDockerClient() {
        URI uri;
        try {
            uri = new URI("unix:///var/run/docker.sock");
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid Docker URI", e);
        }

        return DockerClientBuilder.getInstance()
                .withDockerHttpClient(new OkDockerHttpClient.Builder()
                        .dockerHost(uri)
                        .build())
                .build();
    }

    private String createContainer(DockerClient dockerClient, String userCodeParentPath) {
        HostConfig hostConfig = new HostConfig()
                .withBinds(new Bind(userCodeParentPath, new Volume("/app")))
                .withMemory(256 * 1024 * 1024L)
                .withCpuCount(1L)
                .withMemorySwap(0L);

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd("openjdk:17-alpine")
                .withHostConfig(hostConfig)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withTty(true)
                .withCmd("/bin/sh");

        CreateContainerResponse containerResponse = containerCmd.exec();
        return containerResponse.getId();
    }

    // TODO: 8/1/24 统计占用内存信息
    private ResultCallback<Statistics> createStatisticsCallback(final long[] maxMemory, final CountDownLatch latch) {
        return new ResultCallback<Statistics>() {

            @Override
            public void onNext(Statistics statistics) {
                long memory = statistics.getMemoryStats().getUsage();
                maxMemory[0] = Math.max(maxMemory[0], memory);
                System.out.println("内存占用: " + maxMemory[0]);
                latch.countDown(); // 统计完成后减少计数
            }

            @Override
            public void onStart(Closeable closeable) {}

            @Override
            public void onError(Throwable throwable) {
                System.err.println("统计错误: " + throwable.getMessage());
                throwable.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onComplete() {}

            @Override
            public void close() throws IOException {}
        };
    }

    private void executeUserCode(DockerClient dockerClient, String containerId, String inputArgs, List<ExecuteMessage> executeMessageList, final long[] maxMemory) {
        StopWatch stopWatch = new StopWatch();
        String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgs.split(" "));
        System.out.println("执行的命令: " + String.join(" ", cmdArray));

        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withCmd(cmdArray)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .exec();

        String execId = execCreateCmdResponse.getId();
        ExecuteMessage executeMessage = new ExecuteMessage();
        final String[] message = {null};
        final String[] errorMessage = {null};
        final boolean[] timeout = {true};

        ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
            @Override
            public void onComplete() {
                timeout[0] = false;
                super.onComplete();
            }

            @Override
            public void onNext(Frame frame) {
                StreamType streamType = frame.getStreamType();
                if (StreamType.STDERR.equals(streamType)) {
                    errorMessage[0] = new String(frame.getPayload());
                    System.out.println("输出错误结果: " + errorMessage[0]);
                } else {
                    message[0] = new String(frame.getPayload()).trim();
                    System.out.println("输出正确结果: " + message[0]);
                }
                super.onNext(frame);
            }
        };

        stopWatch.start();
        try {
            dockerClient.execStartCmd(execId)
                    .exec(execStartResultCallback)
                    .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            System.err.println("程序执行错误: " + e.getMessage());
        }
        stopWatch.stop();

        executeMessage.setMessage(message[0]);
        executeMessage.setErrorMessage(errorMessage[0]);
        executeMessage.setTime(stopWatch.getLastTaskTimeMillis());

        executeMessage.setMemory(maxMemory[0]);

        executeMessageList.add(executeMessage);

        System.out.println("程序执行信息: " + executeMessage);
    }
}
