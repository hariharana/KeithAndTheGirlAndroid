package com.keithandthegirl.app.ui;

import android.database.Cursor;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.keithandthegirl.app.R;
import com.keithandthegirl.app.db.model.Show;
import com.keithandthegirl.app.utils.ImageUtils;

public class ShowsActivity extends ActionBarActivity implements ActionBar.OnNavigationListener, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ShowsActivity.class.getSimpleName();

    public static final String SHOW_NAME_POSITION_KEY = "showNamePositionId";

    /**
     * The serialization (saved instance state) Bundle key representing the
     * current dropdown position.
     */
    private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";

    ShowCursorAdapter mAdapter;
    int mSelectedNavigationItem;

    @Override
    public Loader<Cursor> onCreateLoader( int i, Bundle args ) {
        Log.v( TAG, "onCreateLoader : enter" );

        String[] projection = { Show._ID, Show.FIELD_NAME };

        String selection = null;

        String[] selectionArgs = null;

        CursorLoader cursorLoader = new CursorLoader( this, Show.CONTENT_URI, projection, selection, selectionArgs, Show.FIELD_SORTORDER );

        Log.v( TAG, "onCreateLoader : exit" );
        return cursorLoader;
    }

    @Override
    public void onLoadFinished( Loader<Cursor> cursorLoader, Cursor cursor ) {
        Log.v( TAG, "onLoadFinished : enter" );

        mAdapter.swapCursor( cursor );

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setSelectedNavigationItem( mSelectedNavigationItem );

        Log.v( TAG, "onLoadFinished : exit" );
    }

    @Override
    public void onLoaderReset( Loader<Cursor> cursorLoader ) {
        Log.v( TAG, "onLoaderReset : enter" );

        mAdapter.swapCursor( null );

        Log.v( TAG, "onLoaderReset : exit" );
    }

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        Log.v( TAG, "onCreate : enter" );
        super.onCreate( savedInstanceState );

        setContentView( R.layout.activity_shows );

        // Set up the action bar to show a dropdown list.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled( false );
        actionBar.setNavigationMode( ActionBar.NAVIGATION_MODE_LIST );

        // Show the Up button in the action bar.
        actionBar.setDisplayHomeAsUpEnabled( true );

        getSupportLoaderManager().initLoader( 0, null, this );

        Bundle extras = getIntent().getExtras();
        mSelectedNavigationItem = extras.getInt( SHOW_NAME_POSITION_KEY );
        Log.v( TAG, "onCreate : mSelectedNavigationItem=" + mSelectedNavigationItem );

        mAdapter = new ShowCursorAdapter( actionBar.getThemedContext() );

        // Set up the dropdown list navigation in the action bar.
        actionBar.setListNavigationCallbacks( mAdapter, this );

        Log.v( TAG, "onCreate : exit" );
    }

    @Override
    public void onRestoreInstanceState( Bundle savedInstanceState ) {

        // Restore the previously serialized current dropdown position.
        if( savedInstanceState.containsKey( STATE_SELECTED_NAVIGATION_ITEM ) ) {

            getSupportActionBar().setSelectedNavigationItem( savedInstanceState.getInt( STATE_SELECTED_NAVIGATION_ITEM ) );

        }

    }

    @Override
    public void onSaveInstanceState( Bundle outState ) {

        // Serialize the current dropdown position.
        outState.putInt( STATE_SELECTED_NAVIGATION_ITEM, getSupportActionBar().getSelectedNavigationIndex() );

    }


    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate( R.menu.shows, menu );

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

        return super.onOptionsItemSelected( item );
    }

    @Override
    public boolean onNavigationItemSelected( int position, long id ) {
        Log.v( TAG, "onNavigationItemSelected : enter - position=" + position + ", id=" + id );

        mSelectedNavigationItem = position;

        // When the given dropdown item is selected, show its contents in the
        // container view.
        getSupportFragmentManager().beginTransaction()
                .replace( R.id.container, ShowFragment.newInstance( id ) )
                .commit();

        Log.v( TAG, "onNavigationItemSelected : exit" );
        return true;
    }

    private class ShowCursorAdapter extends CursorAdapter {

        private final String TAG = ShowCursorAdapter.class.getSimpleName();

        private Context mContext;
        private LayoutInflater mInflater;

        public ShowCursorAdapter( Context context ) {
            super( context, null, false );

            mContext = context;
            mInflater = LayoutInflater.from( context );
        }

        @Override
        public View newView( Context context, Cursor cursor, ViewGroup parent ) {

            View view = mInflater.inflate( android.R.layout.simple_list_item_1, parent, false );

            ViewHolder refHolder = new ViewHolder();
            refHolder.name = (TextView) view.findViewById( android.R.id.text1 );

            view.setTag( refHolder );

            return view;
        }

        @Override
        public void bindView( View view, Context context, Cursor cursor ) {

            ViewHolder mHolder = (ViewHolder) view.getTag();

            String name = cursor.getString( cursor.getColumnIndex( Show.FIELD_NAME ) );
            mHolder.name.setText( name );

        }

    }

    private static class ViewHolder {

        TextView name;

        ViewHolder() { }

    }

}