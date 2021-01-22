package com.clevertap.android.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import com.clevertap.android.sdk.DBAdapter.Table;
import java.util.Iterator;
import org.json.JSONException;
import org.json.JSONObject;

public class DBManager extends BaseDatabaseManager {

    private DBAdapter dbAdapter;

    private final CTLockManager mCTLockManager;

    private final CleverTapInstanceConfig mConfig;

    public DBManager(CleverTapInstanceConfig config,
            CTLockManager ctLockManager) {
        mConfig = config;
        mCTLockManager = ctLockManager;
    }

    @Override
    public DBAdapter loadDBAdapter(final Context context) {
        if (dbAdapter == null) {
            dbAdapter = new DBAdapter(context, mConfig);
            dbAdapter.cleanupStaleEvents(DBAdapter.Table.EVENTS);
            dbAdapter.cleanupStaleEvents(DBAdapter.Table.PROFILE_EVENTS);
            dbAdapter.cleanupStaleEvents(DBAdapter.Table.PUSH_NOTIFICATION_VIEWED);
            dbAdapter.cleanUpPushNotifications();
        }
        return dbAdapter;
    }

    /**
     * Only call async
     */
    @Override
    public void clearQueues(final Context context) {
        synchronized (mCTLockManager.getEventLock()) {

            DBAdapter adapter = loadDBAdapter(context);
            DBAdapter.Table tableName = DBAdapter.Table.EVENTS;

            adapter.removeEvents(tableName);
            tableName = DBAdapter.Table.PROFILE_EVENTS;
            adapter.removeEvents(tableName);

            clearUserContext(context);
        }
    }

    //Session
    private void clearIJ(Context context) {
        final SharedPreferences prefs = StorageHelper.getPreferences(context, Constants.NAMESPACE_IJ);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        StorageHelper.persist(editor);
    }

    //Session
    private void clearLastRequestTimestamp(Context context) {
        StorageHelper.putInt(context, StorageHelper.storageKeyWithSuffix(mConfig, Constants.KEY_LAST_TS), 0);
    }

    //Session
    private void clearUserContext(final Context context) {
        clearIJ(context);
        clearFirstRequestTimestampIfNeeded(context);
        clearLastRequestTimestamp(context);
    }
    //Session
    private void clearFirstRequestTimestampIfNeeded(Context context) {
        StorageHelper.putInt(context, StorageHelper.storageKeyWithSuffix(mConfig, Constants.KEY_FIRST_TS), 0);
    }

    // helper extracts the cursor data from the db object

    @Override
    QueueCursor getPushNotificationViewedQueuedEvents(final Context context, final int batchSize,
            final QueueCursor previousCursor) {
        return getQueueCursor(context, DBAdapter.Table.PUSH_NOTIFICATION_VIEWED, batchSize, previousCursor);
    }

    @Override
    QueueCursor getQueueCursor(final Context context, final Table table, final int batchSize,
            final QueueCursor previousCursor) {
        synchronized (mCTLockManager.getEventLock()) {
            DBAdapter adapter = loadDBAdapter(context);
            DBAdapter.Table tableName = (previousCursor != null) ? previousCursor.getTableName() : table;

            // if previousCursor that means the batch represented by the previous cursor was processed so remove those from the db
            if (previousCursor != null) {
                adapter.cleanupEventsFromLastId(previousCursor.getLastId(), previousCursor.getTableName());
            }

            // grab the new batch
            QueueCursor newCursor = new QueueCursor();
            newCursor.setTableName(tableName);
            JSONObject queuedDBEvents = adapter.fetchEvents(tableName, batchSize);
            newCursor = updateCursorForDBObject(queuedDBEvents, newCursor);

            return newCursor;
        }
    }

    @Override
    QueueCursor getQueuedDBEvents(final Context context, final int batchSize, final QueueCursor previousCursor) {

        synchronized (mCTLockManager.getEventLock()) {
            QueueCursor newCursor = getQueueCursor(context, DBAdapter.Table.EVENTS, batchSize, previousCursor);

            if (newCursor.isEmpty() && newCursor.getTableName().equals(DBAdapter.Table.EVENTS)) {
                newCursor = getQueueCursor(context, DBAdapter.Table.PROFILE_EVENTS, batchSize, null);
            }

            return newCursor.isEmpty() ? null : newCursor;
        }
    }

    @SuppressWarnings("SameParameterValue")
    QueueCursor getQueuedEvents(final Context context, final int batchSize, final QueueCursor previousCursor,
            final EventGroup eventGroup) {
        if (eventGroup == EventGroup.PUSH_NOTIFICATION_VIEWED) {
            mConfig.getLogger().verbose(mConfig.getAccountId(), "Returning Queued Notification Viewed events");
            return getPushNotificationViewedQueuedEvents(context, batchSize, previousCursor);
        } else {
            mConfig.getLogger().verbose(mConfig.getAccountId(), "Returning Queued events");
            return getQueuedDBEvents(context, batchSize, previousCursor);
        }
    }

    //Event
    @Override
    void queueEventToDB(final Context context, final JSONObject event, final int type) {
        DBAdapter.Table table = (type == Constants.PROFILE_EVENT) ? DBAdapter.Table.PROFILE_EVENTS
                : DBAdapter.Table.EVENTS;
        queueEventInternal(context, event, table);
    }

    @Override
    void queuePushNotificationViewedEventToDB(final Context context, final JSONObject event) {
        queueEventInternal(context, event, DBAdapter.Table.PUSH_NOTIFICATION_VIEWED);
    }

    @Override
    QueueCursor updateCursorForDBObject(final JSONObject dbObject, final QueueCursor cursor) {
        if (dbObject == null) {
            return cursor;
        }

        Iterator<String> keys = dbObject.keys();
        if (keys.hasNext()) {
            String key = keys.next();
            cursor.setLastId(key);
            try {
                cursor.setData(dbObject.getJSONArray(key));
            } catch (JSONException e) {
                cursor.setLastId(null);
                cursor.setData(null);
            }
        }

        return cursor;
    }

    private void queueEventInternal(final Context context, final JSONObject event, DBAdapter.Table table) {
        synchronized (mCTLockManager.getEventLock()) {
            DBAdapter adapter = loadDBAdapter(context);
            int returnCode = adapter.storeObject(event, table);

            if (returnCode > 0) {
                mConfig.getLogger().debug(mConfig.getAccountId(), "Queued event: " + event.toString());
                mConfig.getLogger()
                        .verbose(mConfig.getAccountId(),
                                "Queued event to DB table " + table + ": " + event.toString());
            }
        }
    }
}