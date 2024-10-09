package com.dy.sandbox.java;

import cn.hutool.core.io.FileUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class DockerContainerPool {

    public static final String GLOBAL_CODE_PATH = "tempcode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private final DockerClient dockerClient;
    private final BlockingQueue<DockerContainer> containerPool;
    private final int poolSize = 2;  // 池大小
    // 静态对象
    private static DockerContainerPool dockerContainerPool;

    // 构造方法私有化
    private DockerContainerPool() {
        this.dockerClient = createDockerClient();
        this.containerPool = new LinkedBlockingQueue<>(poolSize);
        
        // 初始化时创建指定数量的容器
        for (int i = 0; i < poolSize; i++) {
            String userCodePath = saveUserCode();
            String containerId = createContainer(dockerClient, userCodePath);
            containerPool.offer(new DockerContainer(dockerClient, containerId, userCodePath));
        }
    }

    public static DockerContainerPool getInstance() {
        if (dockerContainerPool == null) {
            System.out.println("创建 Docker 池对象");
            dockerContainerPool = new DockerContainerPool();
        }
        return dockerContainerPool;
    }

    // 获取容器
    public DockerContainer acquireContainer() {
        DockerContainer container = null;
        try {


            container = containerPool.take();
            String containerId = container.getContainerId();
            System.out.println("从池中获取容器: " + containerId);

            // 检查容器状态，确保容器在运行
            InspectContainerResponse containerInfo = container.getDockerClient().inspectContainerCmd(containerId).exec();
            if (Boolean.FALSE.equals(containerInfo.getState().getRunning())) {
                // 如果未运行，则启动容器
                System.out.println("容器未运行，启动容器: " + containerId);
                container.getDockerClient().startContainerCmd(containerId).exec();
            }


        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return container;
    }

    // 释放容器
    public void releaseContainer(DockerContainer container) {
        try {
            String containerId = container.getContainerId();

            // 检查容器是否运行中，只有运行中的容器才需要停止
            InspectContainerResponse containerInfo = container.getDockerClient().inspectContainerCmd(containerId).exec();
            if (containerInfo.getState().getRunning()) {
                container.getDockerClient().stopContainerCmd(containerId).exec();
                System.out.println("停止容器并归还到池中: " + containerId);
            }

            // 将容器归还到池中
            containerPool.offer(container);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 创建 Docker 客户端
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

    // 创建容器（与之前类似）
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
        return containerResponse.getId();
    }

    public String  saveUserCode() {
        // 获取当前 Java 进程的工作目录
        String userDir = System.getProperty("user.dir");
        log.info("userDir: {}", userDir); //  D:\WorkSpace\OJ\dyoj-code-sandbox

        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_PATH;

        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 每个提交创建一个不同的文件夹(防止文件名冲突)
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();



        //  使用 Hutool 工具类保存用户代码
        return userCodeParentPath;
    }

}
