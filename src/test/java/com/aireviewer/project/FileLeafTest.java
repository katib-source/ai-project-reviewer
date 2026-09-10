package com.aireviewer.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLeafTest {

    @Test
    void exposesItsOwnNamePathAndSize() {
        FileLeaf leaf = new FileLeaf("Main.java", Path.of("src/Main.java"), 1234L);

        assertEquals("Main.java", leaf.name());
        assertEquals(Path.of("src/Main.java"), leaf.path());
        assertEquals(1234L, leaf.sizeInBytes());
    }

    @Test
    void isNeverADirectory() {
        FileLeaf leaf = new FileLeaf("Main.java", Path.of("src/Main.java"), 0L);

        assertFalse(leaf.isDirectory());
    }

    @Test
    void hasNoChildren() {
        FileLeaf leaf = new FileLeaf("Main.java", Path.of("src/Main.java"), 0L);

        assertTrue(leaf.children().isEmpty());
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class,
                () -> new FileLeaf(null, Path.of("src/Main.java"), 0L));
    }

    @Test
    void rejectsNullPath() {
        assertThrows(NullPointerException.class,
                () -> new FileLeaf("Main.java", null, 0L));
    }

    @Test
    void rejectsNegativeSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new FileLeaf("Main.java", Path.of("src/Main.java"), -1L));
    }
}
