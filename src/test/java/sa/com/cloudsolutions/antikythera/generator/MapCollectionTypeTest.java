package sa.com.cloudsolutions.antikythera.generator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.type.Type;

/**
 * Tests to verify Map vs Collection type handling in TypeInspector
 */
public class MapCollectionTypeTest {

    @Test
    public void testIsMapTypeCorrectlyIdentifiesMaps() {
        Type mapType = StaticJavaParser.parseType("Map<String, String>");
        Type hashMapType = StaticJavaParser.parseType("HashMap<String, String>");
        Type linkedHashMapType = StaticJavaParser.parseType("LinkedHashMap<String, String>");
        
        assertTrue(TypeInspector.isMapType(mapType));
        assertTrue(TypeInspector.isMapType(hashMapType));
        assertTrue(TypeInspector.isMapType(linkedHashMapType));
    }

    @Test
    public void testIsCollectionTypeExcludesMaps() {
        Type mapType = StaticJavaParser.parseType("Map<String, String>");
        Type hashMapType = StaticJavaParser.parseType("HashMap<String, String>");
        Type linkedHashMapType = StaticJavaParser.parseType("LinkedHashMap<String, String>");
        
        assertFalse(TypeInspector.isCollectionType(mapType));
        assertFalse(TypeInspector.isCollectionType(hashMapType));
        assertFalse(TypeInspector.isCollectionType(linkedHashMapType));
    }

    @Test
    public void testIsCollectionTypeCorrectlyIdentifiesCollections() {
        Type listType = StaticJavaParser.parseType("List<String>");
        Type arrayListType = StaticJavaParser.parseType("ArrayList<String>");
        Type setType = StaticJavaParser.parseType("Set<String>");
        Type hashSetType = StaticJavaParser.parseType("HashSet<String>");
        Type collectionType = StaticJavaParser.parseType("Collection<String>");
        
        assertTrue(TypeInspector.isCollectionType(listType));
        assertTrue(TypeInspector.isCollectionType(arrayListType));
        assertTrue(TypeInspector.isCollectionType(setType));
        assertTrue(TypeInspector.isCollectionType(hashSetType));
        assertTrue(TypeInspector.isCollectionType(collectionType));
    }

    @Test
    public void testIsCollectionOrMapFieldTypeIncludesBoth() {
        Type mapType = StaticJavaParser.parseType("Map<String, String>");
        Type listType = StaticJavaParser.parseType("List<String>");
        Type hashMapType = StaticJavaParser.parseType("HashMap<String, String>");
        Type arrayListType = StaticJavaParser.parseType("ArrayList<String>");
        
        assertTrue(TypeInspector.isCollectionOrMapFieldType(mapType));
        assertTrue(TypeInspector.isCollectionOrMapFieldType(listType));
        assertTrue(TypeInspector.isCollectionOrMapFieldType(hashMapType));
        assertTrue(TypeInspector.isCollectionOrMapFieldType(arrayListType));
    }
}
