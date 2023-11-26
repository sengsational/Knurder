package com.sengsational.knurder;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragmentUnused extends PreferenceFragmentCompat {
    private static final String TAG = SettingsFragmentUnused.class.getSimpleName();
    ListPreference mListPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_general, rootKey);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mListPreference = (ListPreference)  getPreferenceManager().findPreference("preference_key");
        mListPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Log.v(TAG, "onPreferenceChange running.");
                return false; // your code here
            }
        });
        return inflater.inflate(R.layout.activity_pop_tutorial, container, false);
    }
}
