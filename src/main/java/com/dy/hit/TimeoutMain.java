package com.dy.hit;

/**
 * @Author: dy
 * @Date: 2024/7/24 11:07
 * @Description:
 */
public class TimeoutMain {
    public static void main(String[] args) {
        long sleepTime = 1000 * 60 * 20;
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
