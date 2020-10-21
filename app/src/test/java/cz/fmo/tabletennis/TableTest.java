package cz.fmo.tabletennis;

import android.graphics.Point;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TableTest {
    private Table table;
    private Point[] corners;
    private Point[] net;
    @Before
    public void setUp() {
        corners = new Point[4];
        corners[0] = new Point(1,2);
        corners[1] = new Point(4,5);
        corners[2] = new Point(6,7);
        corners[3] = new Point(8,9);
        net = new Point[2];
        net[0] = new Point(2,3);
        net[1] = new Point(7,8);
        table = new Table(corners, net);
    }

    @After
    public void tearDown() {
        table = null;
        corners = null;
        net = null;
    }

    @Test
    public void getCornerDownLeft() {
        assertNotNull(table.getCornerDownLeft());
        assertEquals(table.getCornerDownLeft(), corners[0]);
    }

    @Test
    public void getCornerDownRight() {
        assertNotNull(table.getCornerDownRight());
        assertEquals(table.getCornerDownRight(), corners[1]);
    }

    @Test
    public void getCornerTopRight() {
        assertNotNull(table.getCornerTopRight());
        assertEquals(table.getCornerTopRight(), corners[2]);
    }

    @Test
    public void getFarNetEnd() {
        assertNotNull(table.getFarNetEnd());
        assertEquals(table.getFarNetEnd(), net[1]);
    }

    @Test
    public void getCloseNetEnd() {
        assertNotNull(table.getCloseNetEnd());
        assertEquals(table.getCloseNetEnd(), net[0]);
    }

    @Test
    public void getNet() {
        assertEquals(2, table.getNet().length);
        assertSame(net, table.getNet());
    }

    @Test
    public void getCornerTopLeft() {
        assertNotNull(table.getCornerTopLeft());
        assertEquals(table.getCornerTopLeft(), corners[3]);
    }

    @Test
    public void getCorners() {
        assertEquals(4, table.getCorners().length);
        assertSame(corners, table.getCorners());
    }

    @Test
    public void testThrowsExceptionOnInvalidAmountOfCorners() {
        for (int i=0; i<100; i++) {
            if(i != 4) {
                try {
                    table = new Table(new Point[i], new Point[2]);
                    fail();
                } catch (Table.NotFourCornersException ex) {
                    // should throw an error message
                    assertTrue(ex.getMessage().contains(String.valueOf(i)));
                }
            }
        }
    }

    @Test
    public void testThrowsExceptionOnInvalidNetEnds() {
        for (int i=0; i<100; i++) {
            if(i != 2) {
                try {
                    table = new Table(new Point[4], new Point[i]);
                    fail();
                } catch (Table.NotTwoNetEndsException ex) {
                    // should throw an error message
                    assertTrue(ex.getMessage().contains(String.valueOf(i)));
                }
            }
        }
    }

    @Test
    public void makeTableFromProperties() {
        Properties properties = new Properties();
        properties.setProperty("c1_x","147");
        properties.setProperty("c1_y","488");
        properties.setProperty("c2_x","1192");
        properties.setProperty("c2_y","487");
        properties.setProperty("c3_x","940");
        properties.setProperty("c3_y","367");
        properties.setProperty("c4_x","363");
        properties.setProperty("c4_y","365");
        properties.setProperty("n1_x","986");
        properties.setProperty("n1_y","491");
        properties.setProperty("n2_x","686");
        properties.setProperty("n2_y","490");
        table = Table.makeTableFromProperties(properties);
        assertEquals(4, table.getCorners().length);
        assertEquals(147, table.getCornerDownLeft().x);
        assertEquals(488, table.getCornerDownLeft().y);
        assertEquals(1192, table.getCornerDownRight().x);
        assertEquals(487, table.getCornerDownRight().y);
        assertEquals(940, table.getCornerTopRight().x);
        assertEquals(367, table.getCornerTopRight().y);
        assertEquals(363, table.getCornerTopLeft().x);
        assertEquals(365, table.getCornerTopLeft().y);
        assertEquals(2, table.getNet().length);
        assertEquals(986, table.getCloseNetEnd().x);
        assertEquals(491, table.getCloseNetEnd().y);
        assertEquals(686, table.getFarNetEnd().x);
        assertEquals(490, table.getFarNetEnd().y);
    }
}