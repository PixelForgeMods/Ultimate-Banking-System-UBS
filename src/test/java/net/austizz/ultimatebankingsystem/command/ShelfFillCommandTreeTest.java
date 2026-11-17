package net.austizz.ultimatebankingsystem.command;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShelfFillCommandTreeTest {
    @Test
    void adminShopExposesFillAndSelectionFinish() throws Exception {
        Class<?> commands = Class.forName(
                "net.austizz.ultimatebankingsystem.command.UBSAdminCommands",
                true, NeoForgeTestClassLoader.get());
        Method factory = commands.getDeclaredMethod("buildUbsRoot");
        factory.setAccessible(true);
        Object builder = factory.invoke(null);
        Object root = builder.getClass().getMethod("build").invoke(builder);

        Object shelves = child(child(child(root, "admin"), "shop"), "shelves");
        assertNotNull(child(shelves, "fill"));
        assertNotNull(child(shelves, "done"));
    }

    private static Object child(Object node, String name) throws Exception {
        Object child = node.getClass().getMethod("getChild", String.class).invoke(node, name);
        assertNotNull(child, "missing command node " + name);
        return child;
    }
}
