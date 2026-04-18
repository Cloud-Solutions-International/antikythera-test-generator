package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify the hasPublicStaticMain method correctly handles both
 * array and varargs forms of main method parameters.
 */
class TargetClassifierMainTest {

    private static boolean invokeHasPublicStaticMain(String mainSignature) throws Exception {
        String code = String.format("""
            public class MainClass {
                %s {
                    System.out.println("Hello World");
                }
            }
            """, mainSignature);

        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cdecl = cu.getClassByName("MainClass").orElseThrow();

        Method method = TargetClassifier.class.getDeclaredMethod("hasPublicStaticMain",
                ClassOrInterfaceDeclaration.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, cdecl);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "public static void main(String[] args)",
            "public static void main(String... args)",
            "public static void main(java.lang.String[] args)",
            "public static void main(java.lang.String... args)"
    })
    void acceptsValidMainSignatures(String mainSignature) throws Exception {
        assertTrue(invokeHasPublicStaticMain(mainSignature));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "public static int main(String[] args)",
            "public static void main(int[] args)",
            "public void main(String[] args)",
            "private static void main(String[] args)"
    })
    void rejectsInvalidMainSignatures(String mainSignature) throws Exception {
        // Note: "public static int main" uses a body with return - handled by override below
        String code;
        if (mainSignature.contains("int main")) {
            code = String.format("""
                public class MainClass {
                    %s {
                        return 0;
                    }
                }
                """, mainSignature);
        } else {
            code = String.format("""
                public class MainClass {
                    %s {
                        System.out.println("Hello World");
                    }
                }
                """, mainSignature);
        }

        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cdecl = cu.getClassByName("MainClass").orElseThrow();

        Method method = TargetClassifier.class.getDeclaredMethod("hasPublicStaticMain",
                ClassOrInterfaceDeclaration.class);
        method.setAccessible(true);
        assertFalse((Boolean) method.invoke(null, cdecl));
    }
}
