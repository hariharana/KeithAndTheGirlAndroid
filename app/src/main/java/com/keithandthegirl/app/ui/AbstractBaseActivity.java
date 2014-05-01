package com.keithandthegirl.app.ui;


import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.keithandthegirl.app.MainApplication;
import com.keithandthegirl.app.R;
import com.keithandthegirl.app.db.KatgProvider;
import com.keithandthegirl.app.db.model.Live;
import com.keithandthegirl.app.db.model.WorkItem;

public abstract class AbstractBaseActivity extends ActionBarActivity {

    private static final String TAG = AbstractBaseActivity.class.getSimpleName();

    private ContentResolver mContentResolver;
    private Uri mUri;

    private BroadcastingObserver mBroadcastingObserver;

    private Drawable micOn, micOff;

    protected Account mAccount;


    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        Log.d( TAG, "onCreate : enter" );
        super.onCreate( savedInstanceState );

        mContentResolver = getContentResolver();

        mUri = new Uri.Builder()
                .scheme( "content://" )
                .authority( KatgProvider.AUTHORITY )
                .path( WorkItem.TABLE_NAME )
                .build();

        TableObserver observer = new TableObserver();

        mBroadcastingObserver = new BroadcastingObserver();

        mAccount = MainApplication.CreateSyncAccount( this );
        ContentResolver.setSyncAutomatically( mAccount, KatgProvider.AUTHORITY, true );

        micOn = getResources().getDrawable( R.drawable.ic_live_mic_on );
        micOff = getResources().getDrawable( R.drawable.ic_live_mic_off );

        mContentResolver.registerContentObserver( mUri, true, observer );

        Log.d( TAG, "onCreate : exit" );
    }

    @Override
    protected void onPause() {
        Log.d( TAG, "onPause : enter" );
        super.onPause();

        getContentResolver().unregisterContentObserver( mBroadcastingObserver );

        Log.d( TAG, "onPause : exit" );
    }

    @Override
    protected void onResume() {
        Log.d( TAG, "onResume : enter" );
        super.onResume();

        getContentResolver().
            registerContentObserver(
                    ContentUris.withAppendedId( Live.CONTENT_URI, 1 ),
                    true,
                    mBroadcastingObserver
            );

        Log.d( TAG, "onResume : exit" );
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate( R.menu.main, menu );

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu( Menu menu ) {
        super.onPrepareOptionsMenu( menu );

        boolean broadcasting = false;

        Cursor cursor = getContentResolver().query( ContentUris.withAppendedId( Live.CONTENT_URI, 1 ), null, null, null, null );
        if( cursor.moveToNext() ) {

            broadcasting = cursor.getInt( cursor.getColumnIndex( Live.FIELD_BROADCASTING ) ) == 0 ? false : true;

        }
        cursor.close();


        if( broadcasting ) {

            menu.findItem( R.id.action_broadcasting ).setEnabled( true );
            menu.findItem( R.id.action_broadcasting ).setIcon( micOn );

        } else {

            menu.findItem( R.id.action_broadcasting ).setEnabled( false );
            menu.findItem( R.id.action_broadcasting ).setIcon( micOff );

        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {

        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if( id == R.id.action_settings ) {
            return true;
        }

        switch( id ) {
            case R.id.action_settings :
                return true;

//            case R.id.action_work_items :
//
//                WorkFragment newFragment = new WorkFragment();
//
//                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
//                transaction.replace( R.id.container, newFragment );
//                transaction.addToBackStack( null );
//
//                // Commit the transaction
//                transaction.commit();
//
//                return true;

            case R.id.action_broadcasting :

                Toast.makeText( this, "KATG is broadcasting now!", Toast.LENGTH_LONG ).show();

                return true;

        }

        return super.onOptionsItemSelected( item );
    }

    @SuppressLint( "NewApi" )
    private class BroadcastingObserver extends ContentObserver {

        private final String TAG = BroadcastingObserver.class.getSimpleName();

        public BroadcastingObserver() {
            super( null );
        }

        @Override
        public void onChange( boolean selfChange ) {
            super.onChange( selfChange, null );
        }

        @Override
        public void onChange( boolean selfChange, Uri uri ) {
            Log.i( TAG, "onChange : enter" );

            invalidateOptionsMenu();

            Log.i( TAG, "onChange : exit" );
        }

    }

    public class TableObserver extends ContentObserver {

        private final String TAG = TableObserver.class.getSimpleName();

        public TableObserver() {
            super( null );
        }

        @Override
        public void onChange( boolean selfChange ) {
            Log.i( TAG, "onChange : enter" );
            super.onChange(selfChange);

            onChange( selfChange, null );

            Log.i(TAG, "onChange : exit");
        }

        @Override
        public void onChange( boolean selfChange, Uri uri ) {
            Log.i( TAG, "onChange : enter" );

            boolean syncActive = ContentResolver.isSyncActive( mAccount, KatgProvider.AUTHORITY );
            boolean syncPending = ContentResolver.isSyncPending( mAccount, KatgProvider.AUTHORITY);

            if( !syncActive && !syncPending ) {

                Bundle settingsBundle = new Bundle();
                settingsBundle.putBoolean( ContentResolver.SYNC_EXTRAS_MANUAL, true );
                settingsBundle.putBoolean( ContentResolver.SYNC_EXTRAS_EXPEDITED, true );

                ContentResolver.requestSync( mAccount, KatgProvider.AUTHORITY, settingsBundle );

            }

            Log.i( TAG, "onChange : exit" );
        }

    }

}
