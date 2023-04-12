package io.gitee.xuchenoak.limejapidocs.parser.exception;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * 自定义异常
 *
 * @author xuchenoak
 */
public class CustomException extends RuntimeException {

    private static final Logger logger = LoggerFactory.getLogger(CustomException.class);

    private static final long serialVersionUID = 1L;

    public static CustomException instance() {
        return new CustomException();
    }

    public static CustomException instance(String msg) {
        return new CustomException(msg);
    }

    public static CustomException instance(String template, Object... v) {
        return instance(StrUtil.format(template, v));
    }

    public static CustomException instance(int code, String template, Object... v) {
        return instance(code, StrUtil.format(template, v));
    }

    public static CustomException instance(int code, String msg) {
        return new CustomException(code, msg);
    }

    public static CustomException instance(int code, String msg, Throwable cause) {
        return new CustomException(code, msg, cause);
    }

    private int code;
    private String msg;

    private CustomException() {
        super("服务器内部错误");
        this.code = 500;
        this.msg = "服务器内部错误";
    }

    private CustomException(String msg) {
        super(msg);
        this.code = 505;
        this.msg = msg;
    }

    private CustomException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    private CustomException(int code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    /**
     * 获取异常栈信息
     *
     * @param th 异常对象
     * @return 异常打印字符
     */
    public static String getExceptionStr(Throwable th) {
        if (th != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                th.printStackTrace(new PrintStream(out));
                out.flush();
                return out.toString();
            } catch (Exception e) {
                logger.error("getExceptionStr 异常", e);
            } finally {
                IoUtil.close(out);
            }
        }
        return "";
    }
}
