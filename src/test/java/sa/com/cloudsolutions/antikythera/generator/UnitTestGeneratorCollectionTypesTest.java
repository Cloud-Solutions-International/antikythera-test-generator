package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that verifies collection type mapping in expandInlineDtoCollectionFields method
 */
public class UnitTestGeneratorCollectionTypesTest {

    private UnitTestGenerator generator;
    
    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
    }
    
    @BeforeEach
    public void setUp() {
        generator = new UnitTestGenerator(StaticJavaParser.parse("public class TestClass {}"), GeneratorSeams.defaults());
        // Initialize testMethod to avoid null pointer exceptions
        generator.testMethod = generator.buildTestMethod(StaticJavaParser.parseMethodDeclaration("void testMethod() {}"));
    }

    @Test
    public void testExpandInlineDtoCollectionFields_SetType() throws Exception {
        // Create a class with a Set field
        String dtoClassCode = """
            package com.test;
            import java.util.Set;
            public class TestDto {
                private Set<String> items;
                public void setItems(Set<String> items) { this.items = items; }
                public Set<String> getItems() { return items; }
            }
        """;
        CompilationUnit dtoCompilationUnit = StaticJavaParser.parse(dtoClassCode);
        AntikytheraRunTime.addCompilationUnit("TestDto", dtoCompilationUnit);
        AntikytheraRunTime.addType("TestDto", new sa.com.cloudsolutions.antikythera.generator.TypeWrapper(
            dtoCompilationUnit.getType(0).asClassOrInterfaceDeclaration()));
        
        // Create a thenReturn expression with inline DTO creation
        String whenThenCode = "mock.someMethod().thenReturn(new TestDto())";
        MethodCallExpr thenReturnExpr = StaticJavaParser.parseExpression(whenThenCode).asMethodCallExpr();
        
        // Get the private method and invoke it
        Method expandMethod = UnitTestGenerator.class.getDeclaredMethod("expandInlineDtoCollectionFields", 
            com.github.javaparser.ast.expr.Expression.class);
        expandMethod.setAccessible(true);
        expandMethod.invoke(generator, thenReturnExpr);
        
        // Verify the test method body contains HashSet initialization
        String testMethodBody = generator.testMethod.getBody().orElseThrow().toString();
        assertTrue(testMethodBody.contains("new HashSet()"), 
            "Should use HashSet for Set type. Actual: " + testMethodBody);
        
        // Verify import was added
        assertTrue(TestGenerator.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("java.util.HashSet")),
            "Should import java.util.HashSet");
    }

    @Test
    public void testExpandInlineDtoCollectionFields_MapType() throws Exception {
        // Create a class with a Map field
        String dtoClassCode = """
            import java.util.Map;
            public class TestDto {
                private Map<String, Integer> data;
                public void setData(Map<String, Integer> data) { this.data = data; }
                public Map<String, Integer> getData() { return data; }
            }
        """;
        
        CompilationUnit dtoCompilationUnit = StaticJavaParser.parse(dtoClassCode);
        AntikytheraRunTime.addCompilationUnit("TestDto", dtoCompilationUnit);
        AntikytheraRunTime.addType("TestDto", new sa.com.cloudsolutions.antikythera.generator.TypeWrapper(
            dtoCompilationUnit.getType(0).asClassOrInterfaceDeclaration()));
        
        // Create a thenReturn expression with inline DTO creation
        String whenThenCode = "mock.someMethod().thenReturn(new TestDto())";
        MethodCallExpr thenReturnExpr = StaticJavaParser.parseExpression(whenThenCode).asMethodCallExpr();
        
        // Get the private method and invoke it
        Method expandMethod = UnitTestGenerator.class.getDeclaredMethod("expandInlineDtoCollectionFields", 
            com.github.javaparser.ast.expr.Expression.class);
        expandMethod.setAccessible(true);
        expandMethod.invoke(generator, thenReturnExpr);
        
        // Verify the test method body contains HashMap initialization
        String testMethodBody = generator.testMethod.getBody().orElseThrow().toString();
        assertTrue(testMethodBody.contains("new HashMap()"), 
            "Should use HashMap for Map type. Actual: " + testMethodBody);
        
        // Verify import was added
        assertTrue(TestGenerator.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("java.util.HashMap")),
            "Should import java.util.HashMap");
    }

    @Test
    public void testExpandInlineDtoCollectionFields_LinkedListType() throws Exception {
        // Create a class with a LinkedList field
        String dtoClassCode = """
            import java.util.LinkedList;
            public class TestDto {
                private LinkedList<String> items;
                public void setItems(LinkedList<String> items) { this.items = items; }
                public LinkedList<String> getItems() { return items; }
            }
        """;
        
        CompilationUnit dtoCompilationUnit = StaticJavaParser.parse(dtoClassCode);
        AntikytheraRunTime.addCompilationUnit("TestDto", dtoCompilationUnit);
        AntikytheraRunTime.addType("TestDto", new sa.com.cloudsolutions.antikythera.generator.TypeWrapper(
            dtoCompilationUnit.getType(0).asClassOrInterfaceDeclaration()));
        
        // Create a thenReturn expression with inline DTO creation
        String whenThenCode = "mock.someMethod().thenReturn(new TestDto())";
        MethodCallExpr thenReturnExpr = StaticJavaParser.parseExpression(whenThenCode).asMethodCallExpr();
        
        // Get the private method and invoke it
        Method expandMethod = UnitTestGenerator.class.getDeclaredMethod("expandInlineDtoCollectionFields", 
            com.github.javaparser.ast.expr.Expression.class);
        expandMethod.setAccessible(true);
        expandMethod.invoke(generator, thenReturnExpr);
        
        // Verify the test method body contains LinkedList initialization
        String testMethodBody = generator.testMethod.getBody().orElseThrow().toString();
        assertTrue(testMethodBody.contains("new LinkedList()"), 
            "Should use LinkedList for LinkedList type. Actual: " + testMethodBody);
        
        // Verify import was added
        assertTrue(TestGenerator.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("java.util.LinkedList")),
            "Should import java.util.LinkedList");
    }

    @Test
    public void testExpandInlineDtoCollectionFields_ListTypeUsesArrayList() throws Exception {
        // Create a class with a List field (should default to ArrayList)
        String dtoClassCode = """
            import java.util.List;
            public class TestDto {
                private List<String> items;
                public void setItems(List<String> items) { this.items = items; }
                public List<String> getItems() { return items; }
            }
        """;
        
        CompilationUnit dtoCompilationUnit = StaticJavaParser.parse(dtoClassCode);
        AntikytheraRunTime.addCompilationUnit("TestDto", dtoCompilationUnit);
        AntikytheraRunTime.addType("TestDto", new sa.com.cloudsolutions.antikythera.generator.TypeWrapper(
            dtoCompilationUnit.getType(0).asClassOrInterfaceDeclaration()));
        
        // Create a thenReturn expression with inline DTO creation
        String whenThenCode = "mock.someMethod().thenReturn(new TestDto())";
        MethodCallExpr thenReturnExpr = StaticJavaParser.parseExpression(whenThenCode).asMethodCallExpr();
        
        // Get the private method and invoke it
        Method expandMethod = UnitTestGenerator.class.getDeclaredMethod("expandInlineDtoCollectionFields", 
            com.github.javaparser.ast.expr.Expression.class);
        expandMethod.setAccessible(true);
        expandMethod.invoke(generator, thenReturnExpr);
        
        // Verify the test method body contains ArrayList initialization
        String testMethodBody = generator.testMethod.getBody().orElseThrow().toString();
        assertTrue(testMethodBody.contains("new ArrayList()"), 
            "Should use ArrayList for List type. Actual: " + testMethodBody);
        
        // Verify import was added
        assertTrue(TestGenerator.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("java.util.ArrayList")),
            "Should import java.util.ArrayList");
    }

    @Test
    public void testExpandInlineDtoCollectionFields_LinkedHashSetType() throws Exception {
        // Create a class with a LinkedHashSet field
        String dtoClassCode = """
            import java.util.LinkedHashSet;
            public class TestDto {
                private LinkedHashSet<String> items;
                public void setItems(LinkedHashSet<String> items) { this.items = items; }
                public LinkedHashSet<String> getItems() { return items; }
            }
        """;
        
        CompilationUnit dtoCompilationUnit = StaticJavaParser.parse(dtoClassCode);
        AntikytheraRunTime.addCompilationUnit("TestDto", dtoCompilationUnit);
        AntikytheraRunTime.addType("TestDto", new sa.com.cloudsolutions.antikythera.generator.TypeWrapper(
            dtoCompilationUnit.getType(0).asClassOrInterfaceDeclaration()));
        
        // Create a thenReturn expression with inline DTO creation
        String whenThenCode = "mock.someMethod().thenReturn(new TestDto())";
        MethodCallExpr thenReturnExpr = StaticJavaParser.parseExpression(whenThenCode).asMethodCallExpr();
        
        // Get the private method and invoke it
        Method expandMethod = UnitTestGenerator.class.getDeclaredMethod("expandInlineDtoCollectionFields", 
            com.github.javaparser.ast.expr.Expression.class);
        expandMethod.setAccessible(true);
        expandMethod.invoke(generator, thenReturnExpr);
        
        // Verify the test method body contains LinkedHashSet initialization
        String testMethodBody = generator.testMethod.getBody().orElseThrow().toString();
        assertTrue(testMethodBody.contains("new LinkedHashSet()"), 
            "Should use LinkedHashSet for LinkedHashSet type. Actual: " + testMethodBody);
        
        // Verify import was added
        assertTrue(TestGenerator.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("java.util.LinkedHashSet")),
            "Should import java.util.LinkedHashSet");
    }

    @Test
    public void testExpandInlineDtoCollectionFields_LinkedHashMapType() throws Exception {
        // Create a class with a LinkedHashMap field
        String dtoClassCode = """
            import java.util.LinkedHashMap;
            public class TestDto {
                private LinkedHashMap<String, Integer> data;
                public void setData(LinkedHashMap<String, Integer> data) { this.data = data; }
                public LinkedHashMap<String, Integer> getData() { return data; }
            }
        """;
        
        CompilationUnit dtoCompilationUnit = StaticJavaParser.parse(dtoClassCode);
        AntikytheraRunTime.addCompilationUnit("TestDto", dtoCompilationUnit);
        AntikytheraRunTime.addType("TestDto", new sa.com.cloudsolutions.antikythera.generator.TypeWrapper(
            dtoCompilationUnit.getType(0).asClassOrInterfaceDeclaration()));
        
        // Create a thenReturn expression with inline DTO creation
        String whenThenCode = "mock.someMethod().thenReturn(new TestDto())";
        MethodCallExpr thenReturnExpr = StaticJavaParser.parseExpression(whenThenCode).asMethodCallExpr();
        
        // Get the private method and invoke it
        Method expandMethod = UnitTestGenerator.class.getDeclaredMethod("expandInlineDtoCollectionFields", 
            com.github.javaparser.ast.expr.Expression.class);
        expandMethod.setAccessible(true);
        expandMethod.invoke(generator, thenReturnExpr);
        
        // Verify the test method body contains LinkedHashMap initialization
        String testMethodBody = generator.testMethod.getBody().orElseThrow().toString();
        assertTrue(testMethodBody.contains("new LinkedHashMap()"), 
            "Should use LinkedHashMap for LinkedHashMap type. Actual: " + testMethodBody);
        
        // Verify import was added
        assertTrue(TestGenerator.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("java.util.LinkedHashMap")),
            "Should import java.util.LinkedHashMap");
    }
}
