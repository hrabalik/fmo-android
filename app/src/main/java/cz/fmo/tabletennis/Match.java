package cz.fmo.tabletennis;

import java.util.HashMap;
import java.util.Map;

public class Match implements MatchCallback {
    private Game[] games;
    private MatchType type;
    private Player playerLeft;
    private Player playerRight;
    private Map<Side, Integer> wins;
    private UICallback uiCallback;
    private Referee referee;

    public Match(MatchType type, String playerLeftName, String playerRightName, UICallback uiCallback) {
        this.type = type;
        this.wins = new HashMap<>();
        this.wins.put(Side.LEFT, 0);
        this.wins.put(Side.RIGHT,0);
        this.games = new Game[type.amountOfGames];
        this.playerLeft = new Player(playerLeftName);
        this.playerRight = new Player(playerRightName);
        this.uiCallback = uiCallback;
        this.referee = new Referee(Side.LEFT);
        startNewGame();
    }

    void startNewGame() {
        Game game = new Game(this, uiCallback);
        this.games[this.wins.get(Side.RIGHT) + this.wins.get(Side.LEFT)] = game;
        this.referee.setGame(game);
    }

    void end() {
        this.uiCallback.onMatchEnded();
    }

    @Override
    public void onWin(Side side) {
        int win = wins.get(side) + 1;
        wins.put(side, win);
        uiCallback.onWin(side, win);
        if (isMatchOver(win)) {
            end();
        } else {
            startNewGame();
        }
    }

    public Referee getReferee() {
        return referee;
    }

    private boolean isMatchOver(int wins) {
        return (wins >= this.type.gamesNeededToWin);
    }
}
