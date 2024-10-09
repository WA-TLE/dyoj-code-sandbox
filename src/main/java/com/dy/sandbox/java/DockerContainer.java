package com.dy.sandbox.java;

import com.github.dockerjava.api.DockerClient;

public class DockerContainer {
    private Integer id;
    private final DockerClient dockerClient;
    private final String containerId;
    private  String userCodePath;

    public DockerContainer(DockerClient dockerClient, String containerId, String userCodePath) {
        this.dockerClient = dockerClient;
        this.containerId = containerId;
        this.userCodePath = userCodePath;
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public String getContainerId() {
        return containerId;
    }

    public Integer getId() {
        return id;
    }

    public String getUserCodePath() {
        return userCodePath;
    }
}
