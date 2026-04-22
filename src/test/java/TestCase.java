/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import net.ucanaccess.util.Logger;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-12-29 nsano initial version <br>
 */
@EnabledIf("localPropertiesExists")
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property
    String file = "src/test/resources/test.o";

    @Property(name = "mus")
    String mml = "src/test/resources/test.mus";

    @Property(name = "vavi.test.volume")
    double volume = 0.2;

    @Property
    String muapDotNet;

    @Property
    String dir;

    @Property
    String ext;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }

        System.setProperty("muap.volume", "%4.2f".formatted(volume));

        System.setProperty("muap.dir.dta", "tmp/MUAP/IV");
        System.setProperty("muap.dir.pcm", "tmp/MUAP/PCM");

        muap.console.Program.isTest = true;

Debug.println("volume: " + System.getProperty("muap.volume"));
    }

    @Test
    @DisplayName("play")
    void test1() throws Exception {
Debug.print(file);
        muap.player.Program.main(new String[]{file});
    }

    /**
     * @param dir separated by ';'
     * @param ext separated by ','
     */
    static List<Path> listFilesUnderDirFilteredByExt(String dir, String ext) {
Debug.println("dir: " + dir);
Debug.println("ext: " + ext);
        Predicate<Path> x = p -> Arrays.stream(ext.split(",")).anyMatch(e -> p.getFileName().toString().toUpperCase().endsWith(e));
        return Arrays.stream(dir.split(File.pathSeparator)).flatMap(d -> {
            try {
                return Files.walk(Paths.get(d)).filter(x);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).toList();
    }

    @Test
    @DisplayName("compile dir")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test2() throws Exception {
        Path testMML = Path.of("tmp/test_java.mus");
        Path testO = Path.of("tmp/test_java.o");
        Path testMML2 = Path.of("tmp/test_dotnet.mus");
        Path testO2 = Path.of("tmp/test_dotnet.o");

        Files.deleteIfExists(testMML);
        Files.deleteIfExists(testMML2);

        listFilesUnderDirFilteredByExt(dir, ext).forEach(path -> {
            try {
Debug.println(path);
                Files.copy(path, testMML, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(path, testMML2, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(testO);
                Files.deleteIfExists(testO2);

                // compile c#
Debug.println("compile c# --------");
                ProcessBuilder pb = new ProcessBuilder();
//                pb.inheritIO();
                Process p = pb.command(muapDotNet, testMML2.toRealPath().toString()).start();
                int r = p.waitFor();
                assertEquals(0, r);
                assertTrue(Files.exists(testO2), "c# compile failed");

                // compile java
Debug.println("compile java --------");
                muap.console.Program.main(new String[] {testMML.toString()});
                assertTrue(Files.exists(testO), "java compile failed");

                // compare
Debug.println("compare --------");
Debug.println("c#  : " + Files.size(testO2));
Debug.println("java: " + Files.size(testO));
                assertEquals(Files.size(testO2), Files.size(testO), "java output is different from the original");
            } catch (Exception e) {
Debug.println(e);
            }
        });
    }

    @Test
    @DisplayName("compile & compare & play")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test6() throws Exception {
Debug.println(mml);
        Path testMML = Path.of("tmp/test_java.mus");
        Path testO = Path.of("tmp/test_java.o");
        Path testMML2 = Path.of("tmp/test_dotnet.mus");
        Path testO2 = Path.of("tmp/test_dotnet.o");

        Files.copy(Path.of(mml), testMML, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of(mml), testMML2, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(testO);
        Files.deleteIfExists(testO2);

        // compile c#
Debug.println("compile c# --------");
        ProcessBuilder pb = new ProcessBuilder();
        pb.inheritIO();
        Process p = pb.command(muapDotNet, testMML2.toRealPath().toString()).start();
        int r = p.waitFor();
        assertEquals(0, r);
        assertTrue(Files.exists(testO2), "c# compile failed");

        // compile java
Debug.println("compile java --------");
        muap.console.Program.main(new String[] {testMML.toString()});
        assertTrue(Files.exists(testO), "java compile failed");

        // compare
Debug.println("compare --------");
Debug.println("c#  : " + Files.size(testO2));
Debug.println("java: " + Files.size(testO));
        assertEquals(Files.size(testO2), Files.size(testO), "java output is different from the original");

        // play
Debug.println("play --------");
        muap.player.Program.main(new String[] {testO.toString()});
    }
}
