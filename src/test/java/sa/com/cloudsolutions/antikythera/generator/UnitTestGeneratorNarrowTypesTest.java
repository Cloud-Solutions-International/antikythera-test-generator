package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify that narrow numeric and char types are properly casted
 * in defaultExpressionForSimpleType and valueFieldInitializerLiteral methods
 */
class UnitTestGeneratorNarrowTypesTest {

    private UnitTestGenerator generator;
    
    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
    }
    
    @BeforeEach
    void setUp() {
        generator = new UnitTestGenerator(StaticJavaParser.parse("public class TestClass {}"), GeneratorSeams.defaults());
    }

    @Test
    void testDefaultExpressionForSimpleType_byteTypes() throws Exception {
        Method method = UnitTestGenerator.class.getDeclaredMethod("defaultExpressionForSimpleType", Type.class);
        method.setAccessible(true);
        
        // Test primitive byte
        Type primitiveByteType = new PrimitiveType(PrimitiveType.Primitive.BYTE);
        Expression byteExpr = (Expression) method.invoke(generator, primitiveByteType);
        assertEquals("(byte) 0", byteExpr.toString(), "Primitive byte should emit (byte) 0");
        
        // Test wrapper Byte
        Type wrapperByteType = StaticJavaParser.parseType("Byte");
        Expression wrapperByteExpr = (Expression) method.invoke(generator, wrapperByteType);
        assertEquals("(byte) 0", wrapperByteExpr.toString(), "Wrapper Byte should emit (byte) 0");
    }

    @Test
    void testDefaultExpressionForSimpleType_shortTypes() throws Exception {
        Method method = UnitTestGenerator.class.getDeclaredMethod("defaultExpressionForSimpleType", Type.class);
        method.setAccessible(true);
        
        // Test primitive short
        Type primitiveShortType = new PrimitiveType(PrimitiveType.Primitive.SHORT);
        Expression shortExpr = (Expression) method.invoke(generator, primitiveShortType);
        assertEquals("(short) 0", shortExpr.toString(), "Primitive short should emit (short) 0");
        
        // Test wrapper Short
        Type wrapperShortType = StaticJavaParser.parseType("Short");
        Expression wrapperShortExpr = (Expression) method.invoke(generator, wrapperShortType);
        assertEquals("(short) 0", wrapperShortExpr.toString(), "Wrapper Short should emit (short) 0");
    }

    @Test
    void testDefaultExpressionForSimpleType_charTypes() throws Exception {
        Method method = UnitTestGenerator.class.getDeclaredMethod("defaultExpressionForSimpleType", Type.class);
        method.setAccessible(true);
        
        // Test primitive char
        Type primitiveCharType = new PrimitiveType(PrimitiveType.Primitive.CHAR);
        Expression charExpr = (Expression) method.invoke(generator, primitiveCharType);
        assertEquals("'\\u0000'", charExpr.toString(), "Primitive char should emit '\\u0000'");
        
        // Test wrapper Character
        Type wrapperCharType = StaticJavaParser.parseType("Character");
        Expression wrapperCharExpr = (Expression) method.invoke(generator, wrapperCharType);
        assertEquals("'\\u0000'", wrapperCharExpr.toString(), "Wrapper Character should emit '\\u0000'");
    }

    @Test
    void testDefaultExpressionForSimpleType_intTypesUnchanged() throws Exception {
        Method method = UnitTestGenerator.class.getDeclaredMethod("defaultExpressionForSimpleType", Type.class);
        method.setAccessible(true);
        
        // Test that int types are not casted (should remain as plain "0")
        Type intType = new PrimitiveType(PrimitiveType.Primitive.INT);
        Expression intExpr = (Expression) method.invoke(generator, intType);
        assertEquals("0", intExpr.toString(), "Integer should not be casted");
        
        Type wrapperIntType = StaticJavaParser.parseType("Integer");
        Expression wrapperIntExpr = (Expression) method.invoke(generator, wrapperIntType);
        assertEquals("0", wrapperIntExpr.toString(), "Wrapper Integer should not be casted");
    }

    @Test
    void testValueFieldInitializerLiteral_byteTypes() throws Exception {
        Method method = UnitTestGenerator.class.getDeclaredMethod("valueFieldInitializerLiteral", Type.class);
        method.setAccessible(true);
        
        // Test primitive byte
        Type primitiveByteType = new PrimitiveType(PrimitiveType.Primitive.BYTE);
        @SuppressWarnings("unchecked")
        Optional<String> byteResult = (Optional<String>) method.invoke(null, primitiveByteType);
        assertTrue(byteResult.isPresent());
        assertEquals("(byte)0", byteResult.get(), "Primitive byte should return (byte)0");
        
        // Test wrapper Byte
        Type wrapperByteType = StaticJavaParser.parseType("Byte");
        @SuppressWarnings("unchecked")
        Optional<String> wrapperByteResult = (Optional<String>) method.invoke(null, wrapperByteType);
        assertTrue(wrapperByteResult.isPresent());
        assertEquals("(byte)0", wrapperByteResult.get(), "Wrapper Byte should return (byte)0");
    }

    @Test
    void testValueFieldInitializerLiteral_shortTypes() throws Exception {
        Method method = UnitTestGenerator.class.getDeclaredMethod("valueFieldInitializerLiteral", Type.class);
        method.setAccessible(true);
        
        // Test primitive short
        Type primitiveShortType = new PrimitiveType(PrimitiveType.Primitive.SHORT);
        @SuppressWarnings("unchecked")
        Optional<String> shortResult = (Optional<String>) method.invoke(null, primitiveShortType);
        assertTrue(shortResult.isPresent());
        assertEquals("(short)0", shortResult.get(), "Primitive short should return (short)0");
        
        // Test wrapper Short
        Type wrapperShortType = StaticJavaParser.parseType("Short");
        @SuppressWarnings("unchecked")
        Optional<String> wrapperShortResult = (Optional<String>) method.invoke(null, wrapperShortType);
        assertTrue(wrapperShortResult.isPresent());
        assertEquals("(short)0", wrapperShortResult.get(), "Wrapper Short should return (short)0");
    }

    @Test
    void testValueFieldInitializerLiteral_charTypes() throws Exception {
        Method method = UnitTestGenerator.class.getDeclaredMethod("valueFieldInitializerLiteral", Type.class);
        method.setAccessible(true);
        
        // Test primitive char
        Type primitiveCharType = new PrimitiveType(PrimitiveType.Primitive.CHAR);
        @SuppressWarnings("unchecked")
        Optional<String> charResult = (Optional<String>) method.invoke(null, primitiveCharType);
        assertTrue(charResult.isPresent());
        assertEquals("'\\0'", charResult.get(), "Primitive char should return '\\0'");
        
        // Test wrapper Character
        Type wrapperCharType = StaticJavaParser.parseType("Character");
        @SuppressWarnings("unchecked")
        Optional<String> wrapperCharResult = (Optional<String>) method.invoke(null, wrapperCharType);
        assertTrue(wrapperCharResult.isPresent());
        assertEquals("'\\0'", wrapperCharResult.get(), "Wrapper Character should return '\\0'");
    }

    @Test
    void testValueFieldInitializerLiteral_intTypesUnchanged() throws Exception {
        Method method = UnitTestGenerator.class.getDeclaredMethod("valueFieldInitializerLiteral", Type.class);
        method.setAccessible(true);
        
        // Test that int types are not casted (should remain as plain "0")
        Type intType = new PrimitiveType(PrimitiveType.Primitive.INT);
        @SuppressWarnings("unchecked")
        Optional<String> intResult = (Optional<String>) method.invoke(null, intType);
        assertTrue(intResult.isPresent());
        assertEquals("0", intResult.get(), "Primitive int should not be casted");
        
        Type wrapperIntType = StaticJavaParser.parseType("Integer");
        @SuppressWarnings("unchecked")
        Optional<String> wrapperIntResult = (Optional<String>) method.invoke(null, wrapperIntType);
        assertTrue(wrapperIntResult.isPresent());
        assertEquals("0", wrapperIntResult.get(), "Wrapper Integer should not be casted");
    }
}
