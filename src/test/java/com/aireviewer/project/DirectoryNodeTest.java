package com.aireviewer.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectoryNodeTest {

    @Test
    void exposesItsOwnNameAndPath() {
        DirectoryNode dir = new DirectoryNode("src", Path.of("src"));

        assertEquals("src", dir.name());
        assertEquals(Path.of("src"), dir.path());
    }

    @Test
    void isAlwaysADirectory() {
        DirectoryNode dir = new DirectoryNode("src", Path.of("src"));

        assertTrue(dir.isDirectory());
    }

    @Test
    void emptyDirectoryHasNoChildrenAndZeroSize() {
        DirectoryNode dir = new DirectoryNode("src", Path.of("src"));

        assertTrue(dir.children().isEmpty());
        assertEquals(0L, dir.sizeInBytes());
    }

    @Test
    void sizeIsTheSumOfDirectChildren() {
        DirectoryNode dir = new DirectoryNode("src", Path.of("src"));
        dir.addChild(new FileLeaf("A.java", Path.of("src/A.java"), 100L));
        dir.addChild(new FileLeaf("B.java", Path.of("src/B.java"), 250L));

        assertEquals(350L, dir.sizeInBytes());
        assertEquals(2, dir.children().size());
    }

    @Test
    void sizeIsSummedRecursivelyThroughNestedDirectories() {
        DirectoryNode nested = new DirectoryNode("nested", Path.of("src/nested"));
        nested.addChild(new FileLeaf("C.java", Path.of("src/nested/C.java"), 40L));

        DirectoryNode dir = new DirectoryNode("src", Path.of("src"));
        dir.addChild(new FileLeaf("A.java", Path.of("src/A.java"), 100L));
        dir.addChild(nested);

        assertEquals(140L, dir.sizeInBytes());
    }

    @Test
    void childrenViewIsUnmodifiable() {
        DirectoryNode dir = new DirectoryNode("src", Path.of("src"));

        assertThrows(UnsupportedOperationException.class,
                () -> dir.children().add(new FileLeaf("A.java", Path.of("src/A.java"), 1L)));
    }

    @Test
    void rejectsNullChild() {
        DirectoryNode dir = new DirectoryNode("src", Path.of("src"));

        assertThrows(NullPointerException.class, () -> dir.addChild(null));
    }

    @Test
    void rejectsNullNameOrPath() {
        assertThrows(NullPointerException.class, () -> new DirectoryNode(null, Path.of("src")));
        assertThrows(NullPointerException.class, () -> new DirectoryNode("src", null));
    }
}
