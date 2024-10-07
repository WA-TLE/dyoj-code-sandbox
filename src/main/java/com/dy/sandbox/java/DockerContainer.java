package com.dy.sandbox.java;

import com.github.dockerjava.api.DockerClient;

public class DockerContainer {
    private final DockerClient dockerClient;
    private final String containerId;

    public DockerContainer(DockerClient dockerClient, String containerId) {
        this.dockerClient = dockerClient;
        this.containerId = containerId;
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public String getContainerId() {
        return containerId;
    }
}
