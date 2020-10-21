package cz.fmo.tabletennis;

public interface UICallback {
    void onMatchEnded();
    void onScore (Side side, int score);
    void onWin (Side side, int wins);
}
