package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class StreamReminderService implements TickleListener {
    private static final String TAG = StreamReminderService.class.getSimpleName();
    private static StreamReminderService sInstance;
    private final MediaItemManager mItemManager;
    private final List<Video> mItems;
    private final Context mContext;
    private Disposable mMetadataAction;

    private StreamReminderService(Context context) {
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mItems = new ArrayList<>();
        mContext = context.getApplicationContext();
    }

    public static StreamReminderService instance(Context context) {
        if (sInstance == null) {
            sInstance = new StreamReminderService(context);
        }

        return sInstance;
    }

    public boolean isReminderSet(Video video) {
        return Helpers.containsIf(mItems, item -> video.videoId.equals(item.videoId));
    }

    public void toggleReminder(Video video) {
        if (video.videoId == null || !video.isUpcoming) {
            return;
        }

        List<Video> removed = Helpers.removeIf(mItems, item -> video.videoId.equals(item.videoId));

        if (removed == null) {
            mItems.add(video);
        }

        checkListener();
    }

    private void checkListener() {
        if (mItems.isEmpty()) {
            TickleManager.instance().removeListener(this);
        } else {
            TickleManager.instance().addListener(this);
        }
    }

    @Override
    public void onTickle() {
        if (mItems.isEmpty()) {
            checkListener();
            return;
        }

        RxUtils.disposeActions(mMetadataAction);

        List<Observable<MediaItemMetadata>> observables = toObservables();

        mMetadataAction = Observable.mergeDelayError(observables)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::processMetadata,
                        error -> Log.e(TAG, "loadMetadata error: %s", error.getMessage())
                );
    }

    private void processMetadata(MediaItemMetadata metadata) {
        String videoId = metadata.getVideoId();
        if (!metadata.isUpcoming() && videoId != null) {
            Utils.movePlayerToForeground(mContext);
            PlaybackPresenter.instance(mContext).openVideo(videoId);
            Helpers.removeIf(mItems, item -> videoId.equals(item.videoId));
            checkListener();
        }
    }

    private List<Observable<MediaItemMetadata>> toObservables() {
        List<Observable<MediaItemMetadata>> result = new ArrayList<>();

        for (Video item : mItems) {
            result.add(mItemManager.getMetadataObserve(item.videoId));
        }

        return result;
    }
}
