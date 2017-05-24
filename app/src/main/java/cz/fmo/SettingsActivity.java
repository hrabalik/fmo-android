package cz.fmo;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;

import java.util.List;

public class SettingsActivity extends PreferenceActivity {
    private static SummaryUpdater sSummaryUpdater = new SummaryUpdater();

    private static void bindToSummaryUpdater(Preference preference, SummaryUpdater updater) {
        preference.setOnPreferenceChangeListener(updater);

        updater.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
    }

    private void setupActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (!super.onMenuItemSelected(featureId, item)) {
                NavUtils.navigateUpFromSameTask(this);
            }
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    public boolean onIsMultiPane() {
        return true;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || CapturePreferenceFragment.class.getName().equals(fragmentName)
                || VelocityPreferenceFragment.class.getName().equals(fragmentName)
                || AdvancedPreferenceFragment.class.getName().equals(fragmentName);
    }

    private static class SummaryUpdater implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);

            } else {
                preference.setSummary(stringValue);
            }
            return true;
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class PreferenceFragmentBase extends PreferenceFragment {
        private int mXmlResourceId = -1;

        void setXmlResourceId(int xmlResourceId) {
            mXmlResourceId = xmlResourceId;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(mXmlResourceId);
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class CapturePreferenceFragment extends PreferenceFragmentBase {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.setXmlResourceId(R.xml.pref_capture);
            super.onCreate(savedInstanceState);
            bindToSummaryUpdater(findPreference("facing"), sSummaryUpdater);
            bindToSummaryUpdater(findPreference("resolution"), sSummaryUpdater);
            bindToSummaryUpdater(findPreference("recordMode"), sSummaryUpdater);
        }
    }

    public static class VelocityPreferenceFragment extends PreferenceFragmentBase {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.setXmlResourceId(R.xml.pref_velocity);
            super.onCreate(savedInstanceState);
            bindToSummaryUpdater(findPreference("velocityEstimationMode"), sSummaryUpdater);
            bindToSummaryUpdater(findPreference("objectDiameterPicker"), sSummaryUpdater);
            bindToSummaryUpdater(findPreference("objectDiameterCustom"), sSummaryUpdater);
            bindToSummaryUpdater(findPreference("frameRate"), sSummaryUpdater);
        }
    }

    public static class AdvancedPreferenceFragment extends PreferenceFragmentBase {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.setXmlResourceId(R.xml.pref_advanced);
            super.onCreate(savedInstanceState);
        }
    }
}
