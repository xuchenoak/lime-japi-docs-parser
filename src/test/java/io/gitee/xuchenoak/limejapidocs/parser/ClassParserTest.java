package io.gitee.xuchenoak.limejapidocs.parser;

import io.gitee.xuchenoak.limejapidocs.parser.basenode.ClassNode;
import io.gitee.xuchenoak.limejapidocs.parser.basenode.FieldNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for parsing arbitrary POJOs with {@link ClassParser}.
 *
 * @author xuchenoak
 **/
public class ClassParserTest {

    private static final String FIXTURE_ROOT = new File("src/test/resources/fixtures/sample/src/main/java").getAbsolutePath();

    private ClassNode parseFixture(String relativePath) {
        ClassParser.addRootPath(FIXTURE_ROOT);
        return new ClassParser<ClassNode>() {
        }.parse(new File(FIXTURE_ROOT, relativePath));
    }

    @Test
    public void parse_simplePojo() {
        ClassNode node = parseFixture("io/gitee/sample/dto/UserProfile.java");
        assertNotNull(node);
        assertEquals("UserProfile", node.getName());
        assertEquals("io.gitee.sample.dto.UserProfile", node.getFullName());
        assertNotNull(node.getFieldNodeByName("bio"));
        assertEquals("String", node.getFieldNodeByName("bio").getValueTypeClassNode().getName());
        assertNotNull(node.getFieldNodeByName("level"));
        assertEquals("Integer", node.getFieldNodeByName("level").getValueTypeClassNode().getName());
    }

    @Test
    public void parse_markParseIgnoreField() {
        ClassNode node = parseFixture("io/gitee/sample/dto/User.java");
        FieldNode secret = node.getFieldNodeByName("secret");
        assertNotNull(secret);
        assertTrue(secret.getIgnore());
    }

    @Test
    public void parse_arrayAndGenericList() {
        ClassNode node = parseFixture("io/gitee/sample/dto/User.java");
        FieldNode tags = node.getFieldNodeByName("tags");
        assertTrue(tags.getValueTypeClassNode().isArray());
        assertEquals("List", tags.getValueTypeClassNode().getName());
        assertEquals("String", tags.getValueTypeClassNode().getGenericityNodeList().get(0).getName());

        FieldNode scores = node.getFieldNodeByName("scores");
        assertTrue(scores.getValueTypeClassNode().isArray());
        assertEquals("Integer", scores.getValueTypeClassNode().getGenericityNodeList().get(0).getName());
    }

    @Test
    public void parse_customObjectTypeResolved() {
        ClassNode node = parseFixture("io/gitee/sample/dto/User.java");
        FieldNode addresses = node.getFieldNodeByName("addresses");
        ClassNode addressType = addresses.getValueTypeClassNode().getGenericityNodeList().get(0);
        assertEquals("Address", addressType.getName());
        assertNotNull(addressType.getFieldNodeByName("city"));
        assertNotNull(addressType.getFieldNodeByName("street"));
    }

    @Test
    public void parse_selfReferenceTruncated() {
        ClassNode node = parseFixture("io/gitee/sample/dto/Address.java");
        FieldNode children = node.getFieldNodeByName("children");
        assertNotNull(children.getValueTypeClassNode());
        assertTrue(children.getValueTypeClassNode().isArray());
        ClassNode addressType = children.getValueTypeClassNode().getGenericityNodeList().get(0);
        assertEquals("Address", addressType.getName());

        FieldNode nestedChildren = addressType.getFieldNodeByName("children");
        assertNotNull(nestedChildren);
        ClassNode nestedChildType = nestedChildren.getValueTypeClassNode().getGenericityNodeList().get(0);
        assertEquals("Object", nestedChildType.getName());
        assertEquals("java.lang.Object", nestedChildType.getFullName());
    }

    @Test
    public void parse_extendsNodeResolved() {
        ClassNode node = parseFixture("io/gitee/sample/dto/GenericParam.java");
        assertNotNull(node.getExtendsNode());
        assertEquals("PageParam", node.getExtendsNode().getName());
        assertNotNull(node.getExtendsNode().getFieldNodeByName("pageNo"));
        assertNotNull(node.getExtendsNode().getFieldNodeByName("pageSize"));
    }

    @Test
    public void parse_injectFieldsPrefersSubclass() {
        ClassNode node = parseFixture("io/gitee/sample/dto/GenericParam.java");
        java.util.List<FieldNode> fields = new java.util.ArrayList<>();
        node.injectFieldNodeListAndExtends(fields);
        assertTrue(fields.stream().anyMatch(f -> "sort".equals(f.getName())));
        assertTrue(fields.stream().anyMatch(f -> "pageNo".equals(f.getName())));
        assertFalse(fields.stream().filter(f -> "pageNo".equals(f.getName())).count() > 1);
    }

    @Test
    public void parse_missingFileReturnsNull() {
        assertNull(parseFixture("io/gitee/sample/dto/NotExist.java"));
    }

    @Test
    public void parse_reusesCachedTemplateForRepeatedReferences() {
        ClassNode first = parseFixture("io/gitee/sample/dto/User.java");
        ClassNode second = parseFixture("io/gitee/sample/dto/User.java");
        ClassNode firstProfile = first.getFieldNodeByName("profile").getValueTypeClassNode();
        ClassNode secondProfile = second.getFieldNodeByName("profile").getValueTypeClassNode();
        assertSame(firstProfile, secondProfile);
    }

    @Test
    public void clearCache_releasesTemplatesAndKeepsWindowSharing() {
        ClassNode before = parseFixture("io/gitee/sample/dto/User.java");
        ClassNode beforeProfile = before.getFieldNodeByName("profile").getValueTypeClassNode();

        ClassParser.clearCache();

        ClassNode after = parseFixture("io/gitee/sample/dto/User.java");
        ClassNode afterProfile = after.getFieldNodeByName("profile").getValueTypeClassNode();
        assertNotSame(beforeProfile, afterProfile);

        // 清理后同一解析窗口内共享缓存仍然有效
        ClassParser.clearCache();
        ClassNode u1 = parseFixture("io/gitee/sample/dto/User.java");
        ClassNode u2 = parseFixture("io/gitee/sample/dto/User.java");
        assertSame(u1.getFieldNodeByName("profile").getValueTypeClassNode(),
                u2.getFieldNodeByName("profile").getValueTypeClassNode());
    }

    @Test
    public void parse_recordSyntaxHandledByJavaParser() throws Exception {
        File file = new File(FIXTURE_ROOT, "io/gitee/sample/dto/UserRecord.java");
        String source = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        CompilationUnit cu = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25))
                .parse(source).getResult().orElse(null);
        assertNotNull(cu);
        assertTrue(cu.getRecordByName("UserRecord").isPresent());
    }

    @Test
    public void parse_recordFileDirectShowsComponents() {
        ClassNode node = parseFixture("io/gitee/sample/dto/UserRecord.java");
        assertNotNull(node);
        assertEquals("UserRecord", node.getName());
        assertEquals("io.gitee.sample.dto.UserRecord", node.getFullName());
        assertEquals("String", node.getFieldNodeByName("username").getValueTypeClassNode().getName());
        assertEquals("Integer", node.getFieldNodeByName("age").getValueTypeClassNode().getName());
    }

    @Test
    public void parse_nestedTypesMounted() {
        ClassNode node = parseFixture("io/gitee/sample/dto/Outer.java");
        assertNotNull(node);
        assertEquals("Outer", node.getName());
        assertTrue(node.getNestedClassNodeList().size() == 3);

        ClassNode inner = node.getNestedClassNodeList().get(0);
        assertEquals("Inner", inner.getName());
        assertEquals("io.gitee.sample.dto.Outer.Inner", inner.getFullName());
        assertEquals("String", inner.getFieldNodeByName("innerName").getValueTypeClassNode().getName());

        ClassNode nested = node.getNestedClassNodeList().get(1);
        assertEquals("Nested", nested.getName());
        assertEquals("Integer", nested.getFieldNodeByName("code").getValueTypeClassNode().getName());

        ClassNode nestedRec = node.getNestedClassNodeList().get(2);
        assertEquals("NestedRec", nestedRec.getName());
        assertEquals("String", nestedRec.getFieldNodeByName("key").getValueTypeClassNode().getName());
        assertEquals("Long", nestedRec.getFieldNodeByName("value").getValueTypeClassNode().getName());
    }

    @Test
    public void parse_innerFieldReferencedBySimpleName() {
        ClassNode node = parseFixture("io/gitee/sample/dto/Outer.java");
        ClassNode innerType = node.getFieldNodeByName("inner").getValueTypeClassNode();
        assertEquals("Inner", innerType.getName());
        assertNotNull(innerType.getFieldNodeByName("innerName"));
    }
}