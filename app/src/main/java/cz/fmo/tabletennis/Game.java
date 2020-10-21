package cz.fmo.tabletennis;

import java.util.HashMap;
import java.util.Map;

public class Game implements GameCallback {
    private int maxScore = 11;
    private Map<Side, Integer> scores;
    private MatchCallback matchCallback;
    private UICallback uiCallback;

    public Game (MatchCallback matchCallback, UICallback uiCallback) {
        scores = new HashMap<>();
        scores.put(Side.LEFT, 0);
        scores.put(Side.RIGHT, 0);
        this.matchCallback = matchCallback;
        this.uiCallback = uiCallback;
    }

    @Override
    public void onPoint(Side side) {
        int score = scores.get(side) + 1;
        scores.put(side, score);
        uiCallback.onScore(side, score);
        if (hasReachedMax(score)) {
            matchCallback.onWin(side);
        }
    }

    private boolean hasReachedMax(int score) {
        if (scores.get(Side.LEFT).equals(scores.get(Side.RIGHT)) && score == maxScore-1) {
            increaseMaxScore();
        }
        return (score >= maxScore);
    }

    private void increaseMaxScore() {
        maxScore++;
    }
}
