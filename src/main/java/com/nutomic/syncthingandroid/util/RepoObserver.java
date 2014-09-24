package com.nutomic.syncthingandroid.util;

import android.os.FileObserver;
import android.util.Log;

import com.nutomic.syncthingandroid.syncthing.RestApi;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

/**
 * Recursively watches a directory and all subfolders.
 */
public class RepoObserver extends FileObserver {

    private static final String TAG = "RepoObserver";

    private final OnRepoFileChangeListener mListener;

    private final RestApi.Repo mRepo;

    private final String mPath;

    private final ArrayList<RepoObserver> mChilds;

    public interface OnRepoFileChangeListener {
        public void onRepoFileChange(String repoId, String relativePath);
    }

    public RepoObserver(OnRepoFileChangeListener listener, RestApi.Repo repo) {
        this(listener, repo, "");
    }

    /**
     * Constructs watcher and starts watching the given directory recursively.
     *
     * @param listener The listener where changes should be sent to.
     * @param repo The repository where this folder belongs to.
     * @param path Path to the monitored folder, relative to repo root.
     */
    private RepoObserver(OnRepoFileChangeListener listener, RestApi.Repo repo, String path) {
        super(repo.Directory + "/" + path,
                ATTRIB | CLOSE_WRITE | CREATE | DELETE | DELETE_SELF | MODIFY | MOVED_FROM |
                MOVED_TO | MOVE_SELF);
        mListener = listener;
        mRepo = repo;
        mPath = path;
        Log.v(TAG, "observer created for " + path + " in " + repo.ID);
        startWatching();

        File[] directories = new File(repo.Directory, path).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });

        mChilds = new ArrayList<>(directories.length);
        for (File f : directories) {
            mChilds.add(new RepoObserver(mListener, mRepo, path + "/" + f.getName()));
        }
    }

    /**
     * Handles incoming events for changed files.
     */
    @Override
    public void onEvent(int event, String path) {
        // Ignore some weird events that we may receive.
        event &= FileObserver.ALL_EVENTS;
        if (event == 0)
            return;

        switch (event) {
            case MOVED_FROM:
                // fall through
            case DELETE_SELF:
                // fall through
            case DELETE:
                for (RepoObserver ro : mChilds) {
                    if (ro.mPath.equals(path)) {
                        mChilds.remove(ro);
                        break;
                    }
                }
                break;
            case MOVED_TO:
                // fall through
            case CREATE:
                mChilds.add(new RepoObserver(mListener, mRepo, path));
                // fall through
            default:
                mListener.onRepoFileChange(mRepo.ID, new File(mPath, path).getPath());
        }
    }

    /**
     * Recursively stops watching the directory.
     */
    @Override
    public void stopWatching() {
        super.stopWatching();
        for (RepoObserver ro : mChilds) {
            ro.stopWatching();
        }
    }
}
