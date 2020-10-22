package cz.fmo.tabletennis;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PlayerTest {
    private Player player;

    @Before
    public void setUp() throws Exception {
        player = new Player("");
    }

    @After
    public void tearDown() throws Exception {
        player = null;
    }

    @Test
    public void getName() {
        String someName = "Hans Peter";
        player = new Player(someName);
        assertEquals(someName, player.getName());
        assertSame(someName, player.getName());
        player = new Player(null);
        assertNull(player.getName());
    }
}