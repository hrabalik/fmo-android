package cz.fmo.tabletennis;

public enum MatchType {
    BO1(1, 1),
    BO3(3, 2),
    BO5(5, 3);

    public final int amountOfGames;
    public final int gamesNeededToWin;

    private MatchType(int amountOfGames, int gamesNeededToWin) {
        this.amountOfGames = amountOfGames;
        this.gamesNeededToWin = gamesNeededToWin;
    }
}
