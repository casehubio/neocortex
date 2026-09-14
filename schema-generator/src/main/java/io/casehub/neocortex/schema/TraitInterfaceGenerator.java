package io.casehub.neocortex.schema;

import io.casehub.neocortex.mindmap.SchemaField;
import java.util.Map;

public final class TraitInterfaceGenerator {

    private TraitInterfaceGenerator() {}

    public static String generate(String typeName, Map<String, SchemaField> schema,
                                   String packageName) {
        String interfaceName = toInterfaceName(typeName);
        var sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        if (!schema.isEmpty()) {
            sb.append("import java.util.Optional;\n\n");
        }
        sb.append("public interface ").append(interfaceName).append(" {\n");
        for (var field : schema.values()) {
            sb.append("    Optional<String> ").append(field.name()).append("();\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    static String toInterfaceName(String typeName) {
        String[] parts = typeName.split("-");
        var sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        sb.append("like");
        return sb.toString();
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: TraitInterfaceGenerator --type <name> --package <pkg>");
            System.exit(1);
        }
        String type = null, pkg = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--type" -> type = args[++i];
                case "--package" -> pkg = args[++i];
            }
        }
        if (type == null || pkg == null) {
            System.err.println("--type and --package are required");
            System.exit(1);
        }
        System.err.println("Note: standalone mode generates from inline schema only.");
        System.err.println("For live store access, use within a Quarkus application.");
        System.out.println(generate(type, Map.of(), pkg));
    }
}
