package com.keithandthegirl.app.ui.gallery;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.keithandthegirl.app.MainApplication;
import com.keithandthegirl.app.R;
import com.keithandthegirl.app.ui.custom.TouchImageView;
import com.keithandthegirl.app.utils.StringUtils;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class EpisodeImageGalleryFragment extends Fragment {
    private static final String ARG_HOLDER_LIST = "ARG_HOLDER_LIST";
    private static final String ARG_IMAGE_POSITION = "ARG_IMAGE_POSITION";

    public static final String STACK_NAME = EpisodeImageGalleryFragment.class.getName();
    private static boolean mShowExplicit;

    private ArrayList<ImageGalleryInfoHolder> mInfoListHolder;
    private int mPosition;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param position initial position.
     * @param imageGalleryInfoHolderList url list.
     * @return A new instance of fragment EpisodeImageGalleryFragment.
     */
    public static EpisodeImageGalleryFragment newInstance(int position, ArrayList<ImageGalleryInfoHolder> imageGalleryInfoHolderList) {
        EpisodeImageGalleryFragment fragment = new EpisodeImageGalleryFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_IMAGE_POSITION, position);
        args.putParcelableArrayList(ARG_HOLDER_LIST, imageGalleryInfoHolderList);
        fragment.setArguments(args);
        return fragment;
    }
    public EpisodeImageGalleryFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mInfoListHolder = getArguments().getParcelableArrayList(ARG_HOLDER_LIST);
            mPosition = getArguments().getInt(ARG_IMAGE_POSITION);
        }
        mShowExplicit = MainApplication.isExplicitAllowed();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View fragmentView = inflater.inflate(R.layout.fragment_episode_image_gallery, container, false);
        ViewPager viewPager = (ViewPager) fragmentView.findViewById(R.id.pager);
        viewPager.setAdapter(new ImageGalleryPagerAdapter(mInfoListHolder));
        viewPager.setCurrentItem(mPosition);
        return fragmentView;
    }

    static class ImageGalleryPagerAdapter extends PagerAdapter {

        private final ArrayList<ImageGalleryInfoHolder> mImageHolderList;

        public ImageGalleryPagerAdapter(final ArrayList<ImageGalleryInfoHolder> imageGalleryInfoHolderArrayList) {
            mImageHolderList = imageGalleryInfoHolderArrayList;
        }

        @Override
        public int getCount() {
            return mImageHolderList.size();
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {
            ImageGalleryInfoHolder imageGalleryInfoHolder = mImageHolderList.get(position);
            LayoutInflater layoutInflater = LayoutInflater.from(container.getContext());
            ViewPager viewPager = (ViewPager) container;
            View view = layoutInflater.inflate(R.layout.view_pager_episode_gallery_image, container, false);
            TouchImageView imageView = (TouchImageView) view.findViewById(R.id.galleryImageView);

            if (imageGalleryInfoHolder.isExplicit() && !mShowExplicit) {
                Picasso.with(container.getContext()).load(R.drawable.img_explicit_warning).into(imageView);
            } else {
                Picasso.with(container.getContext()).load(imageGalleryInfoHolder.getImageUrl()).into(imageView);
            }

            TextView imageTitleTextView = (TextView) view.findViewById(R.id.imageTitleTextView);
            String title = imageGalleryInfoHolder.getTitle();
            if (StringUtils.isNullOrEmpty(title)) {
                imageTitleTextView.setVisibility(View.GONE);
            } else {
                imageTitleTextView.setText(title);
                imageTitleTextView.setVisibility(View.VISIBLE);
            }

            TextView imageDescriptionTextView = (TextView) view.findViewById(R.id.imageDescriptionTextView);
            String description = imageGalleryInfoHolder.getDescription();
            if (StringUtils.isNullOrEmpty(description)) {
                imageDescriptionTextView.setVisibility(View.GONE);
            } else {
                imageDescriptionTextView.setText(description);
                imageDescriptionTextView.setVisibility(View.VISIBLE);
            }
            viewPager.addView(view);
            return view;
        }

        @Override
        public boolean isViewFromObject(final View view, final Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(final ViewGroup container, final int position, final Object object) {
            ViewPager viewPager = (ViewPager)container;
            viewPager.removeView((View) object);
        }
    }
}