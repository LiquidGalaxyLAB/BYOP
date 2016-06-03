/*
 * Copyright 2015 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gsoc.google.com.byop.utils.PW;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import gsoc.google.com.byop.R;

/**
 * This receiver starts the UriBeaconDiscoveryService.
 */
public class AutostartPwoDiscoveryServiceReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        // Make sure the user has opted in
        String preferencesKey = context.getString(R.string.main_prefs_key);
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean(context.getString(R.string.user_opted_in_flag), false)) {
            return;
        }

        Intent newIntent = new Intent(context, ScreenListenerService.class);
        context.startService(newIntent);
    }
}