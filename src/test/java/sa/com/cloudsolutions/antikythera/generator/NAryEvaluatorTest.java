package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.DummyArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the NAry functional evaluator correctly generates two tests for a JPA
 * {@code Specification} factory method:
 * <ol>
 *   <li>A success-path test that asserts {@code assertNotNull(resp)}.</li>
 *   <li>A functional-application test that asserts
 *       {@code assertThrows(NullPointerException.class, () -> resp.toPredicate(null, null, null))}.</li>
 * </ol>
 *
 * <p>The fixture is  sa.com.cloudsolutions.antikythera.testhelper.evaluator.RecordSearchSpecification
 * — an anonymised equivalent of the real-world {@code ProblemSearchSpecification} class that
 * triggered the original NAry evaluator bug.</p>
 */
class NAryEvaluatorTest {

    private static final String RECORD_SEARCH_SPEC =
            "sa.com.cloudsolutions.antikythera.testhelper.evaluator.RecordSearchSpecification";

    private static final String APPOINTMENT_SPEC =
            "sa.com.cloudsolutions.antikythera.testhelper.evaluator.AppointmentSpecification";

    private static final String REFERRAL_SPEC =
            "sa.com.cloudsolutions.antikythera.testhelper.evaluator.ReferralSpecification";

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        AntikytheraRunTime.reset();
        AntikytheraRunTime.resetAutowires();
    }

    // -----------------------------------------------------------------------
    // RecordSearchSpecification — primary fixture (mirrors ProblemSearchSpecification)
    // -----------------------------------------------------------------------

    @Test
    void searchRecords_generatesNotNullAssertForReturnValue() throws ReflectiveOperationException {
        String source = runGeneratorForMethod(RECORD_SEARCH_SPEC, "searchRecords");
        assertTrue(source.contains("assertNotNull(resp)"),
                "Expected assertNotNull(resp) for the Specification return value.\nGenerated:\n" + source);
    }

    @Test
    void searchRecords_generatesFPApplicationTestWithNPE() throws ReflectiveOperationException {
        String source = runGeneratorForMethod(RECORD_SEARCH_SPEC, "searchRecords");
        assertTrue(
                source.contains("assertThrows") && source.contains("NullPointerException") && source.contains("toPredicate"),
                "Expected assertThrows(NullPointerException.class, () -> resp.toPredicate(...)) for FP application.\n"
                        + "Generated:\n" + source);
    }

    @Test
    void searchRecords_generatesTwoSeparateTestMethods() throws ReflectiveOperationException {
        String source = runGeneratorForMethod(RECORD_SEARCH_SPEC, "searchRecords");
        // First test
        assertTrue(source.contains("searchRecordsTest()"),
                "Expected searchRecordsTest() method.\nGenerated:\n" + source);
        // Second test (FP application) — name has _1 suffix
        assertTrue(source.contains("searchRecordsTest_1()"),
                "Expected searchRecordsTest_1() method.\nGenerated:\n" + source);
    }

    @Test
    void searchRecords_fpTestBodyContainsMainInvocationThenAssertThrows()
            throws ReflectiveOperationException {
        String source = runGeneratorForMethod(RECORD_SEARCH_SPEC, "searchRecords");
        // The FP application test must first assign resp, then assertThrows — order matters.
        int assignIdx = source.indexOf("resp = RecordSearchSpecification.searchRecords");
        // Generator may emit simple or fully-qualified NullPointerException class name
        int assertIdx = source.indexOf("assertThrows(NullPointerException.class");
        if (assertIdx < 0) assertIdx = source.indexOf("assertThrows(java.lang.NullPointerException.class");
        assertTrue(assignIdx >= 0,
                "Expected Specification assignment in generated source.\nGenerated:\n" + source);
        assertTrue(assertIdx >= 0,
                "Expected assertThrows in generated source.\nGenerated:\n" + source);
        assertTrue(assignIdx < assertIdx,
                "Assignment must appear before assertThrows in the FP application test.\nGenerated:\n" + source);
    }

    // -----------------------------------------------------------------------
    // AppointmentSpecification — 3-param lambda with Objects.requireNonNull guards
    // -----------------------------------------------------------------------

    @Test
    void appointmentSpec_findByAdmissionId_generatesNotNullTest() throws ReflectiveOperationException {
        String source = runGeneratorForMethod(APPOINTMENT_SPEC, "findByAdmissionId");
        // The method either generates assertNotNull OR assertThrows; it must not be empty.
        assertFalse(source.isBlank(), "Generator should produce output for findByAdmissionId");
        assertTrue(source.contains("findByAdmissionIdTest"),
                "Expected a test method for findByAdmissionId.\nGenerated:\n" + source);
    }

    // -----------------------------------------------------------------------
    // ReferralSpecification — 3-param lambda with predicate list accumulation
    // -----------------------------------------------------------------------

    @Test
    void referralSpec_findByFilters_generatesTest() throws ReflectiveOperationException {
        String source = runGeneratorForMethod(REFERRAL_SPEC, "findByFilters");
        assertFalse(source.isBlank(), "Generator should produce output for findByFilters");
        assertTrue(source.contains("findByFiltersTest"),
                "Expected a test method for findByFilters.\nGenerated:\n" + source);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private String runGeneratorForMethod(String className, String methodName)
            throws ReflectiveOperationException {

        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
        assertNotNull(cu, "CompilationUnit for " + className + " should not be null");

        UnitTestGenerator generator = new UnitTestGenerator(cu);
        generator.setArgumentGenerator(new DummyArgumentGenerator());
        generator.setAsserter(new JunitAsserter());

        SpringEvaluator evaluator = EvaluatorFactory.create(className, SpringEvaluator.class);
        evaluator.setOnTest(true);
        evaluator.addGenerator(generator);
        evaluator.setArgumentGenerator(new DummyArgumentGenerator());

        for (CallableDeclaration<?> cd : cu.findAll(CallableDeclaration.class)) {
            if (cd instanceof MethodDeclaration md
                    && md.getNameAsString().equals(methodName)) {
                evaluator.visit(md);
            }
        }

        CompilationUnit gen = generator.getCompilationUnit();
        assertNotNull(gen);
        return gen.toString();
    }
}
