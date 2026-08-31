package io.gitee.sample.dto;

/**
 * Java 21 record type used to verify newer syntax handling
 *
 * @param username 用户名
 * @param age      年龄
 */
public record UserRecord(String username, Integer age) {
}