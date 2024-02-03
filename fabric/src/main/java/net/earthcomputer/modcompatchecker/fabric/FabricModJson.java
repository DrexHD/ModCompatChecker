package net.earthcomputer.modcompatchecker.fabric;

import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

record FabricModJson(Map<String, List<String>> entrypoints, @Nullable String accessWidener, List<String> jars) {

    @Nullable
    public static FabricModJson load(JarFile modJar) throws IOException {
        JarEntry modJsonEntry = modJar.getJarEntry("fabric.mod.json");
        if (modJsonEntry == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(modJar.getInputStream(modJsonEntry), StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.beginObject();
            return parse(jsonReader);
        }
    }

    // TODO License
    // https://github.com/FabricMC/fabric-loader/blob/master/src/main/java/net/fabricmc/loader/impl/metadata/V1ModMetadataParser.java
    static FabricModJson parse(JsonReader reader) throws IOException {
        Map<String, List<String>> entrypoints = new HashMap<>();
        List<String> jars = new ArrayList<>();
        String accessWidener = null;
        while (reader.hasNext()) {
            final String key = reader.nextName();

            switch (key) {
                case "schemaVersion":
                    if (reader.peek() != JsonToken.NUMBER) {
                        throw new JsonSyntaxException("Duplicate \"schemaVersion\" field is not a number");
                    }

                    final int read = reader.nextInt();

                    if (read != 1) {
                        throw new JsonSyntaxException(String.format("Duplicate \"schemaVersion\" field does not match the predicted schema version of 1. Duplicate field value is %s", read));
                    }

                    break;
                case "entrypoints":
                    readEntrypoints(reader, entrypoints);
                    break;
                case "jars":
                    readNestedJarEntries(reader, jars);
                    break;
                case "accessWidener":
                    if (reader.peek() != JsonToken.STRING) {
                        throw new JsonSyntaxException("Access Widener file must be a string");
                    }

                    accessWidener = reader.nextString();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        return new FabricModJson(entrypoints, accessWidener, jars);
    }

    private static void readEntrypoints(JsonReader reader, Map<String, List<String>> entrypoints) throws IOException {
        // Entrypoints must be an object
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new JsonSyntaxException("Entrypoints must be an object");
        }

        reader.beginObject();

        while (reader.hasNext()) {
            final String key = reader.nextName();

            List<String> metadata = new ArrayList<>();

            if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                throw new JsonSyntaxException("Entrypoint list must be an array!");
            }

            reader.beginArray();

            while (reader.hasNext()) {
                String adapter = "default";
                String value = null;

                // Entrypoints may be specified directly as a string or as an object to allow specification of the language adapter to use.
                switch (reader.peek()) {
                    case STRING:
                        value = reader.nextString();
                        break;
                    case BEGIN_OBJECT:
                        reader.beginObject();

                        while (reader.hasNext()) {
                            final String entryKey = reader.nextName();
                            switch (entryKey) {
                                case "adapter":
                                    adapter = reader.nextString();
                                    break;
                                case "value":
                                    value = reader.nextString();
                                    break;
                                default:
                                    reader.skipValue();
                                    break;
                            }
                        }

                        reader.endObject();
                        break;
                    default:
                        throw new JsonSyntaxException("Entrypoint must be a string or object with \"value\" field");
                }

                if (value == null) {
                    throw new JsonSyntaxException("Entrypoint value must be present");
                }

                metadata.add(value);
            }

            reader.endArray();

            // Empty arrays are acceptable, do not check if the List of metadata is empty
            entrypoints.put(key, metadata);
        }

        reader.endObject();
    }

    private static void readNestedJarEntries(JsonReader reader, List<String> jars) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            throw new JsonSyntaxException("Jar entries must be in an array");
        }

        reader.beginArray();

        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new JsonSyntaxException("Invalid type for JAR entry!");
            }

            reader.beginObject();
            String file = null;

            while (reader.hasNext()) {
                final String key = reader.nextName();

                if (key.equals("file")) {
                    if (reader.peek() != JsonToken.STRING) {
                        throw new JsonSyntaxException("\"file\" entry in jar object must be a string");
                    }

                    file = reader.nextString();
                } else {
                    reader.skipValue();
                }
            }

            reader.endObject();

            if (file == null) {
                throw new JsonSyntaxException("Missing mandatory key 'file' in JAR entry!");
            }

            jars.add(file);
        }

        reader.endArray();
    }
}
