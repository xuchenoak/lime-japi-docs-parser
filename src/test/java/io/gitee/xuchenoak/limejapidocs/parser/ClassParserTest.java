package io.gitee.xuchenoak.limejapidocs.parser;

import io.gitee.xuchenoak.limejapidocs.parser.basenode.ClassNode;
import io.gitee.xuchenoak.limejapidocs.parser.basenode.FieldNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for parsing arbitrary POJOs with {@link ClassParser}.
 *
 * @author xuchenoak
 **/
public class ClassParserTest {

    private static final String FIXTURE_ROOT = new File("src/test/resources/fixtures/sample/src/main/java").getAbsolutePath();

    private static final File USER_FILE = new File(FIXTURE_ROOT, "io/gitee/sample/dto/User.java");

    private ClassNode parseFixture(String relativePath) {
        ClassParser<ClassNode> parser = new ClassParser<ClassNode>() {
        };
        parser.addRootPaths(java.util.Collections.singleton(FIXTURE_ROOT));
        return parser.parse(new File(FIXTURE_ROOT, relativePath));
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
    public void parse_instancesAreIsolated() {
        // 每次解析使用独立实例（独立解析会话）→ 模板缓存互不共享，各自基于最新源码
        ClassNode first = parseFixture("io/gitee/sample/dto/User.java");
        ClassNode second = parseFixture("io/gitee/sample/dto/User.java");
        assertNotSame(first.getFieldNodeByName("profile").getValueTypeClassNode(),
                second.getFieldNodeByName("profile").getValueTypeClassNode());
    }

    @Test
    public void parse_windowSharingAndClearWithinSameInstance() {
        // 同一解析会话可由多个解析器共享：引用解析命中会话内模板缓存，各实例单次解析互不干扰
        ParseSession session = new ParseSession();
        session.addRootPath(FIXTURE_ROOT);
        ClassParser<ClassNode> parserA = new ClassParser<ClassNode>(session) {
        };
        ClassParser<ClassNode> parserB = new ClassParser<ClassNode>(session) {
        };

        ClassNode first = parserA.parse(USER_FILE);
        ClassNode second = parserB.parse(USER_FILE);
        assertNotSame(first, second);
        ClassNode firstProfile = first.getFieldNodeByName("profile").getValueTypeClassNode();
        ClassNode secondProfile = second.getFieldNodeByName("profile").getValueTypeClassNode();
        assertSame(firstProfile, secondProfile);

        // 清会话缓存后，新实例引用模板重建
        session.clearCache();
        ClassParser<ClassNode> parserC = new ClassParser<ClassNode>(session) {
        };
        ClassNode third = parserC.parse(USER_FILE);
        assertNotNull(third);
        assertNotSame(firstProfile, third.getFieldNodeByName("profile").getValueTypeClassNode());
    }

    @Test
    public void parse_instanceIsSingleUse() {
        ClassParser<ClassNode> parser = new ClassParser<ClassNode>() {
        };
        parser.addRootPath(FIXTURE_ROOT);
        parser.parse(USER_FILE);
        assertThrows(IllegalStateException.class, () -> parser.parse(USER_FILE));
    }

    @Test
    public void concurrent_instancesAreIsolated() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ClassNode> user = pool.submit(() -> {
                ClassParser<ClassNode> parser = new ClassParser<ClassNode>() {
                };
                parser.addRootPath(FIXTURE_ROOT);
                return parser.parse(USER_FILE);
            });
            Future<ClassNode> outer = pool.submit(() -> {
                ClassParser<ClassNode> parser = new ClassParser<ClassNode>() {
                };
                parser.addRootPaths(java.util.Collections.singleton(FIXTURE_ROOT));
                return parser.parse(new File(FIXTURE_ROOT, "io/gitee/sample/dto/Outer.java"));
            });
            ClassNode userNode = user.get();
            ClassNode outerNode = outer.get();
            assertNotNull(userNode.getFieldNodeByName("profile"));
            assertNotNull(outerNode.getFieldNodeByName("inner"));
            // 两线程独立实例（独立会话），解析结果字段互不串扰
            assertEquals("innerName", outerNode.getFieldNodeByName("inner").getValueTypeClassNode()
                    .getFieldNodeByName("innerName").getName());
        } finally {
            pool.shutdownNow();
        }
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