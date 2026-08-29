package io.gitee.sample.dto;

import io.gitee.xuchenoak.limejapidocs.parser.annotaion.ParseIgnore;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import java.util.Date;
import java.util.List;

/**
 * User domain object
 */
public class User {

    /**
     * primary key
     */
    private Long id;

    /**
     * login name
     */
    private String name;

    /**
     * display nickname
     */
    @NotBlank(message = "nickname must not be blank")
    private String nickname;

    /**
     * user age
     */
    private Integer age;

    /**
     * birthday
     */
    private Date birthday;

    /**
     * created time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * profile object
     */
    private UserProfile profile;

    /**
     * tags
     */
    private List<String> tags;

    /**
     * living addresses
     */
    private List<Address> addresses;

    /**
     * score history
     */
    private Integer[] scores;

    /**
     * granted roles
     */
    private String[] roles;

    /**
     * internal secret, must be ignored by parser
     */
    @ParseIgnore
    private String secret;
}