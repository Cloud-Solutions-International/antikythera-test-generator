package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify the hasPublicStaticMain method correctly handles both
 * array and varargs forms of main method parameters
 */
class TargetClassifierMainTest {

    @Test
    void testHasPublicStaticMainWithStringArray() throws Exception {
        String code = """
            public class MainClass {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
            """;
        
        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cdecl = cu.getClassByName("MainClass").orElseThrow();
        
        Method method = TargetClassifier.class.getDeclaredMethod("hasPublicStaticMain", 
                ClassOrInterfaceDeclaration.class);
        method.setAccessible(true);
        
        assertTrue((Boolean) method.invoke(null, cdecl));
    }

    @Test
    void testHasPublicStaticMainWithVarargs() throws Exception {
        String code = """
            public class MainClass {
                public static void main(String... args) {
                    System.out.println("Hello World");
                }
            }
            """;
        
        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cdecl = cu.getClassByName("MainClass").orElseThrow();
        
        Method method = TargetClassifier.class.getDeclaredMethod("hasPublicStaticMain", 
                ClassOrInterfaceDeclaration.class);
        method.setAccessible(true);
        
        assertTrue((Boolean) method.invoke(null, cdecl));
    }

    @Test
    void testHasPublicStaticMainWithFullyQualifiedStringArray() throws Exception {
        String code = """
            public class MainClass {
                public static void main(java.lang.String[] args) {
                    System.out.println("Hello World");
                }
            }
            """;
        
        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cdecl = cu.getClassByName("MainClass").orElseThrow();
        
        Method method = TargetClassifier.class.getDeclaredMethod("hasPublicStaticMain", 
                ClassOrInterfaceDeclaration.class);
        method.setAccessible(true);
        
        assertTrue((Boolean) method.invoke(null, cdecl));
    }

    @Test
    void testHasPublicStaticMainWithFullyQualifiedVarargs() throws Exception {
        String code = """
            public class MainClass {
                public static void main(java.lang.String... args) {
                    System.out.println("Hello World");
                }
            }
            """;
        
        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cdecl = cu.getClassByName("MainClass").orElseThrow();
        
        Method method = TargetClassifier.class.getDeclaredMethod("hasPublicStaticMain", 
                ClassOrInterfaceDeclaration.class);
        method.setAccessible(true);
        
        assertTrue((Boolean) method.invoke(null, cdecl));
    }

    @Test
    void testHasPublicStaticMainRejectsNonVoidReturn() throws Exception {
        String code = """
            public class MainClass {
                public static int main(String[] args) {
                    return 0;
                }
            }
            """;
        
        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cdecl = cu.getClassByName("MainClass").orElseThrow();
        
        Method method = TargetClassifier.class.getDeclaredMethod("hasPublicStaticMain", 
                ClassOrInterfaceDeclaration.class);
        method.setAccessible(true);
        
        assertFalse((Boolean) method.invoke(null, cdecl));
    }

    @Test
    void testHasPublicStaticMainRejectsWrongParameterType() throws Exception {
        String code = """
            public class MainClass {
                public static void main(int[] args) {
                    System.out.println("Hello World");
                }
            }
            """;
        
        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cdecl = cu.getClassByName("MainClass").orElseThrow();
        
        Method method = TargetClassifier.class.getDeclaredMethod("hasPublicStaticMain", 
                ClassOrInterfaceDeclaration.class);
        method.setAccessible(true);
        
        assertFalse((Boolean) method.invoke(null, cdecl));
    }

    @Test
    void testHasPublicStaticMainRejectsNonStaticMethod() throws Exception {
        String code = """
            public class MainClass {
                public void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
            """;
        
        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cdecl = cu.getClassByName("MainClass").orElseThrow();
        
        Method method = TargetClassifier.class.getDeclaredMethod("hasPublicStaticMain", 
                ClassOrInterfaceDeclaration.class);
        method.setAccessible(true);
        
        assertFalse((Boolean) method.invoke(null, cdecl));
    }

    @Test
    void testHasPublicStaticMainRejectsNonPublicMethod() throws Exception {
        String code = """
            public class MainClass {
                private static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
            """;
        
        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cdecl = cu.getClassByName("MainClass").orElseThrow();
        
        Method method = TargetClassifier.class.getDeclaredMethod("hasPublicStaticMain", 
                ClassOrInterfaceDeclaration.class);
        method.setAccessible(true);
        
        assertFalse((Boolean) method.invoke(null, cdecl));
    }
}
