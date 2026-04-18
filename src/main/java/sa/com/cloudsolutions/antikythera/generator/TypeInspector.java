package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.type.Type;

/**
 * Erased simple-name checks for JavaParser {@link Type} nodes used during test generation.
 */
public final class TypeInspector {

    private static final String ARRAY_LIST = "ArrayList";
    private static final String COLLECTION = "Collection";
    private static final String HASH_SET = "HashSet";
    private static final String LINKED_LIST = "LinkedList";
    private static final String LINKED_HASH_SET = "LinkedHashSet";

    private TypeInspector() {
    }

    public static String rawSimpleName(Type type) {
        if (type.isClassOrInterfaceType()) {
            return type.asClassOrInterfaceType().getName().getIdentifier();
        }
        return type.asString().replaceAll("<.*>", "").trim();
    }

    /**
     * Collection types used when matching method parameters for exception-path collection seeding.
     */
    public static boolean isCollectionParameterType(Type type) {
        if (!type.isClassOrInterfaceType()) {
            return false;
        }
        String raw = rawSimpleName(type);
        return raw.equals("List") || raw.equals(ARRAY_LIST) || raw.equals(COLLECTION)
                || raw.equals("Set") || raw.equals(HASH_SET);
    }

    /**
     * Collection and map types for field mocking and dependency setup.
     */
    public static boolean isCollectionOrMapFieldType(Type type) {
        String raw = rawSimpleName(type);
        return raw.equals("List") || raw.equals(ARRAY_LIST) || raw.equals(LINKED_LIST)
                || raw.equals("Set") || raw.equals(HASH_SET) || raw.equals(LINKED_HASH_SET)
                || raw.equals(COLLECTION) || raw.equals("Map") || raw.equals("HashMap")
                || raw.equals("LinkedHashMap");
    }

    /**
     * Collection types for field mocking and dependency setup (excludes Maps).
     */
    public static boolean isCollectionType(Type type) {
        String raw = rawSimpleName(type);
        return raw.equals("List") || raw.equals(ARRAY_LIST) || raw.equals(LINKED_LIST)
                || raw.equals("Set") || raw.equals(HASH_SET) || raw.equals(LINKED_HASH_SET)
                || raw.equals(COLLECTION);
    }

    public static boolean isMapType(Type type) {
        if (!type.isClassOrInterfaceType()) {
            return false;
        }
        String raw = rawSimpleName(type);
        return raw.equals("Map") || raw.equals("HashMap") || raw.equals("LinkedHashMap");
    }

    /**
     * True for {@code new X<>()} where {@code X} is treated as an empty collection constructor
     * in {@link CollectionExpressionAnalyzer}.
     */
    public static boolean isLikelyConcreteEmptyCollectionType(Type type) {
        if (!type.isClassOrInterfaceType()) {
            return false;
        }
        String simple = type.asClassOrInterfaceType().getNameAsString();
        if (simple.contains(ARRAY_LIST) || simple.contains(LINKED_LIST) || simple.contains(HASH_SET)
                || simple.contains("TreeSet") || simple.contains(LINKED_HASH_SET)) {
            return true;
        }
        String raw = rawSimpleName(type);
        return raw.equals("List") || raw.equals(ARRAY_LIST) || raw.equals(COLLECTION)
                || raw.equals("Set") || raw.equals(HASH_SET);
    }
}
