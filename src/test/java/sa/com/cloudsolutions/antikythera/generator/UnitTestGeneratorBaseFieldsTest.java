package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify the extractBaseClassFields and tryUseBaseClassField methods
 * handle multiple fields of the same type correctly
 */
class UnitTestGeneratorBaseFieldsTest {

    private UnitTestGenerator generator;
    
    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
    }
    
    @BeforeEach
    void setUp() {
        CompilationUnit cu = StaticJavaParser.parse("public class TestClass {}");
        generator = new UnitTestGenerator(cu, GeneratorSeams.defaults());
    }

    @Test
    void testExtractBaseClassFields_collectsMultipleFieldsOfSameType() throws Exception {
        String baseClass = """
            class BaseTest {
                protected String tenant;
                protected String mainTenant;
                protected MetaData metaData;
                @Mock
                private String mockField;
                @MockBean
                private String mockBeanField;
                private String privateField;
            }
            """;
        
        CompilationUnit cu = StaticJavaParser.parse(baseClass);
        TypeDeclaration<?> baseType = cu.getType(0);
        
        // Use reflection to access private method
        Method extractMethod = UnitTestGenerator.class.getDeclaredMethod("extractBaseClassFields", 
                TypeDeclaration.class);
        extractMethod.setAccessible(true);
        extractMethod.invoke(generator, baseType);
        
        // Use reflection to access private field
        Field baseClassFieldsField = UnitTestGenerator.class.getDeclaredField("baseClassFields");
        baseClassFieldsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> baseClassFields = (Map<String, List<String>>) baseClassFieldsField.get(generator);
        
        // Verify multiple String fields were collected (both tenant and mainTenant)
        List<String> stringFields = baseClassFields.get("String");
        assertNotNull(stringFields);
        assertEquals(2, stringFields.size());
        assertTrue(stringFields.contains("tenant"));
        assertTrue(stringFields.contains("mainTenant"));
        
        // Verify MetaData field was collected
        List<String> metaDataFields = baseClassFields.get("MetaData");
        assertNotNull(metaDataFields);
        assertEquals(1, metaDataFields.size());
        assertTrue(metaDataFields.contains("metaData"));
        
        // Verify mock fields were excluded
        assertFalse(baseClassFields.containsValue(List.of("mockField")));
        assertFalse(baseClassFields.containsValue(List.of("mockBeanField")));
        
        // Verify private field was excluded
        assertFalse(baseClassFields.containsValue(List.of("privateField")));
    }

    @Test
    void testTryUseBaseClassField_exactNameMatchPreferred() throws Exception {
        // Setup test method
        setupTestMethod();
        
        // Setup base class fields manually
        Field baseClassFieldsField = UnitTestGenerator.class.getDeclaredField("baseClassFields");
        baseClassFieldsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> baseClassFields = (Map<String, List<String>>) baseClassFieldsField.get(generator);
        
        // Add multiple String fields: tenant, mainTenant
        baseClassFields.put("String", List.of("tenant", "mainTenant"));
        
        // Create a parameter with exact name match
        Parameter param = StaticJavaParser.parseParameter("String tenant");
        
        Method tryUseMethod = UnitTestGenerator.class.getDeclaredMethod("tryUseBaseClassField", 
                Parameter.class);
        tryUseMethod.setAccessible(true);
        
        boolean result = (Boolean) tryUseMethod.invoke(generator, param);
        
        assertTrue(result, "Should return true when exact name match found");
        
        // Verify the correct statement was added
        String testMethodBody = generator.testMethod.getBody().orElseThrow().toString();
        assertTrue(testMethodBody.contains("String tenant = this.tenant;"), 
                "Should use exact name match 'tenant', not 'mainTenant'");
        assertFalse(testMethodBody.contains("String tenant = this.mainTenant;"), 
                "Should not use different field name when exact match exists");
    }

    @Test
    void testTryUseBaseClassField_autoWireWhenUniqueType() throws Exception {
        // Setup test method
        setupTestMethod();
        
        // Setup base class fields manually
        Field baseClassFieldsField = UnitTestGenerator.class.getDeclaredField("baseClassFields");
        baseClassFieldsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> baseClassFields = (Map<String, List<String>>) baseClassFieldsField.get(generator);
        
        // Add single MetaData field
        baseClassFields.put("MetaData", List.of("metaData"));
        
        // Create a parameter with different name but same type
        Parameter param = StaticJavaParser.parseParameter("MetaData someMetaData");
        
        Method tryUseMethod = UnitTestGenerator.class.getDeclaredMethod("tryUseBaseClassField", 
                Parameter.class);
        tryUseMethod.setAccessible(true);
        
        boolean result = (Boolean) tryUseMethod.invoke(generator, param);
        
        assertTrue(result, "Should return true when exactly one field of type exists");
        
        // Verify the correct statement was added
        String testMethodBody = generator.testMethod.getBody().orElseThrow().toString();
        assertTrue(testMethodBody.contains("MetaData someMetaData = this.metaData;"), 
                "Should auto-wire by type when only one candidate exists");
    }

    @Test
    void testTryUseBaseClassField_rejectsMultipleCandidatesWithoutExactMatch() throws Exception {
        // Setup test method
        setupTestMethod();
        
        // Setup base class fields manually
        Field baseClassFieldsField = UnitTestGenerator.class.getDeclaredField("baseClassFields");
        baseClassFieldsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> baseClassFields = (Map<String, List<String>>) baseClassFieldsField.get(generator);
        
        // Add multiple String fields: tenant, mainTenant
        baseClassFields.put("String", List.of("tenant", "mainTenant"));
        
        // Create a parameter with no exact match
        Parameter param = StaticJavaParser.parseParameter("String someOtherName");
        
        Method tryUseMethod = UnitTestGenerator.class.getDeclaredMethod("tryUseBaseClassField", 
                Parameter.class);
        tryUseMethod.setAccessible(true);
        
        boolean result = (Boolean) tryUseMethod.invoke(generator, param);
        
        assertFalse(result, "Should return false when multiple candidates exist without exact name match");
        
        // Verify no statement was added
        String testMethodBody = generator.testMethod.getBody().orElseThrow().toString();
        assertFalse(testMethodBody.contains("String someOtherName = this."), 
                "Should not add any assignment when unable to select field");
    }

    @Test
    void testTryUseBaseClassField_rejectsNonExistentType() throws Exception {
        // Setup test method
        setupTestMethod();
        
        // Setup base class fields manually
        Field baseClassFieldsField = UnitTestGenerator.class.getDeclaredField("baseClassFields");
        baseClassFieldsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> baseClassFields = (Map<String, List<String>>) baseClassFieldsField.get(generator);
        
        // Add only String fields
        baseClassFields.put("String", List.of("tenant"));
        
        // Create a parameter with different type
        Parameter param = StaticJavaParser.parseParameter("Integer someNumber");
        
        Method tryUseMethod = UnitTestGenerator.class.getDeclaredMethod("tryUseBaseClassField", 
                Parameter.class);
        tryUseMethod.setAccessible(true);
        
        boolean result = (Boolean) tryUseMethod.invoke(generator, param);
        
        assertFalse(result, "Should return false when no fields of the type exist");
    }
    
    private void setupTestMethod() {
        MethodDeclaration md = StaticJavaParser.parseMethodDeclaration("public void testMethod() {}");
        generator.methodUnderTest = md;
        generator.testMethod = generator.buildTestMethod(md);
        generator.setAsserter(new JunitAsserter());
    }
}
