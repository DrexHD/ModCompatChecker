package net.earthcomputer.modcompatchecker.fabric;

import net.earthcomputer.modcompatchecker.config.Config;
import net.earthcomputer.modcompatchecker.config.Plugin;
import net.earthcomputer.modcompatchecker.indexer.Index;
import net.earthcomputer.modcompatchecker.indexer.Indexer;
import net.earthcomputer.modcompatchecker.util.ThreeState;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipOutputStream;

public class FabricPlugin implements Plugin {
    private final Set<String> entrypointClasses = new HashSet<>();

    @Override
    public String id() {
        return "fabric";
    }

    @Override
    public void initialize() {
        Config.registerSectionType(FabricUtil.FABRIC_SECTION);
    }

    @Override
    public void preIndexLibrary(Config config, Index index, Path libraryPath) throws IOException {
        preIndex(config, index, libraryPath, false);
    }

    @Override
    public void preIndexMod(Config config, Index index, Path modPath) throws IOException {
        preIndex(config, index, modPath, true);
    }

    public void preIndex(Config config, Index index, Path modPath, boolean checkEntryPoints) throws IOException {
        try (JarFile modJar = new JarFile(modPath.toFile())) {
            FabricModJson modJson = FabricModJson.load(modJar);
            if (modJson == null) {
                return;
            }
            if (checkEntryPoints) {
                for (List<String> entrypointCategory : modJson.entrypoints().values()) {
                    for (String entrypoint : entrypointCategory) {
                        entrypointClasses.add(entrypoint.replace('.', '/'));
                    }
                }
            }
            for (String jarEntry : modJson.jars()) {
                JarEntry libraryJar = modJar.getJarEntry(jarEntry);
                try (InputStream inputStream = modJar.getInputStream(libraryJar)) {
                    Path tempFile = Files.createTempFile(null, null);
                    OutputStream outputStream = Files.newOutputStream(tempFile);
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                    }
                    outputStream.close();
                    // TODO should these call preIndex as well
                    Indexer.indexJar(tempFile, index);
                }
            }
        }
    }

    @Override
    public ThreeState isClassAccessedViaReflection(String className) {
        return entrypointClasses.contains(className) ? ThreeState.TRUE : ThreeState.UNKNOWN;
    }
}
