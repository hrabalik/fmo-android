package cz.fmo.tabletennis;

import android.graphics.Point;
import android.support.annotation.NonNull;

import java.util.Properties;

public class Table {
    private Point[] corners;

    public Table(@NonNull Point[] corners) {
        if (corners.length != 4) {
           throw new NotFourCornersException(corners.length);
        }
        this.corners = corners;
    }

    public Point getCornerDownLeft() {
        return corners[0];
    }

    public Point getCornerDownRight() {
        return corners[1];
    }

    public Point getCornerTopRight() {
        return corners[2];
    }

    public Point getCornerTopLeft() {
        return corners[3];
    }

    public Point[] getCorners() {
        return corners;
    }

    public static Table makeTableFromProperties(Properties properties) {
        int x;
        int y;
        Point[] corners = new Point[4];
        for(int i = 1; i<5; i++) {
            x = Integer.parseInt(properties.getProperty("c"+i+"_x"));
            y = Integer.parseInt(properties.getProperty("c"+i+"_y"));
            corners[i-1] = new Point(x,y);
        }
        return new Table(corners);
    }

    static class NotFourCornersException extends RuntimeException {
        private static final String MESSAGE = "Table needs 4 points as corners, you provided: ";
        NotFourCornersException(int amountOfCorners) {
            super(MESSAGE + amountOfCorners);
        }
    }
}
