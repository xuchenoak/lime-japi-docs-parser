package io.gitee.xuchenoak.limejapidocs.parser;

import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldDataNode;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldInfo;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snapshot-ish tests for {@link StringUtil#toFormatJsonStr}.
 *
 * @author xuchenoak
 **/
public class StringUtilTest {

    private static FieldInfo lastValueInfo(String comment, String name, String type, String defaultValue) {
        FieldInfo fieldInfo = new FieldInfo(comment, name, type, null);
        fieldInfo.toLastValue();
        fieldInfo.setDefaultValue(defaultValue);
        return fieldInfo;
    }

    private static FieldDataNode beanNode(FieldInfo... fieldInfos) {
        FieldDataNode node = new FieldDataNode();
        List<FieldInfo> list = new ArrayList<>();
        for (FieldInfo fieldInfo : fieldInfos) {
            list.add(fieldInfo);
        }
        node.setFieldInfoList(list);
        return node;
    }

    @Test
    public void toFormatJsonStr_lastValueRoot() {
        FieldDataNode node = new FieldDataNode();
        node.setLastValue(true);
        node.setLastValueType("String");
        assertEquals("String", StringUtil.toFormatJsonStr(node, 0, 2, false, false, false, true, false));
    }

    @Test
    public void toFormatJsonStr_flatBean() {
        FieldDataNode node = beanNode(
                lastValueInfo("login name", "username", "String", "\"zhuge\""),
                lastValueInfo("user age", "age", "Integer", "18")
        );
        String json = StringUtil.toFormatJsonStr(node, 0, 2, true, true, false, true, true);
        assertNotNull(json);
        assertTrue(json.contains("\"username\": \"zhuge\""));
        assertTrue(json.contains("\"age\": 18"));
        assertTrue(json.contains("// login name | String"));
        assertTrue(json.contains("// user age | Integer"));
    }

    @Test
    public void toFormatJsonStr_noCommentNoType() {
        FieldDataNode node = beanNode(
                lastValueInfo("login name", "username", "String", "\"zhuge\"")
        );
        String json = StringUtil.toFormatJsonStr(node, 0, 2, false, false, false, true, true);
        assertTrue(json.contains("\"username\": \"zhuge\""));
        assertFalse(json.contains("//"));
    }

    @Test
    public void toFormatJsonStr_nonJsonKeys() {
        FieldDataNode node = beanNode(
                lastValueInfo("login name", "username", "String", "\"zhuge\"")
        );
        String text = StringUtil.toFormatJsonStr(node, 0, 2, false, false, false, false, true);
        assertTrue(text.contains("username:"));
        assertFalse(text.contains("\"username\""));
    }

    @Test
    public void toFormatJsonStr_nestedObject() {
        FieldInfo profile = new FieldInfo("profile detail", "profile", "UserProfile", null);
        profile.setValueFieldData(beanNode(lastValueInfo("level number", "level", "Integer", "3")));
        FieldDataNode node = beanNode(
                lastValueInfo("login name", "username", "String", "\"zhuge\""),
                profile
        );
        String json = StringUtil.toFormatJsonStr(node, 0, 2, true, true, false, true, true);
        assertTrue(json.contains("\"username\""));
        assertTrue(json.contains("\"profile\""));
        assertTrue(json.contains("\"level\": 3"));
    }

    @Test
    public void toFormatJsonStr_nullOutputForNullNode() {
        assertNull(StringUtil.toFormatJsonStr(null, 0, 2, false, false, false, true, false));
    }

    @Test
    public void toFormatJsonStr_addDefaultValueFalseAppendsNullLiteral() {
        FieldDataNode node = beanNode(
                lastValueInfo("login name", "username", "String", null)
        );
        String json = StringUtil.toFormatJsonStr(node, 0, 2, false, false, false, true, false);
        assertTrue(json.contains("null"));
    }

    @Test
    public void getRetract_returnsExpectedSpaces() {
        assertEquals("  ", StringUtil.getRetract(2));
        assertEquals("", StringUtil.getRetract(0));
    }
}