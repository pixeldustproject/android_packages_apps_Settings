/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.gestures;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.hardware.AmbientDisplayConfiguration;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.SettingsPreferenceFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Top level fragment for gesture settings.
 * This will create individual switch preference for each gesture and handle updates when each
 * preference is updated
 */
public class GestureSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, Indexable {

    private static final String TAG = "GestureSettings";
    private static final String PREF_KEY_DOUBLE_TAP_POWER = "gesture_double_tap_power";
    private static final String PREF_KEY_DOUBLE_TWIST = "gesture_double_twist";
    private static final String PREF_KEY_SWIPE_DOWN_FINGERPRINT = "gesture_swipe_down_fingerprint";
    private static final String PREF_QUICK_PULLDOWN_FP = "quick_pulldown_fp";
    private static final String DEBUG_DOZE_COMPONENT = "debug.doze.component";
    private static final String FP_SWIPE_CALL_ACTIONS = "fp_swipe_call_actions";

    private List<GesturePreference> mPreferences;

    private int mFpSwipeCallActionsValue;
    private ListPreference mFpSwipeCallActions;
    private SwitchPreference mQuickPulldownFp;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.gesture_settings);
        Context context = getActivity();
        mPreferences = new ArrayList();

        // Double tap power for camera
        if (isCameraDoubleTapPowerGestureAvailable(getResources())) {
            int cameraDisabled = Secure.getInt(
                    getContentResolver(), Secure.CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED, 0);
            addPreference(PREF_KEY_DOUBLE_TAP_POWER, cameraDisabled == 0);
        } else {
            removePreference(PREF_KEY_DOUBLE_TAP_POWER);
        }

        // Fingerprint slide for notifications
        if (isSystemUINavigationAvailable(context)) {
            addPreference(PREF_KEY_SWIPE_DOWN_FINGERPRINT, isSystemUINavigationEnabled(context));

            mQuickPulldownFp = (SwitchPreference) findPreference(PREF_QUICK_PULLDOWN_FP);
            mQuickPulldownFp.setChecked(Settings.System.getIntForUser(getContentResolver(),
                    Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN_FP, 0, UserHandle.USER_CURRENT) == 1);
            mQuickPulldownFp.setOnPreferenceChangeListener(this);
            mQuickPulldownFp.setEnabled(isSystemUINavigationEnabled(context));

            mFpSwipeCallActions = (ListPreference) findPreference(FP_SWIPE_CALL_ACTIONS);
            mFpSwipeCallActionsValue = Settings.System.getIntForUser(getContentResolver(),
                    Settings.System.FP_SWIPE_CALL_ACTIONS, 0, UserHandle.USER_CURRENT);
            mFpSwipeCallActions.setValue(Integer.toString(mFpSwipeCallActionsValue));
            mFpSwipeCallActions.setSummary(mFpSwipeCallActions.getEntry());
            mFpSwipeCallActions.setOnPreferenceChangeListener(this);
        } else {
            removePreference(PREF_KEY_SWIPE_DOWN_FINGERPRINT);
            removePreference(PREF_QUICK_PULLDOWN_FP);
            removePreference(FP_SWIPE_CALL_ACTIONS);
        }

        // Double twist for camera mode
        if (isDoubleTwistAvailable(context)) {
            int doubleTwistEnabled = Secure.getInt(
                    getContentResolver(), Secure.CAMERA_DOUBLE_TWIST_TO_FLIP_ENABLED, 1);
            addPreference(PREF_KEY_DOUBLE_TWIST, doubleTwistEnabled != 0);
        } else {
            removePreference(PREF_KEY_DOUBLE_TWIST);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        RecyclerView listview = getListView();
        listview.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    for (GesturePreference pref : mPreferences) {
                        pref.setScrolling(true);
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    for (GesturePreference pref : mPreferences) {
                        pref.setScrolling(false);
                    }
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            }
        });
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        for (GesturePreference preference : mPreferences) {
            preference.onViewVisible();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        for (GesturePreference preference : mPreferences) {
            preference.onViewInvisible();
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (PREF_KEY_DOUBLE_TAP_POWER.equals(key)) {
            boolean enabled = (boolean) newValue;
            Secure.putInt(getContentResolver(),
                    Secure.CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED, enabled ? 0 : 1);
        } else if (PREF_KEY_SWIPE_DOWN_FINGERPRINT.equals(key)) {
            boolean enabled = (boolean) newValue;
            Secure.putInt(getContentResolver(),
                    Secure.SYSTEM_NAVIGATION_KEYS_ENABLED, enabled ? 1 : 0);
            mQuickPulldownFp.setEnabled(enabled);
        } else if (PREF_KEY_DOUBLE_TWIST.equals(key)) {
            boolean enabled = (boolean) newValue;
            Secure.putInt(getContentResolver(),
                    Secure.CAMERA_DOUBLE_TWIST_TO_FLIP_ENABLED, enabled ? 1 : 0);
        } else if (PREF_QUICK_PULLDOWN_FP.equals(key)) {
            boolean enabled = (boolean) newValue;
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN_FP, enabled ? 1 : 0,
                    UserHandle.USER_CURRENT);
        } else if (FP_SWIPE_CALL_ACTIONS.equals(key)) {
            mFpSwipeCallActionsValue = Integer.valueOf((String) newValue);
            int index = mFpSwipeCallActions.findIndexOfValue((String) newValue);
            mFpSwipeCallActions.setSummary(
                    mFpSwipeCallActions.getEntries()[index]);
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.FP_SWIPE_CALL_ACTIONS, mFpSwipeCallActionsValue,
                    UserHandle.USER_CURRENT);
        }
        return true;
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_gestures;
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.SETTINGS_GESTURES;
    }

    private static boolean isCameraDoubleTapPowerGestureAvailable(Resources res) {
        return res.getBoolean(
                com.android.internal.R.bool.config_cameraDoubleTapPowerGestureEnabled);
    }

    private static boolean isSystemUINavigationAvailable(Context context) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_supportSystemNavigationKeys);
    }

    private static boolean isSystemUINavigationEnabled(Context context) {
        return Secure.getInt(context.getContentResolver(), Secure.SYSTEM_NAVIGATION_KEYS_ENABLED, 0)
                == 1;
    }

    private static boolean isDoubleTwistAvailable(Context context) {
        return hasSensor(context, R.string.gesture_double_twist_sensor_name,
                R.string.gesture_double_twist_sensor_vendor);
    }

    private static boolean hasSensor(Context context, int nameResId, int vendorResId) {
        Resources resources = context.getResources();
        String name = resources.getString(nameResId);
        String vendor = resources.getString(vendorResId);
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(vendor)) {
            SensorManager sensorManager =
                    (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            for (Sensor s : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
                if (name.equals(s.getName()) && vendor.equals(s.getVendor())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addPreference(String key, boolean enabled) {
        GesturePreference preference = (GesturePreference) findPreference(key);
        preference.setChecked(enabled);
        preference.setOnPreferenceChangeListener(this);
        mPreferences.add(preference);
    }

    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                     boolean enabled) {
                ArrayList<SearchIndexableResource> result =
                        new ArrayList<SearchIndexableResource>();

                SearchIndexableResource sir = new SearchIndexableResource(context);
                sir.xmlResId = R.xml.gesture_settings;
                result.add(sir);

                return result;
            }

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                ArrayList<String> result = new ArrayList<String>();
                if (!isCameraDoubleTapPowerGestureAvailable(context.getResources())) {
                    result.add(PREF_KEY_DOUBLE_TAP_POWER);
                }
                if (!isSystemUINavigationAvailable(context)) {
                    result.add(PREF_KEY_SWIPE_DOWN_FINGERPRINT);
                }
                if (!isDoubleTwistAvailable(context)) {
                    result.add(PREF_KEY_DOUBLE_TWIST);
                }
                return result;
            }
        };

}
