package cz.fmo.tabletennis;

import android.graphics.Point;
import android.support.annotation.NonNull;

import java.util.Properties;

public class Table {
    private Point[] corners;
    private Point[] net;

    public Table(@NonNull Point[] corners, @NonNull Point[] net) {
        if (corners.length != 4) {
           throw new NotFourCornersException(corners.length);
        }

        if (net.length != 2) {
            throw new NotTwoNetEndsException(net.length);
        }
        this.corners = corners;
        this.net = net;
    }

    public Point getFarNetEnd() {
        return net[1];
    }

    public Point getCloseNetEnd() {
        return net[0];
    }

    public Point[] getNet() {
        return net;
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
        Point[] net = new Point[2];
        for(int i = 1; i<5; i++) {
            if(i <= 2) {
                x = Integer.parseInt(properties.getProperty("n"+i+"_x"));
                y = Integer.parseInt(properties.getProperty("n"+i+"_y"));
                net[i-1] = new Point(x,y);
            }
            x = Integer.parseInt(properties.getProperty("c"+i+"_x"));
            y = Integer.parseInt(properties.getProperty("c"+i+"_y"));
            corners[i-1] = new Point(x,y);
        }
        return new Table(corners, net);
    }

    static class NotFourCornersException extends RuntimeException {
        private static final String MESSAGE = "Table needs 4 points as corners, you provided: ";
        NotFourCornersException(int amountOfCorners) {
            super(MESSAGE + amountOfCorners);
        }
    }

    static class NotTwoNetEndsException extends RuntimeException {
        private static final String MESSAGE = "Net needs 2 points which mark the end and start of the net, you provided: ";
        NotTwoNetEndsException(int amountOfPoints) {
            super(MESSAGE + amountOfPoints);
        }
    }
}
