package cz.fmo;

import android.support.test.rule.ActivityTestRule;
import android.widget.TextView;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

public class BenchmarkingActivityTest {
    private BenchmarkingActivity benchmarkingActivity;

    @Rule
    public ActivityTestRule<BenchmarkingActivity> bActivityTestRule = new ActivityTestRule<BenchmarkingActivity>(BenchmarkingActivity.class);

    @Before
    public void setUp() throws Exception {
        benchmarkingActivity = bActivityTestRule.getActivity();
    }

    @After
    public void tearDown() throws Exception {
        benchmarkingActivity.finish();
        benchmarkingActivity = null;
    }

    @Test
    public void testFindViewsInActivityTest() {
        TextView log = benchmarkingActivity.findViewById(R.id.benchmarking_log);
        assertNotNull(log);
    }

    @Test
    public void testIfBenchmarkingWorks() {
        TextView log = benchmarkingActivity.findViewById(R.id.benchmarking_log);
        // test if benchmarking generates Text -> visible on the screen
        String[] benchmarkTexts = getBenchmarkStrings(log);
        assertNotEquals(benchmarkTexts[0], benchmarkTexts[1]);
        // test if benchmarking can be paused -> no changes on the screen
        benchmarkingActivity.onPause();
        benchmarkTexts = getBenchmarkStrings(log);
        assertEquals(benchmarkTexts[0],benchmarkTexts[1]);
    }

    private String[] getBenchmarkStrings(TextView log) {
        try {
            Thread.sleep(1000);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        String benchmarkTextBefore = log.getText().toString();
        try {
            Thread.sleep(6000);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        String benchmarkTextAfter = log.getText().toString();
        return new String[]{benchmarkTextBefore, benchmarkTextAfter};
    }
}