package com.keithandthegirl.app.ui.main;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.keithandthegirl.app.R;
import com.keithandthegirl.app.db.model.YoutubeConstants;
import com.keithandthegirl.app.sync.YoutubeDataFragment;
import com.keithandthegirl.app.ui.youtube.YoutubeFragmentActivity;
import com.squareup.picasso.Picasso;

/**
 * Created by dmfrey on 4/17/14.
 */
public class YoutubeFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = YoutubeFragment.class.getSimpleName();

    private static final String YOUTUBE_DATA_FRAGMENT_TAG = YoutubeDataFragment.class.getName();

    YoutubeCursorAdapter mAdapter;

    public static YoutubeFragment newInstance() {
        YoutubeFragment fragment = new YoutubeFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public YoutubeFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
        getLoaderManager().initLoader(0, getArguments(), this);
        mAdapter = new YoutubeCursorAdapter(getActivity());
        setListAdapter(mAdapter);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setFastScrollEnabled(true);
//        setRetainInstance(true);

        YoutubeDataFragment youtubeDataFragment = (YoutubeDataFragment) getChildFragmentManager().findFragmentByTag( YOUTUBE_DATA_FRAGMENT_TAG );
        if( null == youtubeDataFragment ) {

            youtubeDataFragment = (YoutubeDataFragment) instantiate( getActivity(), YoutubeDataFragment.class.getName() );
            youtubeDataFragment.setRetainInstance( true );

            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.add( youtubeDataFragment, YOUTUBE_DATA_FRAGMENT_TAG );
            transaction.commit();

        }

    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // use the cursor from the adapter to get item data. But don't close the cursor the cursorLoader
        // is responsible for that.
        Cursor c = ((YoutubeCursorAdapter) l.getAdapter()).getCursor();
        c.moveToPosition(position);

        String youtubeId = c.getString(c.getColumnIndex(YoutubeConstants.FIELD_YOUTUBE_ID));

        Intent intent = new Intent(getActivity(), YoutubeFragmentActivity.class);
        intent.putExtra(YoutubeFragmentActivity.YOUTUBE_VIDEO_KEY, youtubeId);
        startActivity(intent);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle args) {
        String[] projection = null;
        String selection = null;
        String[] selectionArgs = null;

        CursorLoader cursorLoader = new CursorLoader(getActivity(), YoutubeConstants.CONTENT_URI, projection, selection, selectionArgs, YoutubeConstants.FIELD_YOUTUBE_PUBLISHED + " DESC");
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mAdapter.swapCursor(null);
    }

    private class YoutubeCursorAdapter extends CursorAdapter {
        private LayoutInflater mInflater;

        public YoutubeCursorAdapter(Context context) {
            super(context, null, false);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = mInflater.inflate(R.layout.youtube_item_row, parent, false);

            ViewHolder refHolder = new ViewHolder();
            refHolder.thumbnail = (ImageView) view.findViewById(R.id.youtube_thumbnail);
            refHolder.title = (TextView) view.findViewById(R.id.youtube_title);
            refHolder.select = (ImageView) view.findViewById(R.id.youtube_select);

            view.setTag(refHolder);

            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {

            ViewHolder mHolder = (ViewHolder) view.getTag();

            mHolder.title.setText(cursor.getString(cursor.getColumnIndex(YoutubeConstants.FIELD_YOUTUBE_TITLE)));

            String thumbnail = cursor.getString(cursor.getColumnIndex(YoutubeConstants.FIELD_YOUTUBE_THUMBNAIL));
            if (null != thumbnail && !"".equals(thumbnail)) {
                mHolder.thumbnail.setVisibility(View.VISIBLE);
                Picasso.with(getActivity()).load(thumbnail).fit().centerCrop().into(mHolder.thumbnail);
            } else {
                mHolder.thumbnail.setVisibility(View.GONE);
            }
        }
    }

    private static class ViewHolder {

        ImageView thumbnail;
        TextView title;
        ImageView select;

        ViewHolder() {
        }
    }
}