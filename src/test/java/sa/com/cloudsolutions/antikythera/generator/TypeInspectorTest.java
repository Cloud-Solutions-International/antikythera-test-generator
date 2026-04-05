package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeInspectorTest {

    @Test
    void rawSimpleNameUsesIdentifierForQualifiedClassOrInterfaceType() {
        Type type = StaticJavaParser.parseType("java.util.Set<java.lang.String>");
        assertEquals("Set", TypeInspector.rawSimpleName(type));
    }

    @Test
    void rawSimpleNameKeepsPrimitiveFallback() {
        Type type = StaticJavaParser.parseType("long");
        assertEquals("long", TypeInspector.rawSimpleName(type));
    }

    @Test
    void isCollectionParameterTypeSupportsQualifiedSet() {
        Type type = StaticJavaParser.parseType("java.util.Set<java.lang.String>");
        assertTrue(TypeInspector.isCollectionParameterType(type));
    }

    @Test
    void isCollectionOrMapFieldTypeSupportsQualifiedList() {
        Type type = StaticJavaParser.parseType("java.util.List<java.lang.String>");
        assertTrue(TypeInspector.isCollectionOrMapFieldType(type));
    }

    @Test
    void isMapTypeSupportsQualifiedMap() {
        Type type = StaticJavaParser.parseType("java.util.Map<java.lang.String, java.lang.Integer>");
        assertTrue(TypeInspector.isMapType(type));
    }
}

