package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.NullArgumentGenerator;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import sa.com.cloudsolutions.antikythera.evaluator.ExceptionContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Targets {@link ExceptionResponseAsserter} outcomes (legacy assertThrows, unconditional assertThrows).
 */
class ExceptionResponseAsserterTest {

    private UnitTestGenerator utg;
    private ClassOrInterfaceDeclaration classUnderTest;

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.service.PersonService");
        classUnderTest = cu.getType(0).asClassOrInterfaceDeclaration();
        utg = new UnitTestGenerator(cu);
        utg.setArgumentGenerator(Mockito.mock(NullArgumentGenerator.class));
        utg.setPreConditions(new ArrayList<>());
        utg.setAsserter(new JunitAsserter());
    }

    @Test
    void legacyAssertThrowsWhenExceptionContextMissing() {
        MethodDeclaration md = classUnderTest.getMethodsByName("queries2").getFirst();
        utg.createTests(md, new MethodResponse());

        MethodResponse emptyContext = new MethodResponse();
        new ExceptionResponseAsserter(utg).handle(emptyContext, "personService.queries2();");

        String body = utg.testMethod.getBody().orElseThrow().toString();
        assertTrue(body.contains("assertThrows"), body);
    }

    @Test
    void assertThrowsEmittedForUnconditionalRuntimeException() {
        var saved = new ArrayList<>(classUnderTest.getAnnotations());
        try {
            classUnderTest.getAnnotations().clear();
            classUnderTest.addAnnotation("Service");
            utg.addBeforeClass();
            MethodDeclaration md = classUnderTest.getMethodsByName("queries2").getFirst();
            MethodResponse response = new MethodResponse();
            response.setException(new EvaluatorException("sym", new RuntimeException("boom")));

            utg.createTests(md, response);

            assertTrue(utg.getCompilationUnit().toString().contains("assertThrows"),
                    "Unconditional non-NPE exception should yield assertThrows in generated source");
        } finally {
            classUnderTest.getAnnotations().clear();
            classUnderTest.getAnnotations().addAll(saved);
        }
    }

    /**
     * Test the specific path where both illegalArgumentSuppressionApplied and reinstatedNpe become true,
     * ensuring the exception type becomes EvaluatorException(NPE) as expected.
     * 
     * This test verifies that when:
     * 1. shouldSuppressIllegalArgumentAssertThrows returns true (sets illegalArgumentSuppressionApplied = true)
     * 2. hasWhenStubs() returns false OR hasNullThenReturnStubs() returns true (sets reinstatedNpe = true)
     * 
     * Then the response exception is mutated to EvaluatorException wrapping a NPE.
     */
    @Test
    void bothIllegalArgumentSuppressionAndNpeReinstatementApplied() {
        // Setup test method with no when stubs to trigger reinstatedNpe = true
        MethodDeclaration md = classUnderTest.getMethodsByName("queries2").getFirst();
        utg.createTests(md, new MethodResponse()); // Create base test method structure
        
        // Create mock UnitTestGenerator to control the specific methods
        UnitTestGenerator mockUtg = spy(utg);
        
        // Setup exception context that would trigger IllegalArgumentException suppression
        ExceptionContext ctx = mock(ExceptionContext.class);
        when(ctx.getException()).thenReturn(new IllegalArgumentException("Mock IAE for testing"));
        
        // Create test response with the exception context
        MethodResponse response = new MethodResponse();
        response.setExceptionContext(ctx);
        response.setException(new EvaluatorException("test", new IllegalArgumentException("test IAE")));
        
        // Setup test arguments that won't trigger the exception (non-null, non-empty)
        Map<String, Expression> testArgs = new HashMap<>();
        testArgs.put("param1", new StringLiteralExpr("non-null"));
        testArgs.put("param2", new IntegerLiteralExpr("42"));
        
        // Mock the methods to control the flow:
        // 1. shouldSuppressIllegalArgumentAssertThrows should return true (for illegalArgumentSuppressionApplied = true)
        doReturn(true).when(mockUtg).shouldSuppressIllegalArgumentAssertThrows(any(), any());
        
        // 2. hasWhenStubs should return false (for reinstatedNpe = true)
        doReturn(false).when(mockUtg).hasWhenStubs();
        
        // 3. extractTestArguments should return our controlled test args
        doReturn(testArgs).when(mockUtg).extractTestArguments();
        
        // Mock other necessary methods to avoid side effects
        doNothing().when(mockUtg).seedCollectionArgumentsForException(any(), any());
        doNothing().when(mockUtg).assertThrows(anyString(), any(MethodResponse.class));
        
        // Create and execute the ExceptionResponseAsserter
        ExceptionResponseAsserter asserter = new ExceptionResponseAsserter(mockUtg);
        asserter.handle(response, "mockService.testMethod()");
        
        // Verify that the exception was mutated to EvaluatorException(NPE)
        Exception resultException = response.getException();
        assertTrue(resultException instanceof EvaluatorException, 
                "Expected EvaluatorException but got: " + resultException.getClass().getSimpleName());
        
        EvaluatorException evaluatorException = (EvaluatorException) resultException;
        assertTrue(evaluatorException.getCause() instanceof NullPointerException,
                "Expected NPE as cause but got: " + 
                (evaluatorException.getCause() != null ? evaluatorException.getCause().getClass().getSimpleName() : "null"));
        
        assertEquals("Reinstated assertThrows for runtime NPE", evaluatorException.getMessage(),
                "Expected specific message for reinstated NPE");
        
        // Verify that assertThrows was called (willTrigger = true due to reinstatement)
        verify(mockUtg, times(1)).assertThrows(anyString(), eq(response));
    }

    @Test 
    void bothIllegalArgumentSuppressionAndNpeReinstatementWithNullThenReturnStubs() {
        // Alternative test path: reinstatedNpe = true via hasNullThenReturnStubs() = true
        MethodDeclaration md = classUnderTest.getMethodsByName("queries2").getFirst();
        utg.createTests(md, new MethodResponse());
        
        UnitTestGenerator mockUtg = spy(utg);
        
        // Setup exception context for IAE suppression
        ExceptionContext ctx = mock(ExceptionContext.class);
        when(ctx.getException()).thenReturn(new IllegalArgumentException("Mock IAE"));
        
        MethodResponse response = new MethodResponse();
        response.setExceptionContext(ctx);
        response.setException(new EvaluatorException("test", new IllegalArgumentException("test IAE")));
        
        Map<String, Expression> testArgs = new HashMap<>();
        testArgs.put("param1", new StringLiteralExpr("non-null"));
        
        // Control flow: suppression = true, hasWhenStubs = true, but hasNullThenReturnStubs = true
        doReturn(true).when(mockUtg).shouldSuppressIllegalArgumentAssertThrows(any(), any());
        doReturn(true).when(mockUtg).hasWhenStubs(); // This time hasWhenStubs = true
        doReturn(true).when(mockUtg).hasNullThenReturnStubs(); // But this returns true -> reinstatedNpe = true
        doReturn(testArgs).when(mockUtg).extractTestArguments();
        
        doNothing().when(mockUtg).seedCollectionArgumentsForException(any(), any());
        doNothing().when(mockUtg).assertThrows(anyString(), any(MethodResponse.class));
        
        ExceptionResponseAsserter asserter = new ExceptionResponseAsserter(mockUtg);
        asserter.handle(response, "mockService.testMethod()");
        
        // Verify same mutation occurs
        Exception resultException = response.getException();
        assertTrue(resultException instanceof EvaluatorException);
        
        EvaluatorException evaluatorException = (EvaluatorException) resultException;
        assertTrue(evaluatorException.getCause() instanceof NullPointerException);
        assertEquals("Reinstated assertThrows for runtime NPE", evaluatorException.getMessage());
        
        verify(mockUtg, times(1)).assertThrows(anyString(), eq(response));
    }
}
