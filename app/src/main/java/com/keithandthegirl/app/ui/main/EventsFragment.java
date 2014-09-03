package com.keithandthegirl.app.ui.main;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.keithandthegirl.app.R;
import com.keithandthegirl.app.db.model.EventConstants;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.TimeZone;

/**
 * Created by dmfrey on 3/21/14.
 */
public class EventsFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = EventsFragment.class.getSimpleName();

    EventCursorAdapter mAdapter;

    public static EventsFragment newInstance() {
        EventsFragment fragment = new EventsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader( int i, Bundle args ) {
        String[] projection = null;

        String selection = EventConstants.FIELD_ENDDATE + " > ?";

        DateTime now = new DateTime( DateTimeZone.UTC );
        String[] selectionArgs = new String[] { String.valueOf( now.getMillis() ) };

        CursorLoader cursorLoader = new CursorLoader( getActivity(), EventConstants.CONTENT_URI, projection, selection, selectionArgs, EventConstants.FIELD_ENDDATE );
        return cursorLoader;
    }

    @Override
    public void onLoadFinished( Loader<Cursor> cursorLoader, Cursor cursor ) {
        mAdapter.swapCursor( cursor );
    }

    @Override
    public void onLoaderReset( Loader<Cursor> cursorLoader ) {
        mAdapter.swapCursor( null );
    }

    @Override
    public void onActivityCreated( Bundle savedInstanceState ) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader( 0, getArguments(), this );
        mAdapter = new EventCursorAdapter( getActivity() );
        setListAdapter( mAdapter );
    }

    public void updateEventsView( long showNameId ) {
        Bundle args = new Bundle();
        getLoaderManager().restartLoader( 0, args, this );
    }

    private class EventCursorAdapter extends CursorAdapter {
        private LayoutInflater mInflater;

        DateTimeFormatter mFormatter = DateTimeFormat.forPattern( "MMM d, yyyy hh:mm aa" ).withZone( DateTimeZone.forTimeZone( TimeZone.getTimeZone( "America/New_York" ) ) );

        public EventCursorAdapter( Context context ) {
            super( context, null, false );

            mInflater = LayoutInflater.from( context );
        }

        @Override
        public View newView( Context context, Cursor cursor, ViewGroup parent ) {
            View view = mInflater.inflate( R.layout.event_item_row, parent, false );

            ViewHolder refHolder = new ViewHolder();
            refHolder.title = (TextView) view.findViewById( R.id.event_title );
            refHolder.startDate = (TextView) view.findViewById( R.id.event_start_date );
            refHolder.endDate = (TextView) view.findViewById( R.id.event_end_date );
            refHolder.location = (TextView) view.findViewById( R.id.event_location );
            refHolder.details = (TextView) view.findViewById( R.id.event_details );

            view.setTag( refHolder );

            return view;
        }

        @Override
        public void bindView( View view, Context context, Cursor cursor ) {

            ViewHolder mHolder = (ViewHolder) view.getTag();

            long start = cursor.getLong( cursor.getColumnIndex( EventConstants.FIELD_STARTDATE ) );
            long end = cursor.getLong( cursor.getColumnIndex( EventConstants.FIELD_ENDDATE ) );
            String details = cursor.getString(cursor.getColumnIndex(EventConstants.FIELD_DETAILS));

            mHolder.title.setText( cursor.getString( cursor.getColumnIndex( EventConstants.FIELD_TITLE ) ) );

            if( start > 0 ) {
                mHolder.startDate.setText( mFormatter.print(start) );
            }

            if( end > 0 ) {
                mHolder.endDate.setText( mFormatter.print(end) );
            }

            mHolder.location.setText( cursor.getString( cursor.getColumnIndex( EventConstants.FIELD_LOCATION ) ) );

            if( null != details && !"null".equals( details ) && !"".equals( details ) ) {

                details = "<p>" + details + "</p>";

                mHolder.details.setMovementMethod( LinkMovementMethod.getInstance() );
                mHolder.details.setText( Html.fromHtml( details ) );
            }

        }

    }

    private static class ViewHolder {
        TextView title;
        TextView startDate;
        TextView endDate;
        TextView location;
        TextView details;

        ViewHolder() { }
    }
}