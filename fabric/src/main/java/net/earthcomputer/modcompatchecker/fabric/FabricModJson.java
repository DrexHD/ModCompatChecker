package net.earthcomputer.modcompatchecker.fabric;

import com.google.gson.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

record FabricModJson(List<String> entrypoints, @Nullable String accessWidener, List<String> jars) {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(FabricModJson.class, new FabricModJsonDeserializer())
        .create();

    @Nullable
    public static FabricModJson load(JarFile modJar) throws IOException {
        JarEntry modJsonEntry = modJar.getJarEntry("fabric.mod.json");
        if (modJsonEntry == null) {
            return null;
        }
        FabricModJson fabricModJson;
        try (Reader reader = new InputStreamReader(modJar.getInputStream(modJsonEntry), StandardCharsets.UTF_8)) {
            fabricModJson = GSON.fromJson(reader, FabricModJson.class);
        } catch (JsonParseException e) {
            throw new IOException("Failed to parse fabric.mod.json", e);
        }
        return fabricModJson;
    }

    private static class FabricModJsonDeserializer implements JsonDeserializer<FabricModJson> {
        @Override
        public FabricModJson deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            // Throws IllegalStateException or ClassCastException if the file is formatted incorrect
            JsonObject root = json.getAsJsonObject();
            JsonElement schemaVersionJson = root.get("schemaVersion");
            if (schemaVersionJson == null || schemaVersionJson.getAsInt() != 1) {
                throw new IllegalStateException("Schema version 1 is currently the only supported format");
            }
            String accessWidener = null;
            List<String> entrypoints = new LinkedList<>();
            List<String> jars = new LinkedList<>();

            JsonElement accessWidenerJson = root.get("accessWidener");
            if (accessWidenerJson != null) {
                accessWidener = accessWidenerJson.getAsString();
            }

            JsonObject entrypointsJson = root.getAsJsonObject("entrypoints");
            if (entrypointsJson != null) {
                for (String key : entrypointsJson.keySet()) {
                    JsonArray jsonArray = entrypointsJson.getAsJsonArray(key);
                    for (JsonElement entrypointJson : jsonArray) {
                        if (entrypointJson.isJsonPrimitive()) {
                            entrypoints.add(entrypointJson.getAsString());
                        } else {
                            JsonObject entrypointJsonObject = entrypointJson.getAsJsonObject();
                            // required field
                            JsonElement value = entrypointJsonObject.get("value");
                            entrypoints.add(value.getAsString());
                        }
                    }
                }
            }

            JsonArray jarsJson = root.getAsJsonArray("jars");
            if (jarsJson != null) {
                for (JsonElement jarJson : jarsJson) {
                    JsonObject jarJsonObject = jarJson.getAsJsonObject();
                    JsonElement fileJson = jarJsonObject.get("file");
                    if (fileJson != null) {
                        jars.add(fileJson.getAsString());
                    }
                }
            }
            return new FabricModJson(entrypoints, accessWidener, jars);
        }
    }
}
