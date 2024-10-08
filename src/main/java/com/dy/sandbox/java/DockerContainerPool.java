package com.dy.sandbox.java;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class DockerContainerPool {

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
            String containerId = createContainer(dockerClient, "/app");
            containerPool.offer(new DockerContainer(dockerClient, containerId));
        }
    }

    public static DockerContainerPool getInstance() {
        if (dockerContainerPool == null) {
            dockerContainerPool = new DockerContainerPool();
        }
        return dockerContainerPool;
    }

    // 获取容器
    public DockerContainer acquireContainer(String userCodeParentPath) {
        DockerContainer container = null;
        try {
            container = containerPool.take();
            System.out.println("从池中获取容器: " + container.getContainerId());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return container;
    }

    // 释放容器
    public void releaseContainer(DockerContainer container) {
        try {
            container.getDockerClient().stopContainerCmd(container.getContainerId()).exec();
            containerPool.offer(container);
            System.out.println("将容器归还到池中: " + container.getContainerId());
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
        String containerId = containerResponse.getId();

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();
        System.out.println("创建并启动容器: " + containerId);

        return containerId;
    }

}
