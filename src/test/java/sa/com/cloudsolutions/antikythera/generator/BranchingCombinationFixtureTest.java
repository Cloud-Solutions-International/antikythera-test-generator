package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Branching;
import sa.com.cloudsolutions.antikythera.evaluator.BranchingTrace;
import sa.com.cloudsolutions.antikythera.evaluator.DummyArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchingCombinationFixtureTest {
    private static final String FIXTURE_CLASS =
            "sa.com.cloudsolutions.antikythera.testhelper.evaluator.BranchingCombinations";

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @Test
    void generatesTestsForBranchingCombinationFixture() throws ReflectiveOperationException {
        String genSource = runGenerator(FIXTURE_CLASS);

        assertTrue(genSource.contains("sequentialProblemStringsTest"),
                "Expected generated tests for sequentialProblemStrings");
        assertTrue(genSource.contains("deletedByLookupTest"),
                "Expected generated tests for deletedByLookup");
        assertTrue(genSource.contains("CombinationRepository repository;"),
                "Expected repository dependency to be mocked");
        assertTrue(genSource.contains("DoctorDirectory directory;"),
                "Expected directory dependency to be mocked");
        assertTrue(genSource.contains("Mockito.when(repository.findActiveByDiagnosisType"),
                "Expected collaborator stubbing for diagnosis-type path");
        assertTrue(genSource.contains("Mockito.when(repository.findActive("),
                "Expected collaborator stubbing for empty-diagnosis path");
        assertTrue(genSource.contains("branchingCombinations = new BranchingCombinations(repository, directory);"),
                "Expected constructor-injected fixture to be instantiated with discovered mocks");
        assertFalse(genSource.contains("assertThrows(java.lang.NullPointerException.class"),
                "Collaborator-backed fixture generation should no longer collapse into plain NPE tests");
    }

    @Test
    void collaboratorFixturesRecordPreconditionsAndGeneratedFingerprints() throws ReflectiveOperationException {
        String genSource = runGenerator(FIXTURE_CLASS);

        List<String> trace = BranchingTrace.snapshot();
        System.out.println("TRACE_START");
        System.out.println(String.join(System.lineSeparator(), trace));
        System.out.println("TRACE_END");
        System.out.println("GEN_START");
        System.out.println(genSource);
        System.out.println("GEN_END");
        assertTrue(trace.stream().anyMatch(event -> event.startsWith("preconditions:sequentialProblemStrings")),
                "Expected precondition trace entries for sequentialProblemStrings");
        assertTrue(trace.stream().anyMatch(event -> event.startsWith("preconditions:deletedByLookup")),
                "Expected precondition trace entries for deletedByLookup");
        assertTrue(trace.stream().anyMatch(event -> event.startsWith("generated:sequentialProblemStrings")),
                "Expected generated-test trace entries for sequentialProblemStrings");
        assertTrue(trace.stream().anyMatch(event -> event.startsWith("generated:deletedByLookup")),
                "Expected generated-test trace entries for deletedByLookup");
    }

    @Test
    void sequentialDirectRecordsBranchSelectionTrace() throws ReflectiveOperationException {
        runEvaluator(FIXTURE_CLASS, "sequentialDirect");

        List<String> trace = BranchingTrace.snapshot();
        long selectedCount = trace.stream()
                .filter(event -> event.startsWith("selected:sequentialDirect"))
                .count();
        assertTrue(trace.stream().anyMatch(event -> event.startsWith("target:sequentialDirect")),
                "Expected target trace entries for sequentialDirect");
        assertTrue(trace.stream().anyMatch(event -> event.startsWith("selected:sequentialDirect")),
                "Expected selected-combination trace entries for sequentialDirect");
        assertTrue(trace.stream()
                        .filter(event -> event.startsWith("truthTable:sequentialDirect"))
                        .map(this::extractRowCount)
                        .allMatch(count -> count >= 1),
                "Expected truth-table row counts in sequentialDirect trace");
        assertTrue(selectedCount >= 4,
                "Expected multiple branch-attempt selections for sequentialDirect");
    }

    @Disabled("Pending branch-combination exploration fix")
    @Test
    void sequentialProblemStringsShouldGenerateFourCombinations() throws ReflectiveOperationException {
        String genSource = runGenerator(FIXTURE_CLASS);

        assertEquals(4, countTests(genSource, "sequentialProblemStringsTest"));
        assertTrue(genSource.contains("setDiagnosisType(null)"));
        assertTrue(genSource.contains("setDiagnosisType(\"0\")"));
        assertTrue(genSource.contains("thenReturn(new ArrayList<>())")
                || genSource.contains("thenReturn(List.of())"));
        assertTrue(genSource.contains("thenReturn(List.of(\"Antikythera\"))")
                || genSource.contains("thenReturn(List.of(\"FOUND\"))"));
    }

    @Disabled("Pending branch-combination exploration fix")
    @Test
    void deletedByLookupShouldGenerateDistinctDeletedByBranchTests() throws ReflectiveOperationException {
        String genSource = runGenerator(FIXTURE_CLASS);

        assertTrue(countTests(genSource, "deletedByLookupTest") >= 4,
                "Expected distinct tests for allRecords split and deletedBy branch family");
        assertTrue(genSource.contains("setDeletedBy(null)")
                || genSource.contains("setDeletedBy(\"\")"));
        assertTrue(genSource.contains("setDeletedBy(\"1\")")
                || genSource.contains("setDeletedBy(\"0\")"));
    }

    private String runGenerator(String cls) throws ReflectiveOperationException {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(cls);
        assertNotNull(cu, "CompilationUnit for " + cls + " should not be null");

        UnitTestGenerator generator = new UnitTestGenerator(cu);
        generator.setArgumentGenerator(new DummyArgumentGenerator());
        generator.setAsserter(new JunitAsserter());
        generator.addBeforeClass();

        SpringEvaluator evaluator = EvaluatorFactory.create(cls, SpringEvaluator.class);
        evaluator.setOnTest(true);
        evaluator.addGenerator(generator);
        evaluator.setArgumentGenerator(new DummyArgumentGenerator());
        Branching.clear();
        BranchingTrace.clear();

        for (String methodName : new String[]{"sequentialProblemStrings", "deletedByLookup"}) {
            MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                    method -> method.getNameAsString().equals(methodName)).orElseThrow();
            evaluator.visit(md);
        }

        CompilationUnit gen = generator.getCompilationUnit();
        assertNotNull(gen);
        return gen.toString();
    }

    private void runEvaluator(String cls, String methodName) throws ReflectiveOperationException {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(cls);
        assertNotNull(cu, "CompilationUnit for " + cls + " should not be null");

        SpringEvaluator evaluator = EvaluatorFactory.create(cls, SpringEvaluator.class);
        evaluator.setOnTest(true);
        evaluator.setArgumentGenerator(new DummyArgumentGenerator());

        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                method -> method.getNameAsString().equals(methodName)).orElseThrow();

        Branching.clear();
        BranchingTrace.clear();
        evaluator.visit(md);
    }

    private int countTests(String genSource, String baseName) {
        Pattern pattern = Pattern.compile("void\\s+" + Pattern.quote(baseName) + "(?:_\\d+)?\\s*\\(");
        Matcher matcher = pattern.matcher(genSource);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int extractRowCount(String event) {
        Matcher matcher = Pattern.compile("\\|rows=(\\d+)\\|").matcher(event);
        assertTrue(matcher.find(), "Trace event should include row count: " + event);
        return Integer.parseInt(matcher.group(1));
    }
}
