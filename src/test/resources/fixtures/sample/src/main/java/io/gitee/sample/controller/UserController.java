package io.gitee.sample.controller;

import io.gitee.sample.dto.GenericParam;
import io.gitee.sample.dto.SelfRef;
import io.gitee.sample.dto.User;
import io.gitee.sample.dto.UserQuery;
import io.gitee.sample.dto.UserRecord;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.Validated;
import java.util.List;

/**
 * User management controller
 *
 * @author xuchenoak
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    /**
     * Get user by id
     *
     * @param id user identity
     */
    @GetMapping("/info")
    public User getById(@RequestParam("id") Long id) {
        return null;
    }

    /**
     * Query users with form params
     *
     * @param query query filters
     */
    @GetMapping(value = "/list")
    public List<User> list(UserQuery query) {
        return null;
    }

    /**
     * Create a new user
     */
    @PostMapping(value = "/create", consumes = "application/json")
    public User create(@RequestBody User user) {
        return null;
    }

    /**
     * Remove users by ids
     *
     * @param ids identifiers to delete
     */
    @DeleteMapping(value = {"/remove", "/delete"})
    public Boolean remove(@RequestParam List<Long> ids) {
        return null;
    }

    /**
     * Patch a user
     */
    @PatchMapping(path = "/patch")
    public void patch(@RequestBody @Valid User user) {
    }

    /**
     * Update a user with group validation
     */
    @PutMapping("/update")
    public User update(@RequestBody @Validated User user) {
        return null;
    }

    /**
     * Mapping with explicit request methods
     */
    @RequestMapping(value = "/both", method = {RequestMethod.GET, RequestMethod.POST})
    public User both(@RequestParam String name) {
        return null;
    }

    /**
     * Return a self referencing object
     */
    @GetMapping("/self")
    public SelfRef self() {
        return null;
    }

    /**
     * Return a generic wrapped object
     */
    @GetMapping("/generic")
    public GenericParam generic() {
        return null;
    }

    /**
     * Accept an object that extends a base param
     */
    @PostMapping("/form")
    public User form(GenericParam param) {
        return null;
    }

    /**
     * 中文注释接口
     */
    @GetMapping("/cn")
    public String cn() {
        return null;
    }

    /**
     * Return a Java 21 record type
     */
    @GetMapping("/record")
    public UserRecord recordDemo() {
        return null;
    }

    /**
     * Plain helper not exposed as interface
     */
    public String helper() {
        return "helper";
    }
}