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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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


    private final DockerContainerPool containerPool;




    // 通过构造器注入 Docker 容器池
    public JavaDockerCodeSandbox(DockerContainerPool containerPool) {
        this.containerPool = containerPool;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    /**
     * 重新执行代码，得到输出结果
     *
     * @param inputList    代码输入用例
     * @param userCodeFile 用户代码文件(用它来得到父目录)
     * @return
     */
//    @Override
//    public List<ExecuteMessage> runUserCode(List<String> inputList, File userCodeFile) {
//
//        List<ExecuteMessage> executeMessageList = new ArrayList<>();
//        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
//
//        // 1.创建 Docker 客户端
//        DockerClient dockerClient = createDockerClient();
//        // 2.创建容器
//        String containerId = createContainer(dockerClient, userCodeParentPath);
//
//        System.out.println("容器 ID: " + containerId);
//
//        // 3.启动容器!
//        dockerClient.startContainerCmd(containerId).exec();
//
//
//
//        final long[] maxMemory = {0L};
//
//
//        CountDownLatch latch = new CountDownLatch(5);
//
//        try {
//            for (String inputArgs : inputList) {
//                // 1. 获取容器运行时的统计信息, 用于监控容器的资源使用情况
//                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
//                ResultCallback<Statistics> statisticsResultCallback = createStatisticsCallback(maxMemory, latch);
//                statsCmd.exec(statisticsResultCallback);
//
//                // 2. 执行用户代码
//                executeUserCode(dockerClient, containerId, inputArgs, executeMessageList, maxMemory);
//
//                // 3. 等待统计完成
//                try {
//                    latch.await(); // 等待统计数据的获取完成
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    statsCmd.close(); // 停止内存统计
//                }
//            }
//        } finally {
//            // 停止并移除容器
//            dockerClient.stopContainerCmd(containerId).exec();
//            dockerClient.removeContainerCmd(containerId).exec();
//        }
//
//        return executeMessageList;
//    }

    @Override
    public List<ExecuteMessage> runUserCode(List<String> inputList, File userCodeFile) {
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        // 从容器池中获取一个可用的容器
        DockerContainer dockerContainer = containerPool.acquireContainer(userCodeParentPath);
        String containerId = dockerContainer.getContainerId();

        System.out.println("使用的容器 ID: " + containerId);

        StatsCmd statsCmd = dockerContainer.getDockerClient().statsCmd(containerId);
        final long[] maxMemory = {0L};
        CountDownLatch latch = new CountDownLatch(5);
        ResultCallback<Statistics> statisticsResultCallback = createStatisticsCallback(maxMemory, latch);


        try {

            for (String inputArgs : inputList) {
                executeUserCode(dockerContainer.getDockerClient(), containerId, inputArgs, executeMessageList, maxMemory);
            }
            statsCmd.exec(statisticsResultCallback);
            latch.await(); // 等待统计数据获取完成
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 将容器归还到池中，等待下次使用
            containerPool.releaseContainer(dockerContainer);
            statsCmd.close();
        }

        return executeMessageList;
    }

    /**
     * 创建 Docker 客户端
     *
     * @return
     */
    private DockerClient createDockerClient() {
        URI uri;
        try {
            // Docker 守护进程的位置
            uri = new URI("unix:///var/run/docker.sock");
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid Docker URI", e);
        }

        // 创建 Docker 客户端实例
        return DockerClientBuilder.getInstance()
                .withDockerHttpClient(new OkDockerHttpClient.Builder()
                        .dockerHost(uri)
                        .build())
                .build();
    }

    /**
     * 创建容器
     *
     * @param dockerClient
     * @param userCodeParentPath
     * @return
     */
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

        // 创建容器
        CreateContainerResponse containerResponse = containerCmd.exec();
        String containerId = containerResponse.getId();

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();
        System.out.println("创建并启动容器: " + containerId);

        return containerId;
    }


    /**
     * 创建统计内存回调
     * 它是要不当做参数传递, 才会生效的!!!
     *
     * @param maxMemory
     * @param latch
     * @return
     */
    private ResultCallback<Statistics> createStatisticsCallback(final long[] maxMemory, final CountDownLatch latch) {
        return new ResultCallback<Statistics>() {

            @Override
            public void onNext(Statistics statistics) {


                MemoryStatsConfig memoryStats = statistics.getMemoryStats();
                if (memoryStats != null) {
                    Long memory = memoryStats.getUsage();
                    System.out.println("memory: " + memory);
                    if (memory != null) {
                        maxMemory[0] = Math.max(maxMemory[0], memory);
                    }
                    System.out.println("内存占用: " + maxMemory[0]);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
                    Date now = new Date();
                    String time = sdf.format(now);
                    System.out.println("统计内存 - 当前时间: " + time);
                }


                latch.countDown(); // 统计完成后减少计数
            }

            @Override
            public void onStart(Closeable closeable) {
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println("统计错误: " + throwable.getMessage());
                throwable.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void close() throws IOException {
            }
        };
    }

    /**
     * 执行用户代码
     *
     * @param dockerClient
     * @param containerId
     * @param inputArgs
     * @param executeMessageList
     * @param maxMemory
     */
    private void executeUserCode(DockerClient dockerClient, String containerId, String inputArgs, List<ExecuteMessage> executeMessageList, final long[] maxMemory) {
        StopWatch stopWatch = new StopWatch();
        String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgs.split(" "));
        System.out.println("执行的命令: " + String.join(" ", cmdArray));

        // 告诉 Docker 我们需要在容器内执行什么命令
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
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
                Date now = new Date();
                String time = sdf.format(now);
                System.out.println("最终时间: " + time);
                System.out.println("内存大小为: " + maxMemory[0]);
                executeMessage.setMemory(maxMemory[0]);
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        Date now = new Date();
        String time = sdf.format(now);
        System.out.println("储存内存时 - 当前时间: " + time);
        System.out.println("内存大小: " + maxMemory[0]);
        executeMessage.setMemory(maxMemory[0]);

        executeMessageList.add(executeMessage);

        System.out.println("程序执行信息: " + executeMessage);
    }
}
