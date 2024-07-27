package com.dy.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.okhttp.OkDockerHttpClient;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @Author: dy
 * @Date: 2024/7/27 10:41
 * @Description:
 */
public class DockerDemo {
    public static void main(String[] args) throws URISyntaxException, InterruptedException {

        URI uri = new URI("unix:///var/run/docker.sock");

        //  todo 手动配置 Docker HTTP Client
        DockerClient dockerClient = DockerClientBuilder.getInstance()
                .withDockerHttpClient(new OkDockerHttpClient.Builder()
                        .dockerHost(uri) // 指定 Docker 主机地址
                        .build())
                .build();

        PingCmd pingCmd = dockerClient.pingCmd();
        pingCmd.exec();

        System.out.println("Hello world!");


        String image = "nginx:latest";

        //  拉取镜像
/*        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
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
        System.out.println("下载完成");*/

        //  创建容器
       /* CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd
                .withCmd("echo", "Hello Docker")
                .exec();
        System.out.println(createContainerResponse);*/

        //  获取容器zhuangt
       /* ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containerList = listContainersCmd.withShowAll(true).exec();

        for (Container container : containerList) {
            System.out.println(container);
        }*/

        //  查看日志




    }
}
