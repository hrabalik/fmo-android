package cz.fmo.tabletennis;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MatchTest {
    private Match match;
    private UICallback uiCallback;

    @Before
    public void setUp() throws Exception {
        uiCallback = mock(UICallback.class);
        match = new Match(MatchType.BO1, "Player 1", "Player 2", uiCallback);
    }

    @After
    public void tearDown() throws Exception {
        match = null;
        uiCallback = null;
    }

    @Test
    public void onWinInBO1Match() {
        match = spy(match);
        assertNotNull(match.getReferee());
        match.onWin(Side.RIGHT);
        verify(uiCallback, times(1)).onMatchEnded();
        verify(uiCallback, times(1)).onWin(Side.RIGHT, 1);
        verify(match, times(1)).end();
    }

    @Test
    public void onWinInBO3Match() {
        testWithMatchType(MatchType.BO3);
    }

    @Test
    public void onWinInBO5Match() {
        testWithMatchType(MatchType.BO5);
    }

    private void testWithMatchType(MatchType type) {
        match = new Match(type, "Player 1", "Player 2", uiCallback);
        int winsToEnd = type.gamesNeededToWin;
        for(int i = 0; i<winsToEnd-1; i++) {
            match.onWin(Side.LEFT);
            match.onWin(Side.RIGHT);
            verify(uiCallback, times(1)).onWin(Side.LEFT, i+1);
            verify(uiCallback, times(1)).onWin(Side.RIGHT, i+1);
            verify(uiCallback, times(0)).onMatchEnded();
        }
        // finally let one side win
        match.onWin(Side.RIGHT);
        verify(uiCallback, times(1)).onWin(Side.RIGHT, winsToEnd);
        verify(uiCallback, times(1)).onMatchEnded();
    }
}