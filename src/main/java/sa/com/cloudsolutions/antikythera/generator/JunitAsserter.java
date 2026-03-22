package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

public class JunitAsserter extends Asserter {
    @Override
    public Expression assertNotNull(String variable) {
        MethodCallExpr aNotNull = new MethodCallExpr("assertNotNull");
        aNotNull.addArgument(variable);
        return aNotNull;
    }

    @Override
    public Expression assertNull(String variable) {
        MethodCallExpr aNotNull = new MethodCallExpr("assertNull");
        aNotNull.addArgument(variable);
        return aNotNull;
    }

    @Override
    public void setupImports(CompilationUnit gen) {
        gen.addImport("org.junit.jupiter.api.Test");
        gen.addImport("org.junit.jupiter.api.Assertions", true, true);
    }

    @Override
    public Expression assertEquals(String lhs, String rhs) {
        MethodCallExpr assertEquals = new MethodCallExpr( "assertEquals");
        assertEquals.addArgument(lhs);
        assertEquals.addArgument(rhs);
        return assertEquals;
    }

    @Override
    public Expression assertThrows(String invocation, MethodResponse response) {
        MethodCallExpr assertThrows = new MethodCallExpr("assertThrows");
        Throwable ex = response.getException();
        String exceptionClass;
        if (ex == null) {
            exceptionClass = RuntimeException.class.getName();
        } else if (ex.getCause() != null) {
            Throwable cause = ex.getCause();
            // Unwrap InvocationTargetException to expose the real underlying exception
            while (cause instanceof java.lang.reflect.InvocationTargetException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            String causeClass = cause.getClass().getName();
            exceptionClass = causeClass.startsWith("sa.com.cloudsolutions.antikythera") ? Exception.class.getName() : causeClass;
        } else {
            String rawClass = ex.getClass().getName();
            exceptionClass = rawClass.startsWith("sa.com.cloudsolutions.antikythera") ? Exception.class.getName() : rawClass;
        }
        assertThrows.addArgument(exceptionClass + ".class");
        assertThrows.addArgument(String.format("() -> %s", invocation.replace(';', ' ')));
        return assertThrows;
    }

    @Override
    public Expression assertDoesNotThrow(String invocation) {
        MethodCallExpr assertDoesNotThrow = new MethodCallExpr("assertDoesNotThrow");
        assertDoesNotThrow.addArgument(String.format("() -> %s", invocation.replace(';', ' ')));
        return assertDoesNotThrow;
    }

    @Override
    public Expression assertOutput(String expected) {
        MethodCallExpr assertEquals = new MethodCallExpr("assertEquals");
        assertEquals.addArgument("\"" + expected.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"");
        assertEquals.addArgument("outputStream.toString().trim()");
        return assertEquals;
    }
}
