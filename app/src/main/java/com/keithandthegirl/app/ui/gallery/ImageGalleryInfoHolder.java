package com.keithandthegirl.app.ui.gallery;

import android.os.Parcel;
import android.os.Parcelable;

/**
* Created by Jeff on 8/31/2014.
* Copyright JeffInMadison.com 2014
*/
public class ImageGalleryInfoHolder implements Parcelable {
    private String mImageUrl;
    private String mDescription;
    private String mTitle;
    private boolean mIsExplicit;

    public ImageGalleryInfoHolder() {}

    public ImageGalleryInfoHolder(String imageUrl, String title, String description, int explicit) {
        mImageUrl = imageUrl;
        mTitle = title;
        mDescription = description;

        if (explicit > 0) {
            mIsExplicit = true;
        } else {
            mIsExplicit = false;
        }
    }

    public boolean isExplicit() {
        return mIsExplicit;
    }

    public void setExplicit(boolean isExplicit) {
        mIsExplicit = isExplicit;
    }

    public String getImageUrl() {
        return mImageUrl;
    }

    public void setImageUrl(final String imageUrl) {
        mImageUrl = imageUrl;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(final String title) {
        mTitle = title;
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(final String description) {
        mDescription = description;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mImageUrl);
        dest.writeString(this.mDescription);
        dest.writeString(this.mTitle);
        dest.writeByte(mIsExplicit ? (byte) 1 : (byte) 0);
    }

    private ImageGalleryInfoHolder(Parcel in) {
        this.mImageUrl = in.readString();
        this.mDescription = in.readString();
        this.mTitle = in.readString();
        this.mIsExplicit = in.readByte() != 0;
    }

    public static final Parcelable.Creator<ImageGalleryInfoHolder> CREATOR = new Parcelable.Creator<ImageGalleryInfoHolder>() {
        public ImageGalleryInfoHolder createFromParcel(Parcel source) {
            return new ImageGalleryInfoHolder(source);
        }

        public ImageGalleryInfoHolder[] newArray(int size) {
            return new ImageGalleryInfoHolder[size];
        }
    };
}
