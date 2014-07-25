package com.keithandthegirl.app.ui.shows;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.keithandthegirl.app.R;
import com.keithandthegirl.app.db.model.ShowConstants;
import com.keithandthegirl.app.ui.ShowsActivity;
import com.keithandthegirl.app.utils.Utils;
import com.squareup.picasso.Picasso;

/**
 * Created by dmfrey on 3/21/14.
 */
public class ShowsGridFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>, AdapterView.OnItemClickListener {

    private static final String TAG = ShowsGridFragment.class.getSimpleName();

    ShowCursorAdapter mAdapter;

    @Override
    public Loader<Cursor> onCreateLoader( int i, Bundle args ) {
        Log.v(TAG, "onCreateLoader : enter");

        String[] projection = { ShowConstants._ID, ShowConstants.FIELD_NAME, ShowConstants.FIELD_PREFIX, ShowConstants.FIELD_COVERIMAGEURL_200, ShowConstants.FIELD_VIP };

        String selection = null;

        String[] selectionArgs = null;

        CursorLoader cursorLoader = new CursorLoader( getActivity(), ShowConstants.CONTENT_URI, projection, selection, selectionArgs, ShowConstants.FIELD_SORTORDER );

        Log.v( TAG, "onCreateLoader : exit" );
        return cursorLoader;
    }

    @Override
    public void onLoadFinished( Loader<Cursor> cursorLoader, Cursor cursor ) {
        Log.v( TAG, "onLoadFinished : enter" );

        mAdapter.swapCursor( cursor );

        Log.v( TAG, "onLoadFinished : exit" );
    }

    @Override
    public void onLoaderReset( Loader<Cursor> cursorLoader ) {
        Log.v( TAG, "onLoaderReset : enter" );

        mAdapter.swapCursor( null );

        Log.v( TAG, "onLoaderReset : exit" );
    }

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        Log.v( TAG, "onCreate : enter" );
        super.onCreate( savedInstanceState );

        Log.v( TAG, "onCreate : exit" );
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
        Log.v( TAG, "onCreateView : enter" );

        final View rootView = inflater.inflate( R.layout.fragment_shows, container, false );

        Log.v( TAG, "onCreateView : exit" );
        return rootView;
    }

    @Override
    public void onActivityCreated( Bundle savedInstanceState ) {
        Log.v( TAG, "onActivityCreated : enter" );
        super.onActivityCreated( savedInstanceState );

        setRetainInstance( true );

        getLoaderManager().initLoader( 0, getArguments(), this );

        mAdapter = new ShowCursorAdapter( getActivity() );

        final GridView mGridView = (GridView) getActivity().findViewById( R.id.shows_gridview );
        mGridView.setAdapter( mAdapter );
        mGridView.setOnItemClickListener( this );

        Log.v( TAG, "onActivityCreated : exit" );
    }

    @Override
    public void onResume() {
        Log.v( TAG, "onResume : enter" );

        super.onResume();
        mAdapter.notifyDataSetChanged();

        Log.v( TAG, "onResume : exit" );
    }

    @TargetApi( Build.VERSION_CODES.JELLY_BEAN )
    @Override
    public void onItemClick( AdapterView<?> parent, View v, int position, long id ) {
        Log.v( TAG, "onItemClick : enter - position=" + position + ", id=" + id );

        final Intent i = new Intent( getActivity(), ShowsActivity.class );
        i.putExtra( ShowsActivity.SHOW_NAME_POSITION_KEY, position );
        if( Utils.hasJellyBean() ) {
            // makeThumbnailScaleUpAnimation() looks kind of ugly here as the loading spinner may
            // show plus the thumbnail image in GridView is cropped. so using
            // makeScaleUpAnimation() instead.
            ActivityOptions options = ActivityOptions.makeScaleUpAnimation( v, 0, 0, v.getWidth(), v.getHeight() );
            startActivity( i );
        } else {
            startActivity( i );
        }

        Log.v( TAG, "onItemClick : exit" );
    }

    private class ShowCursorAdapter extends CursorAdapter {
        private final String TAG = ShowCursorAdapter.class.getSimpleName();

        private LayoutInflater mInflater;

        public ShowCursorAdapter( Context context ) {
            super( context, null, false );
            mInflater = LayoutInflater.from( context );
        }

        @Override
        public View newView( Context context, Cursor cursor, ViewGroup parent ) {
            Log.v( TAG, "newView : enter" );

            View view = mInflater.inflate( R.layout.show_grid_item, parent, false );

            ViewHolder refHolder = new ViewHolder();
            refHolder.coverImage = (ImageView) view.findViewById( R.id.show_grid_item_coverimage );
            refHolder.coverImage.setScaleType( ImageView.ScaleType.CENTER_CROP );

            refHolder.vip = (TextView) view.findViewById( R.id.show_grid_item_vip );
            refHolder.name = (TextView) view.findViewById( R.id.show_grid_item_name );

            view.setTag( refHolder );

            Log.v( TAG, "newView : exit" );
            return view;
        }

        @Override
        public void bindView( View view, Context context, Cursor cursor ) {
            Log.v( TAG, "bindView : enter" );

            ViewHolder mHolder = (ViewHolder) view.getTag();

            String name = cursor.getString( cursor.getColumnIndex( ShowConstants.FIELD_NAME ) );
            String prefix = cursor.getString( cursor.getColumnIndex( ShowConstants.FIELD_PREFIX ) );
            String coverUrl = cursor.getString( cursor.getColumnIndex( ShowConstants.FIELD_COVERIMAGEURL_200 ) );
            boolean vip = cursor.getLong( cursor.getColumnIndex( ShowConstants.FIELD_VIP ) ) == 0 ? false : true;

            mHolder.name.setText( name );

            if( vip ) {
                mHolder.vip.setVisibility( View.VISIBLE );
            } else {
                mHolder.vip.setVisibility( View.GONE );
            }

            Picasso.with(getActivity()).load(coverUrl).fit().centerCrop().into(mHolder.coverImage);

            Log.v( TAG, "bindView : exit" );
        }
    }

    private static class ViewHolder {

        ImageView coverImage;
        TextView vip;
        TextView name;

        ViewHolder() { }

    }

}
