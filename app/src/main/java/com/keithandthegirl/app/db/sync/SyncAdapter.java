package com.keithandthegirl.app.db.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.keithandthegirl.app.db.model.Detail;
import com.keithandthegirl.app.db.model.Endpoint;
import com.keithandthegirl.app.db.model.Episode;
import com.keithandthegirl.app.db.model.EpisodeGuests;
import com.keithandthegirl.app.db.model.Event;
import com.keithandthegirl.app.db.model.Guest;
import com.keithandthegirl.app.db.model.Image;
import com.keithandthegirl.app.db.model.Live;
import com.keithandthegirl.app.db.model.Show;
import com.keithandthegirl.app.db.model.WorkItem;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 *
 * Created by dmfrey on 3/10/14.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = SyncAdapter.class.getSimpleName();

    private static final DateTimeFormatter format = DateTimeFormat.forPattern( "MM/dd/yyyy HH:mm" ).withZone( DateTimeZone.forTimeZone( TimeZone.getTimeZone( "America/New_York" ) ) );
    private static final DateTimeFormatter formata = DateTimeFormat.forPattern( "M/d/yyyy hh:mm:ss a" ).withZone( DateTimeZone.forTimeZone( TimeZone.getTimeZone( "America/New_York" ) ) );

    // Whether there is a Wi-Fi connection.
    private static boolean wifiConnected = false;

    // Whether there is a mobile connection.
    private static boolean mobileConnected = false;

    // Global variables
    // Define a variable to contain a content resolver instance
    ContentResolver mContentResolver;
    SharedPreferences mSharedPreferences;
    Context mContext;

    /**
     * Set up the sync adapter
     */
    public SyncAdapter( Context context, boolean autoInitialize ) {
        super( context, autoInitialize );

        mContext = context;

        updateConnectedFlags( context );

        /*
         * If your app uses a content resolver, get an instance of it
         * from the incoming Context
         */
        mContentResolver = context.getContentResolver();
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences( context );

    }


    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    public SyncAdapter( Context context, boolean autoInitialize, boolean allowParallelSyncs ) {
        super( context, autoInitialize, allowParallelSyncs );

        mContext = context;

        updateConnectedFlags( context );

        /*
         * If your app uses a content resolver, get an instance of it
         * from the incoming Context
         */
        mContentResolver = context.getContentResolver();
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences( context );

    }

    // Checks the network connection and sets the wifiConnected and mobileConnected
    // variables accordingly.
    private void updateConnectedFlags( Context context ) {

        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService( Context.CONNECTIVITY_SERVICE );

        NetworkInfo activeInfo = connMgr.getActiveNetworkInfo();
        if( null != activeInfo && activeInfo.isConnected() ) {
            wifiConnected = activeInfo.getType() == ConnectivityManager.TYPE_WIFI;
            mobileConnected = activeInfo.getType() == ConnectivityManager.TYPE_MOBILE;
        } else {
            wifiConnected = false;
            mobileConnected = false;
        }

        enableHttpResponseCache();
    }

    /*
     * Specify the code you want to run in the sync adapter. The entire
     * sync adapter runs in a background thread, so you don't have to set
     * up your own background processing.
     */
    @Override
    public void onPerformSync( Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult ) {
        Log.v( TAG, "onPerformSync : enter" );

        /*
         * Put the data transfer code here.
         */
        SyncResult result = new SyncResult();
        try {

            Cursor cursor = provider.query( WorkItem.CONTENT_URI, null, WorkItem.FIELD_STATUS + "=?", new String[] { WorkItem.Status.NEVER.name() }, null );
            do {
                List<Job> jobs = queueScheduledWorkItems( provider );
                Log.i( TAG, "onPerformSync : " + jobs.size() + " scheduled to run" );

                executeJobs( provider, jobs );

                cursor = provider.query( WorkItem.CONTENT_URI, null, WorkItem.FIELD_STATUS + "=?", new String[] { WorkItem.Status.NEVER.name() }, null );
            } while( cursor.getCount() > 0 );

            cursor.close();

        } catch( RemoteException e ) {
            Log.e( TAG, "onPerformSync : error, RemoteException", e );

            result.hasHardError();
        } catch( IOException e ) {
            Log.e( TAG, "onPerformSync : error, IOException", e );

            result.hasHardError();
        }

        Log.v( TAG, "onPerformSync : exit" );
    }

    private List<Job> queueScheduledWorkItems( ContentProviderClient provider ) throws RemoteException, IOException {
        Log.v( TAG, "queueScheduledWorkItems : enter" );

        List<Job> jobs = new ArrayList<Job>();

        Cursor cursor = provider.query( WorkItem.CONTENT_URI, null, WorkItem.FIELD_STATUS + "=?", new String[] { WorkItem.Status.NEVER.name() }, null );
        while( cursor.moveToNext() ) {

            Job job = new Job();

            Long id = cursor.getLong( cursor.getColumnIndex( WorkItem._ID ) );
            job.setId( id );

            Endpoint.Type type = Endpoint.Type.valueOf( cursor.getString( cursor.getColumnIndex( WorkItem.FIELD_ENDPOINT ) ) );
            job.setType(type);

            String address = cursor.getString( cursor.getColumnIndex( WorkItem.FIELD_ADDRESS ) );
            String parameters = cursor.getString( cursor.getColumnIndex( WorkItem.FIELD_PARAMETERS ) );
            job.setUrl(address + parameters);

            WorkItem.Status status = WorkItem.Status.valueOf( cursor.getString( cursor.getColumnIndex( WorkItem.FIELD_STATUS ) ) );
            job.setStatus(status);

            jobs.add( job );
        }
        cursor.close();

        Log.v( TAG, "runScheduledWorkItems : exit" );
        return jobs;
    }

    private void executeJobs( ContentProviderClient provider, List<Job> jobs ) throws RemoteException, IOException {
        Log.v( TAG, "executeJobs : enter" );

        if( !jobs.isEmpty() ) {

            for( Job job : jobs ) {

                switch( job.getType() ) {

                    case OVERVIEW:
                        Log.v( TAG, "runScheduledWorkItems : refreshing shows" );

                        getShows( provider, job );

                        break;

                    case EVENTS:
                        Log.v( TAG, "runScheduledWorkItems : refreshing events" );

                        getEvents( provider, job );

                        break;

                    case LIVE:
                        Log.v( TAG, "runScheduledWorkItems : refreshing live status" );

                        getLives( provider, job );

                        break;

                    case LIST:
                        Log.v( TAG, "runScheduledWorkItems : refreshing episode list" );

                        getEpisodes( provider, job );

                        break;

                    case RECENT:
                        Log.v( TAG, "runScheduledWorkItems : refreshing recent episodes" );

                        getRecentEpisodes( provider, job );

                        break;

                    default:
                        Log.w( TAG, "runScheduledWorkItems : Scheduled '" + job.getType().name() + "' not supported" );

                }
            }
        }

        Log.v( TAG, "executeJobs : exit" );
    }

    private void getShows( ContentProviderClient provider, Job job ) throws RemoteException, IOException {
        Log.v(TAG, "getShows : enter");

        try {

            if( wifiConnected || mobileConnected ) {
                Log.v( TAG, "getShows : network is available" );

                JSONArray jsonArray = loadJsonArrayFromNetwork( job.getUrl() );
                Log.i( TAG, "getShows : jsonArray=" + jsonArray.toString() );
                processShows( jsonArray, provider, job );
            }

        } catch( Exception e ) {
            Log.e(TAG, "getShows : error", e);
        }

        Log.v( TAG, "getShows : exit" );
    }

    private void getEvents( ContentProviderClient provider, Job job ) throws RemoteException, IOException {
        Log.v( TAG, "getEvents : enter" );

        try {

            if( wifiConnected || mobileConnected ) {
                Log.v( TAG, "getEvents : network is available" );

                JSONObject json = loadJsonFromNetwork(job.getUrl());
                Log.i( TAG, "getEvents : json=" + json.toString() );
                processEvents(json, provider, job);
            }

        } catch( Exception e ) {
            Log.e(TAG, "getEvents : error", e);
        }

        Log.v( TAG, "getEvents : exit" );
    }

    private void getLives( ContentProviderClient provider, Job job ) throws RemoteException, IOException {
        Log.v(TAG, "getLives : enter");

        try {

            if( wifiConnected || mobileConnected ) {
                Log.v( TAG, "getEvents : network is available" );

                JSONObject json = loadJsonFromNetwork( job.getUrl() );
                Log.i( TAG, "getLives : json=" + json.toString() );
                processLives(json, provider, job);
            }

        } catch( Exception e ) {
            Log.e(TAG, "getLives : error", e);
        }

        Log.v( TAG, "getLives : exit" );
    }

    private void getEpisodes( ContentProviderClient provider, Job job ) throws RemoteException, IOException {
        Log.v( TAG, "getEpisodes : enter" );

        DateTime lastRun = new DateTime( DateTimeZone.UTC );
        ContentValues update = new ContentValues();
        update.put( WorkItem._ID, job.getId() );
        update.put(WorkItem.FIELD_LAST_MODIFIED_DATE, lastRun.getMillis());

        try {

            if( wifiConnected || mobileConnected ) {
                Log.v( TAG, "getEpisodes : network is available" );

                JSONArray jsonArray = loadJsonArrayFromNetwork(job.getUrl());
                Log.i( TAG, "getEpisodes : jsonArray=" + jsonArray.toString() );
                processEpisodes(jsonArray, provider, job.getType());
            }

            update.put( WorkItem.FIELD_LAST_RUN, lastRun.getMillis() );
            update.put( WorkItem.FIELD_STATUS, WorkItem.Status.OK.name() );

        } catch( Exception e ) {
            Log.e(TAG, "getEpisodes : error", e);

            update.put(WorkItem.FIELD_STATUS, WorkItem.Status.FAILED.name());
        } finally {
            provider.update( ContentUris.withAppendedId( WorkItem.CONTENT_URI, job.getId() ), update, null, null );
        }

        Log.v( TAG, "getEpisodes : exit" );
    }

    private void getRecentEpisodes( ContentProviderClient provider, Job job ) throws RemoteException, IOException {
        Log.v( TAG, "getRecentEpisodes : enter" );

        DateTime lastRun = new DateTime( DateTimeZone.UTC );
        ContentValues update = new ContentValues();
        update.put( WorkItem._ID, job.getId() );
        update.put(WorkItem.FIELD_LAST_MODIFIED_DATE, lastRun.getMillis());

        try {

            if( wifiConnected || mobileConnected ) {
                Log.v( TAG, "getRecentEpisodes : network is available" );

                JSONArray jsonArray = loadJsonArrayFromNetwork( job.getUrl() );
                Log.i( TAG, "getRecentEpisodes : jsonArray=" + jsonArray.toString() );
                processEpisodes( jsonArray, provider, job.getType() );
            }

            update.put( WorkItem.FIELD_LAST_RUN, lastRun.getMillis() );
            update.put( WorkItem.FIELD_STATUS, WorkItem.Status.OK.name() );

        } catch( Exception e ) {
            Log.e(TAG, "getRecentEpisodes : error", e);

            update.put(WorkItem.FIELD_STATUS, WorkItem.Status.FAILED.name());
        } finally {
            provider.update( ContentUris.withAppendedId( WorkItem.CONTENT_URI, job.getId() ), update, null, null );
        }

        Log.v( TAG, "getRecentEpisodes : exit" );
    }

    private void getShowDetails( ContentProviderClient provider, int showId ) throws RemoteException, IOException {
        Log.v( TAG, "getShowDetails : enter" );

        String address = "";
        Cursor cursor = provider.query( Endpoint.CONTENT_URI, null, Endpoint.FIELD_TYPE + "=?", new String[] { Endpoint.Type.DETAILS.name() }, null );
        while( cursor.moveToNext() ) {

            address = cursor.getString( cursor.getColumnIndex( Endpoint.FIELD_URL ) );

        }
        cursor.close();

        try {

            if( wifiConnected || mobileConnected ) {
                Log.v( TAG, "getShowDetails : network is available" );

                JSONObject json = loadJsonFromNetwork( address + "?showid=" + showId );
                Log.i( TAG, "getShowDetails : json=" + json.toString() );
                processEpisodeDetails(json, provider, showId);
            }

        } catch( Exception e ) {
            Log.e(TAG, "getShowDetails : error", e);
        }

        Log.v( TAG, "getShowDetails : exit" );
    }

    private JSONObject loadJsonFromNetwork( String url ) throws IOException, JSONException {
        Log.v( TAG, "loadJsonFromNetwork : enter" );
        InputStream stream = null;

        JSONObject json = null;
        String result = null;

        try {

            stream = downloadUrl( url );

            // json is UTF-8 by default
            BufferedReader reader = new BufferedReader( new InputStreamReader( stream , "UTF-8" ), 8 );
            StringBuilder sb = new StringBuilder();

            String line = null;
            while( ( line = reader.readLine() ) != null ) {
                sb.append( line + "\n" );
            }
            json = new JSONObject( sb.toString() );

        } finally {

            // Makes sure that the InputStream is closed after the app is
            // finished using it.
            if( null != stream  ) {
                stream.close();
            }

        }

        Log.v( TAG, "loadJsonFromNetwork : exit" );
        return json;
    }

    private JSONArray loadJsonArrayFromNetwork( String url ) throws IOException, JSONException {
        Log.v( TAG, "loadJsonArrayFromNetwork : enter" );
        InputStream stream = null;

        JSONArray jsonArray = null;
        String result = null;

        try {

            stream = downloadUrl( url );

            // json is UTF-8 by default
            BufferedReader reader = new BufferedReader( new InputStreamReader( stream , "UTF-8" ), 8 );
            StringBuilder sb = new StringBuilder();

            String line = null;
            while( ( line = reader.readLine() ) != null ) {
                sb.append( line + "\n" );
            }
            jsonArray = new JSONArray( sb.toString() );

        } finally {

            // Makes sure that the InputStream is closed after the app is
            // finished using it.
            if( null != stream  ) {
                stream.close();
            }

        }

        Log.v( TAG, "loadJsonArrayFromNetwork : exit" );
        return jsonArray;
    }

    // Given a string representation of a URL, sets up a connection and gets
    // an input stream.
    private InputStream downloadUrl( String address ) throws IOException {
        Log.v( TAG, "downloadUrl : enter" );

        long currentTime = System.currentTimeMillis();
        InputStream stream = null;

        TrafficStats.setThreadStatsTag( 0xF00D );
        try {
            URL url = new URL( address );
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout( 10000 /* milliseconds */ );
            conn.setConnectTimeout( 15000 /* milliseconds */ );
            conn.setRequestMethod( "GET" );
            conn.setDoInput( true );

            // Starts the query
            conn.connect();
            stream = conn.getInputStream();
        } finally {
            TrafficStats.clearThreadStatsTag();
        }

        Log.v( TAG, "downloadUrl : exit" );
        return stream;
    }

    private void enableHttpResponseCache() {
        try {
            long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
            File httpCacheDir = new File( mContext.getCacheDir(), "http" );
            Class.forName( "android.net.http.HttpResponseCache" )
                    .getMethod( "install", File.class, long.class )
                    .invoke( null, httpCacheDir, httpCacheSize );
        } catch( Exception httpResponseCacheNotAvailable ) {
            Log.d(TAG, "HTTP response cache is unavailable.");
        }
    }

    private void processShows( JSONArray jsonArray, ContentProviderClient provider, Job job ) throws RemoteException {
        Log.v( TAG, "processShows : enter" );

        DateTime lastRun = new DateTime( DateTimeZone.UTC );
        ContentValues update = new ContentValues();
        update.put( WorkItem._ID, job.getId() );
        update.put( WorkItem.FIELD_LAST_MODIFIED_DATE, lastRun.getMillis() );

        try {
            int count = 0, loaded = 0;

            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
            String[] projection = new String[] { Show._ID };

            ContentValues values;

            for( int i = 0; i < jsonArray.length(); i++ ) {
                JSONObject json = jsonArray.getJSONObject( i );
                Log.v( TAG, "processShows : json=" + json.toString() );

                int showNameId = json.getInt( "ShowNameId" );

                int vip = 0;
                try {
                    vip = json.getBoolean( "VIP" ) ? 1 : 0;
                } catch( Exception e ) {
                    Log.w( TAG, "processShows : VIP format is not valid" );
                }

                int sortOrder = 0;
                try {
                    sortOrder = json.getInt( "SortOrder" );
                } catch( Exception e ) {
                    Log.w( TAG, "processShows : SortOrder format is not valid" );
                }

                values = new ContentValues();
                values.put( Show._ID, showNameId );
                values.put( Show.FIELD_NAME, json.getString( "Name" ) );
                values.put( Show.FIELD_PREFIX, json.getString( "Prefix" ) );
                values.put( Show.FIELD_VIP, vip );
                values.put( Show.FIELD_SORTORDER, sortOrder );
                values.put( Show.FIELD_DESCRIPTION, json.getString( "Description" ) );
                values.put( Show.FIELD_COVERIMAGEURL, json.getString( "CoverImageUrl" ) );
                values.put( Show.FIELD_FORUMURL, json.getString( "ForumUrl" ) );
                values.put( Show.FIELD_PREVIEWURL, json.getString( "PreviewUrl" ) );
                values.put( Show.FIELD_LAST_MODIFIED_DATE, new DateTime( DateTimeZone.UTC ).getMillis() );

                Cursor cursor = provider.query( ContentUris.withAppendedId( Show.CONTENT_URI, showNameId ), projection, null, null, null );
                if( cursor.moveToFirst() ) {
                    Log.v( TAG, "processShows : show iteration, updating existing entry" );

                    Long id = cursor.getLong( cursor.getColumnIndexOrThrow( Show._ID ) );
                    ops.add(
                            ContentProviderOperation.newUpdate( ContentUris.withAppendedId( Show.CONTENT_URI, id ) )
                                    .withValues( values )
                                    .withYieldAllowed( true )
                                    .build()
                    );

                } else {
                    Log.v( TAG, "processShows : show iteration, adding new entry" );

                    ops.add(
                            ContentProviderOperation.newInsert( Show.CONTENT_URI )
                                    .withValues( values )
                                    .withYieldAllowed( true )
                                    .build()
                    );

                }
                cursor.close();
                count++;

                if( WorkItem.Status.NEVER.equals( job.getStatus() ) ) {

                    if( showNameId >  1 ) {
                        Log.v( TAG, "processShows : adding daily updates for spinoff shows" );

                        values = new ContentValues();
                        values.put( WorkItem.FIELD_NAME, "Refresh " + json.getString( "Name" ) );
                        values.put( WorkItem.FIELD_FREQUENCY, WorkItem.Type.DAILY.name() );
                        values.put( WorkItem.FIELD_ENDPOINT, Endpoint.Type.LIST.name() );
                        values.put( WorkItem.FIELD_ADDRESS, Endpoint.LIST );
                        values.put( WorkItem.FIELD_PARAMETERS, "?shownameid=" + showNameId );
                        values.put( WorkItem.FIELD_LAST_RUN, -1 );
                        values.put( WorkItem.FIELD_STATUS, WorkItem.Status.NEVER.name() );
                        values.put( WorkItem.FIELD_LAST_MODIFIED_DATE, new DateTime( DateTimeZone.UTC ).getMillis() );

                        ops.add(
                                ContentProviderOperation.newInsert(WorkItem.CONTENT_URI)
                                        .withValues(values)
                                        .withYieldAllowed(true)
                                        .build()
                        );
                        count++;

                    }

                }

                if( count > 100 ) {
                    Log.v( TAG, "processShows : applying batch for '" + count + "' transactions" );

                    if( !ops.isEmpty() ) {

                        ContentProviderResult[] results = provider.applyBatch( ops );
                        loaded += results.length;

                        if( results.length > 0 ) {
                            ops.clear();
                        }
                    }

                    count = 0;
                }
            }

            if( !ops.isEmpty() ) {
                Log.v( TAG, "processShows : applying final batch for '" + count + "' transactions" );

                ContentProviderResult[] results = provider.applyBatch( ops );
                loaded += results.length;

                if( results.length > 0 ) {
                    ops.clear();
                }
            }

            Log.i( TAG, "processShows : shows loaded '" + loaded + "'" );

            update.put( WorkItem.FIELD_LAST_RUN, lastRun.getMillis() );
            update.put( WorkItem.FIELD_STATUS, WorkItem.Status.OK.name() );

        } catch( Exception e ) {
            Log.e( TAG, "processShows : error", e );

            update.put( WorkItem.FIELD_STATUS, WorkItem.Status.FAILED.name() );
        } finally {
            provider.update( ContentUris.withAppendedId( WorkItem.CONTENT_URI, job.getId() ), update, null, null );
        }

        Log.v( TAG, "processShows : exit" );
    }

    private void processEvents( JSONObject jsonObject, ContentProviderClient provider, Job job ) throws RemoteException {
        Log.v( TAG, "processEvents : enter" );

        DateTime lastRun = new DateTime( DateTimeZone.UTC );
        ContentValues update = new ContentValues();
        update.put( WorkItem._ID, job.getId() );
        update.put(WorkItem.FIELD_LAST_MODIFIED_DATE, lastRun.getMillis());

        try {
            int count = 0, loaded = 0;

            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
            String[] projection = new String[] { Event._ID };

            ContentValues values;

            for( int i = 0; i < jsonObject.getJSONArray( "events" ).length(); i++ ) {
                JSONObject json = jsonObject.getJSONArray( "events" ).getJSONObject(i);
                Log.v( TAG, "processEvents : json=" + json.toString() );

                String eventId = json.getString( "eventid" );

                DateTime startDate = new DateTime();
                try {
                    startDate = format.parseDateTime( json.getString( "startdate" ) );
                } catch( JSONException e ) {
                    Log.w( TAG, "processEvents : startdate is not valid" );
                }
                startDate = startDate.withZone( DateTimeZone.UTC );

                DateTime endDate = new DateTime();
                try {
                    endDate = format.parseDateTime( json.getString( "enddate" ) );
                } catch( JSONException e ) {
                    Log.w( TAG, "processEvents : enddate is not valid" );
                }
                endDate = endDate.withZone( DateTimeZone.UTC );

                Log.v(TAG, "processEvents : startDate=" + startDate.toString() + ", endDate=" + endDate.toString());

                values = new ContentValues();
                values.put( Event.FIELD_EVENTID, eventId );
                values.put( Event.FIELD_TITLE, json.getString( "title" ) );
                values.put( Event.FIELD_LOCATION, json.getString( "location" ) );
                values.put( Event.FIELD_STARTDATE, startDate.getMillis() );
                values.put( Event.FIELD_ENDDATE, endDate.getMillis() );
                values.put( Event.FIELD_DETAILS, json.getString( "details" ) );
                values.put( Event.FIELD_LAST_MODIFIED_DATE, new DateTime( DateTimeZone.UTC ).getMillis() );

                Cursor cursor = provider.query( Event.CONTENT_URI, projection, Event.FIELD_EVENTID + "=?", new String[] { eventId }, null );
                if( cursor.moveToFirst() ) {
                    Log.v( TAG, "processEvents : show iteration, updating existing entry" );

                    Long id = cursor.getLong( cursor.getColumnIndexOrThrow( Event._ID ) );
                    ops.add(
                            ContentProviderOperation.newUpdate( ContentUris.withAppendedId( Event.CONTENT_URI, id ) )
                                    .withValues( values )
                                    .withYieldAllowed( true )
                                    .build()
                    );

                } else {
                    Log.v( TAG, "processEvents : show iteration, adding new entry" );

                    ops.add(
                            ContentProviderOperation.newInsert( Event.CONTENT_URI )
                                    .withValues( values )
                                    .withYieldAllowed( true )
                                    .build()
                    );

                }
                cursor.close();
                count++;

                if( count > 100 ) {
                    Log.v( TAG, "processEvents : applying batch for '" + count + "' transactions" );

                    if( !ops.isEmpty() ) {

                        ContentProviderResult[] results = provider.applyBatch( ops );
                        loaded += results.length;

                        if( results.length > 0 ) {
                            ops.clear();
                        }
                    }

                    count = 0;
                }
            }

            if( !ops.isEmpty() ) {
                Log.v( TAG, "processEvents : applying final batch for '" + count + "' transactions" );

                ContentProviderResult[] results = provider.applyBatch( ops );
                loaded += results.length;

                if( results.length > 0 ) {
                    ops.clear();
                }
            }

            Log.i( TAG, "processEvents : events loaded '" + loaded + "'" );

            update.put( WorkItem.FIELD_LAST_RUN, lastRun.getMillis() );
            update.put( WorkItem.FIELD_STATUS, WorkItem.Status.OK.name() );

        } catch( Exception e ) {
            Log.e( TAG, "processEvents : error", e );

            update.put(WorkItem.FIELD_STATUS, WorkItem.Status.FAILED.name());
        } finally {
            provider.update( ContentUris.withAppendedId( WorkItem.CONTENT_URI, job.getId() ), update, null, null );
        }

        Log.v(TAG, "processEvents : exit");
    }

    private void processLives( JSONObject jsonObject, ContentProviderClient provider, Job job ) throws RemoteException {
        Log.v( TAG, "processLives : enter" );

        DateTime lastRun = new DateTime( DateTimeZone.UTC );
        ContentValues update = new ContentValues();
        update.put( WorkItem._ID, job.getId() );
        update.put(WorkItem.FIELD_LAST_MODIFIED_DATE, lastRun.getMillis());

        int broadcasting = 0;
        try {
            broadcasting = jsonObject.getBoolean( "broadcasting" ) ? 1 : 0;

            ContentValues values = new ContentValues();
            values.put( Live.FIELD_BROADCASTING, broadcasting );
            values.put( Live.FIELD_LAST_MODIFIED_DATE, new DateTime( DateTimeZone.UTC ).getMillis() );

            provider.update( ContentUris.withAppendedId( Live.CONTENT_URI, 1 ), values, null, null );

            update.put( WorkItem.FIELD_LAST_RUN, lastRun.getMillis() );
            update.put( WorkItem.FIELD_STATUS, WorkItem.Status.OK.name() );

        } catch( Exception e ) {
            Log.w( TAG, "processLives : broadcasting format is not valid" );

            update.put(WorkItem.FIELD_STATUS, WorkItem.Status.FAILED.name());
        } finally {
            provider.update( ContentUris.withAppendedId( WorkItem.CONTENT_URI, job.getId() ), update, null, null );
        }

        Log.v( TAG, "processLives : exit" );
    }

    private void processEpisodes( JSONArray jsonArray, ContentProviderClient provider, Endpoint.Type type ) {
        Log.v( TAG, "processEpisodes : enter" );

        try {
            int count = 0, loaded = 0;
            List<Integer> detailsQueue = new ArrayList<Integer>();

            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
            String[] projection = new String[] { Episode._ID, Episode.FIELD_DOWNLOADED, Episode.FIELD_PLAYED, Episode.FIELD_LASTPLAYED };

            ContentValues values;

            for( int i = 0; i < jsonArray.length(); i++ ) {
                JSONObject json = jsonArray.getJSONObject( i );
                Log.v( TAG, "processEpisodes : json=" + json.toString() );

                int showId = json.getInt( "ShowId" );
                detailsQueue.add( showId );

                DateTime posted = new DateTime();
                try {
                    posted = formata.parseDateTime( json.getString( "PostedDate" ) );
                } catch( Exception e ) {
                    Log.w( TAG, "processEpisodes : PostedDate format is not valid" );
                }
                posted = posted.withZone( DateTimeZone.UTC );

                String previewUrl = "";
                try {
                    previewUrl = json.getString( "PreviewUrl" );
                } catch( JSONException e ) {
                    Log.w( TAG, "processEpisodes : PreviewUrl format is not valid or does not exist" );
                }

                String fileUrl = "", fileName = "";
                try {
                    fileUrl = json.getString( "FileUrl" );

                    Uri uri = Uri.parse( fileUrl );
                    if( null != uri.getLastPathSegment() ) {
                        fileName = uri.getLastPathSegment();
                        Log.v( TAG, "processEpisodes : fileName=" + fileName );
                    }
                } catch( JSONException e ) {
                    Log.w( TAG, "processEpisodes : FileUrl format is not valid or does not exist" );
                }

                int length = -1;
                try {
                    length = json.getInt("Length");
                } catch( Exception e ) {
                    Log.w( TAG, "processEpisodes : Length format is not valid or not present" );
                }

                int fileSize = -1;
                try {
                    fileSize = json.getInt( "FileSize" );
                } catch( Exception e ) {
                    Log.w( TAG, "processEpisodes : FileSize format is not valid or not present" );
                }

                int vip = 0;
                try {
                    vip = json.getInt( "public" );
                } catch( Exception e ) {
                    Log.w( TAG, "processEpisodes : Public format is not valid or not present" );
                }

                int showNameId = -1;
                try {
                    showNameId = json.getInt( "ShowNameId" );
                } catch( Exception e ) {
                    Log.w( TAG, "processEpisodes : ShowNameId format is not valid or not present" );
                }

                values = new ContentValues();
                values.put( Episode._ID, showId );
                values.put( Episode.FIELD_NUMBER, json.getInt( "Number" ) );
                values.put( Episode.FIELD_TITLE, json.getString("Title") );
                values.put( Episode.FIELD_PREVIEWURL, previewUrl );
                values.put( Episode.FIELD_FILEURL, fileUrl );
                values.put( Episode.FIELD_FILENAME, fileName );
                values.put( Episode.FIELD_LENGTH, length );
                values.put( Episode.FIELD_FILESIZE, fileSize );
                values.put( Episode.FIELD_TYPE, json.getInt( "Type" ) );
                values.put( Episode.FIELD_PUBLIC, vip );
                values.put( Episode.FIELD_TIMESTAMP, posted.getMillis() );
                values.put( Episode.FIELD_SHOWNAMEID, showNameId );
                values.put( Episode.FIELD_LAST_MODIFIED_DATE, new DateTime( DateTimeZone.UTC ).getMillis() );

                Cursor cursor = provider.query( ContentUris.withAppendedId( Episode.CONTENT_URI, showId ), projection, null, null, null );
                if( cursor.moveToFirst() ) {
                    Log.v( TAG, "processEpisodes : episode iteration, updating existing entry" );

                    long downloaded = cursor.getLong( cursor.getColumnIndex( Episode.FIELD_DOWNLOADED ) );
                    long played = cursor.getLong( cursor.getColumnIndex( Episode.FIELD_PLAYED ) );
                    long lastplayed = cursor.getLong( cursor.getColumnIndex( Episode.FIELD_LASTPLAYED ) );

                    values.put( Episode.FIELD_DOWNLOADED, downloaded );
                    values.put( Episode.FIELD_PLAYED, played );
                    values.put( Episode.FIELD_LASTPLAYED, lastplayed );

                    Long id = cursor.getLong( cursor.getColumnIndexOrThrow( Episode._ID ) );
                    ops.add(
                            ContentProviderOperation.newUpdate( ContentUris.withAppendedId( Episode.CONTENT_URI, id ) )
                                    .withValues( values )
                                    .withYieldAllowed( true )
                                    .build()
                    );

                } else {
                    Log.v( TAG, "processEpisodes : episode iteration, adding new entry" );

                    values.put( Episode.FIELD_DOWNLOADED, -1 );
                    values.put( Episode.FIELD_PLAYED, -1 );
                    values.put( Episode.FIELD_LASTPLAYED, -1 );

                    ops.add(
                            ContentProviderOperation.newInsert( Episode.CONTENT_URI )
                                    .withValues( values )
                                    .withYieldAllowed( true )
                                    .build()
                    );

                }
                cursor.close();
                count++;

                Log.v( TAG, "processEpisodes : processing guests" );
                JSONArray guests = json.getJSONArray( "Guests" );
                if( guests.length() > 0 ) {
                    for( int j = 0; j < guests.length(); j++ ) {
                        JSONObject guest = guests.getJSONObject( j );
                        Log.v( TAG, "processEpisodes : guest=" + guest.toString() );

                        int showGuestId = guest.getInt( "ShowGuestId" );

                        values = new ContentValues();
                        values.put( Guest._ID, showGuestId );
                        values.put( Guest.FIELD_REALNAME, guest.getString( "RealName" ) );
                        values.put( Guest.FIELD_DESCRIPTION, guest.getString( "Description" ) );
                        values.put( Guest.FIELD_PICTUREFILENAME, guest.getString( "PictureFilename" ) );
                        values.put( Guest.FIELD_URL1, guest.getString( "Url1" ) );
                        values.put( Guest.FIELD_URL2, guest.getString( "Url2" ) );
                        values.put( Guest.FIELD_PICTUREURL, guest.getString( "PictureUrl" ) );
                        values.put( Guest.FIELD_PICTUREURLLARGE, guest.getString( "PictureUrlLarge" ) );
                        values.put( Guest.FIELD_LAST_MODIFIED_DATE, new DateTime( DateTimeZone.UTC ).getMillis() );

                        cursor = provider.query( ContentUris.withAppendedId( Guest.CONTENT_URI, showGuestId ), null, null, null, null );
                        if( cursor.moveToFirst() ) {
                            Log.v( TAG, "processEpisodes : guest iteration, updating existing entry" );

                            Long id = cursor.getLong(cursor.getColumnIndexOrThrow( Guest._ID ) );
                            ops.add(
                                    ContentProviderOperation.newUpdate( ContentUris.withAppendedId( Guest.CONTENT_URI, id ) )
                                            .withValues( values )
                                            .withYieldAllowed( true )
                                            .build()
                            );

                        } else {
                            Log.v( TAG, "processEpisodes : guest iteration, adding new entry" );

                            ops.add(
                                    ContentProviderOperation.newInsert( Guest.CONTENT_URI )
                                            .withValues( values )
                                            .withYieldAllowed( true )
                                            .build()
                            );

                        }
                        cursor.close();
                        count++;

                        values = new ContentValues();
                        values.put( EpisodeGuests.FIELD_SHOWID, showId );
                        values.put( EpisodeGuests.FIELD_SHOWGUESTID, showGuestId );
                        values.put( Guest.FIELD_LAST_MODIFIED_DATE, new DateTime( DateTimeZone.UTC ).getMillis() );

                        cursor = provider.query( EpisodeGuests.CONTENT_URI, null, EpisodeGuests.FIELD_SHOWID + "=? and " + EpisodeGuests.FIELD_SHOWGUESTID + "=?", new String[] { String.valueOf( showId ), String.valueOf( showGuestId ) }, null );
                        if( cursor.moveToFirst() ) {
                            Log.v( TAG, "processEpisodes : episodeGuest iteration, updating existing entry" );

                            Long id = cursor.getLong(cursor.getColumnIndexOrThrow( EpisodeGuests._ID ) );
                            ops.add(
                                    ContentProviderOperation.newUpdate( ContentUris.withAppendedId( EpisodeGuests.CONTENT_URI, id ) )
                                            .withValues( values )
                                            .withYieldAllowed( true )
                                            .build()
                            );

                        } else {
                            Log.v( TAG, "processEpisodes : episodeGuest iteration, adding new entry" );

                            ops.add(
                                    ContentProviderOperation.newInsert( EpisodeGuests.CONTENT_URI )
                                            .withValues( values )
                                            .withYieldAllowed( true )
                                            .build()
                            );

                        }
                        cursor.close();
                        count++;

                    }
                }

                if( count > 100 ) {
                    Log.v( TAG, "processEpisodes : applying batch for '" + count + "' transactions" );

                    if( !ops.isEmpty() ) {

                        ContentProviderResult[] results = provider.applyBatch( ops );
                        loaded += results.length;

                        if( results.length > 0 ) {
                            ops.clear();
                        }
                    }

                    count = 0;
                }
            }

            if( !ops.isEmpty() ) {
                Log.v( TAG, "processEpisodes : applying final batch for '" + count + "' transactions" );

                ContentProviderResult[] results = provider.applyBatch( ops );
                loaded += results.length;

                if( results.length > 0 ) {
                    ops.clear();
                }
            }

            if( Endpoint.Type.RECENT.equals( type ) ) {
                if( !detailsQueue.isEmpty() ) {
                    Log.v( TAG, "processEpisodes : processing show details" );

                    for( int showId : detailsQueue ) {
                        getShowDetails( provider, showId );
                    }
                }
            }

            Log.i( TAG, "processEpisodes : episodes loaded '" + loaded + "'" );

        } catch( Exception e ) {
            Log.e( TAG, "processEpisodes : error", e );
        }

        Log.v( TAG, "processEpisodes : exit" );
    }

    private void processEpisodeDetails( JSONObject jsonObject, ContentProviderClient provider, int showId ) {
        Log.v( TAG, "processEpisodeDetails : enter" );

        try {
            int count = 0, loaded = 0;

            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
            String[] projection = new String[] { Detail._ID };

            ContentValues values = new ContentValues();
            values.put( Detail.FIELD_NOTES, jsonObject.getString( "notes" ) );
            values.put( Detail.FIELD_FORUMURL, jsonObject.getString( "forum_url" ) );
            values.put( Detail.FIELD_SHOWID, showId );
            values.put( Detail.FIELD_LAST_MODIFIED_DATE, new DateTime( DateTimeZone.UTC ).getMillis() );

            Cursor cursor = provider.query( Detail.CONTENT_URI, projection, Detail.FIELD_SHOWID + "=?", new String[] { String.valueOf( showId ) }, null );
            if( cursor.moveToFirst() ) {
                Log.v( TAG, "processEpisodeDetails : detail iteration, updating existing entry" );

                Long id = cursor.getLong( cursor.getColumnIndexOrThrow( Detail._ID ) );
                ops.add(
                        ContentProviderOperation.newUpdate( ContentUris.withAppendedId( Detail.CONTENT_URI, id ) )
                                .withValues( values )
                                .withYieldAllowed( true )
                                .build()
                );

            } else {
                Log.v( TAG, "processEpisodeDetails : detail iteration, adding new entry" );

                ops.add(
                        ContentProviderOperation.newInsert( Detail.CONTENT_URI )
                                .withValues( values )
                                .withYieldAllowed( true )
                                .build()
                );

            }
            cursor.close();

            for( int i = 0; i < jsonObject.getJSONArray( "images" ).length(); i++ ) {
                JSONObject json = jsonObject.getJSONArray( "images" ).getJSONObject( i );
                Log.v( TAG, "processEpisodeDetails : json=" + json.toString() );

                String mediaUrl = json.getString( "media_url" );

                values = new ContentValues();
                values.put( Image.FIELD_MEDIAURL, mediaUrl );
                values.put( Image.FIELD_TITLE, json.getString( "title" ) );
                values.put( Image.FIELD_DESCRIPTION, json.getString( "description" ) );
                values.put( Image.FIELD_LAST_MODIFIED_DATE, new DateTime( DateTimeZone.UTC ).getMillis() );

                cursor = provider.query( Image.CONTENT_URI, projection, Image.FIELD_MEDIAURL + "=?", new String[] { mediaUrl }, null );
                if( cursor.moveToFirst() ) {
                    Log.v( TAG, "processEpisodeDetails : image iteration, updating existing entry" );

                    Long id = cursor.getLong( cursor.getColumnIndexOrThrow( Image._ID ) );
                    ops.add(
                            ContentProviderOperation.newUpdate( ContentUris.withAppendedId( Image.CONTENT_URI, id ) )
                                    .withValues( values )
                                    .withYieldAllowed( true )
                                    .build()
                    );

                } else {
                    Log.v( TAG, "processEpisodeDetails : image iteration, adding new entry" );

                    ops.add(
                            ContentProviderOperation.newInsert( Image.CONTENT_URI )
                                    .withValues( values )
                                    .withYieldAllowed( true )
                                    .build()
                    );

                }
                cursor.close();
                count++;

                if( count > 100 ) {
                    Log.v( TAG, "processEpisodeDetails : applying batch for '" + count + "' transactions" );

                    if( !ops.isEmpty() ) {

                        ContentProviderResult[] results = provider.applyBatch( ops );
                        loaded += results.length;

                        if( results.length > 0 ) {
                            ops.clear();
                        }
                    }

                    count = 0;
                }
            }

            if( !ops.isEmpty() ) {
                Log.v( TAG, "processEvents : applying final batch for '" + count + "' transactions" );

                ContentProviderResult[] results = provider.applyBatch( ops );
                loaded += results.length;

                if( results.length > 0 ) {
                    ops.clear();
                }
            }

            Log.i( TAG, "processEvents : events loaded '" + loaded + "'" );

        } catch( Exception e ) {
            Log.e( TAG, "processEvents : error", e );
        }

        Log.v( TAG, "processEpisodeDetails : exit" );
    }

    private class Job {

        private Long id;
        private Endpoint.Type type;
        private String url;
        private WorkItem.Status status;

        public Long getId() {
            return id;
        }

        public void setId( Long id ) {
            this.id = id;
        }

        public Endpoint.Type getType() {
            return type;
        }

        public void setType( Endpoint.Type type ) {
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl( String url ) {
            this.url = url;
        }

        public WorkItem.Status getStatus() {
            return status;
        }

        public void setStatus( WorkItem.Status status ) {
            this.status = status;
        }

    }
}
