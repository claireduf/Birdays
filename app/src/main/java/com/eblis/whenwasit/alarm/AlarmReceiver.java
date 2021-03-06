/*
 * Copyright 2017 Evgeny Timofeev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eblis.whenwasit.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.widget.Toast;

import com.eblis.whenwasit.R;
import com.eblis.whenwasit.activities.DetailActivity;
import com.eblis.whenwasit.database.DbHelper;
import com.eblis.whenwasit.models.Person;
import com.eblis.whenwasit.utils.BirdaysApplication;
import com.eblis.whenwasit.utils.Constants;
import com.eblis.whenwasit.utils.ContactsHelper;
import com.eblis.whenwasit.utils.PermissionManager;
import com.eblis.whenwasit.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "com.eblis.whenwasit";

//    private void addNotifications(Context context, NotificationManager manager, SharedPreferences preferences, Intent intent, SortedMap<Integer, List<Person>> notifications) {
//        for (Map.Entry<Integer, List<Person>> entry : notifications.entrySet()) {
////            NotificationManager nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
////            Notification.Builder builder = new Notification.Builder(this);
////            builder.setContentTitle("Lanes");
////            builder.setContentText("Notification from Lanes"+value);
////            builder.setSmallIcon(R.drawable.ic_launcher);
////            builder.setLargeIcon(bitmap);
////            builder.setAutoCancel(true);
////            inboxStyle.setBigContentTitle("Enter Content Text");
////            inboxStyle.addLine("hi events "+value);
////            builder.setStyle(inboxStyle);
////            nManager.notify("App Name",NOTIFICATION_ID,builder.build());
//
//            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
//            inboxStyle.setBigContentTitle("WhenWasIt notifications");
//            inboxStyle.setSummaryText("The following notifications");
//            for (Person person : entry.getValue()) {
//                final String name = person.getName();
//                final String anniversaryLabel = person.getAnniversaryLabel();
//                final int daysToBirthday = entry.getKey();
//                final String when = getWhen(context, daysToBirthday);
//                final long timeStamp = person.getTimeStamp();
//
//
//                PendingIntent pendingIntent = TaskStackBuilder.create(context)
//                        .addNextIntentWithParentStack(getResultIntent(context, timeStamp, intent))
//                        .getPendingIntent(((int) timeStamp), PendingIntent.FLAG_UPDATE_CURRENT);
//
//                NotificationCompat.Builder builder = buildNotification(context, name, anniversaryLabel, when, daysToBirthday);
//                builder.setStyle(inboxStyle);
//
//                setDefaultsAndRingtone(preferences, builder);
//
//                builder.setContentIntent(pendingIntent);
//
//                Notification notification = builder.build();
//                notification.flags |= Notification.FLAG_AUTO_CANCEL;
//
//                if (manager != null) {
//                    manager.notify((int) timeStamp, notification);
//                }
//            }
//        }
//    }

    private void addNotification(Context context, NotificationManager manager, SharedPreferences preferences, Intent intent, Person person, int daysToBirthday) {
        final String name = person.getName();
        final String anniversaryLabel = person.getAnniversaryLabel();
        final String when = getWhen(context, daysToBirthday);
        final long timeStamp = person.getTimeStamp();

        PendingIntent pendingIntent = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(getResultIntent(context, timeStamp, intent))
                .getPendingIntent(((int) timeStamp), PendingIntent.FLAG_UPDATE_CURRENT);

        final Bitmap picture = Utils.getContactPicture(context, person);
        NotificationCompat.Builder builder = buildNotification(context, name, anniversaryLabel, when, daysToBirthday, picture);

        setDefaultsAndRingtone(preferences, builder);

        builder.setContentIntent(pendingIntent);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        if (manager != null) {
            manager.notify((int) timeStamp, notification);
        }
    }

    private String getWhen(Context context, int daysToBirthday) {
        if (daysToBirthday == 0) {
            return "Today";
        }

        final String[] dates = context.getResources().getStringArray(R.array.additional_notification_delay);
        final String[] entryValues = context.getResources().getStringArray(R.array.additional_notification_entry_values);
        for (int i = 0; i < entryValues.length; i++) {
            if (daysToBirthday == (Long.parseLong(entryValues[i]))) {
                return dates[i + 1];
            }
        }
        return null;

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final NotificationManager manager = ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
        final DbHelper dbHelper = new DbHelper(context);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean automaticImport = preferences.getBoolean(Constants.AUTOMATIC_CONTACT_IMPORT_KEY, true);
        if (automaticImport) {
            try {
                if (PermissionManager.readingContactsPermissionGranted(context)) {
                    ContactsHelper contactsHelper = new ContactsHelper(context, context.getContentResolver());
                    contactsHelper.updateContactsNow();
                    Toast.makeText(context, R.string.contacts_uploaded, Toast.LENGTH_SHORT).show();
                }
            }
            catch (Exception ex) {
                Toast.makeText(context, R.string.loading_contacts_error + "\n" + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        createNotificationChannel(context, manager);

        final Set<Long> additionalNotificationOffsets = getAdditionalNotificationOffsets(preferences);
        final List<Person> persons = dbHelper.query().getPersons();
        final SortedMap<Integer, List<Person>> notifications = new TreeMap<>();
        Collections.sort(persons, Collections.<Person>reverseOrder());
        for (Person person : persons) {
            final Integer daysToBirthday = shouldNotify(person, additionalNotificationOffsets);
            if (daysToBirthday != null) {
                if (!notifications.containsKey(daysToBirthday)) {
                    notifications.put(daysToBirthday, new ArrayList<Person>());
                }
                notifications.get(daysToBirthday).add(person);
                addNotification(context, manager, preferences, intent, person, daysToBirthday);
            }
        }
        //addNotifications(context, manager, preferences, intent, notifications);
    }

    private Integer shouldNotify(Person person, Set<Long> additionalNotificationOffsets) {
        final int daysToBirthday = Utils.daysLeft(person);
        if (daysToBirthday == 0 || additionalNotificationOffsets.contains((long) daysToBirthday)) {
            return daysToBirthday;
        }

        //null means we shouldn't notify
        return null;
    }

    /**
     * Creates intent to open DetailActivity on notification click
     */
    private Intent getResultIntent(Context context, long timeStamp, Intent intent) {
        Intent resultIntent = new Intent(context, DetailActivity.class);
        resultIntent.putExtra(Constants.TIME_STAMP, timeStamp);
        if (BirdaysApplication.isActivityVisible()) {
            resultIntent = intent;
        }
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return resultIntent;
    }

    private Set<Long> getAdditionalNotificationOffsets(SharedPreferences preferences) {
        final Set<String> strs =  preferences.getStringSet(Constants.ADDITIONAL_NOTIFICATION_KEY, Collections.<String>emptySet());
        final Set<Long> results = new HashSet<>();
        for (String str : strs) {
            try {
                results.add(Long.parseLong(str));
            }
            catch (NumberFormatException nfex) {
                // ignore invalid numbers
            }
        }

        return results;
    }


    /**
     * Creates notification channel for Android API 26+
     */
    private void createNotificationChannel(Context context, NotificationManager manager) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Builds default notification
     */
    private NotificationCompat.Builder buildNotification(Context context, String title, String anniversaryLabel, String text, int daysToBirthday, Bitmap contactPicture) {
        final int color = Utils.getNotificationColor(context, daysToBirthday);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(anniversaryLabel + ": " + text)
                .setContentInfo(anniversaryLabel)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(contactPicture)
                .setColor(color)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
    }

    /**
     * Avoids FileUriExposedException on Android API 24+
     */
    private void setDefaultsAndRingtone(SharedPreferences preferences, NotificationCompat.Builder builder) {
        String ringtone = preferences.getString(Constants.RINGTONE_KEY,
                Settings.System.DEFAULT_NOTIFICATION_URI.toString());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                setRingtone(builder, Uri.parse(ringtone));
            } catch (Exception e) {
                builder.setDefaults(NotificationCompat.DEFAULT_ALL);
            }
        } else {
            setRingtone(builder, Uri.parse(ringtone));
        }
    }

    /**
     * Set up notification tone, vibration and lights for notification
     */
    private void setRingtone(NotificationCompat.Builder builder, Uri ringtoneUri) {
        builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS);
        builder.setSound(ringtoneUri);
    }
}