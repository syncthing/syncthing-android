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
public class FolderObserver extends FileObserver {

    private static final String TAG = "FolderObserver";

    private final OnFolderFileChangeListener mListener;

    private final RestApi.Folder mFolder;

    private final String mPath;

    private final ArrayList<FolderObserver> mChilds;

    public interface OnFolderFileChangeListener {
        public void onFolderFileChange(String folderId, String relativePath);
    }

    public FolderObserver(OnFolderFileChangeListener listener, RestApi.Folder folder) {
        this(listener, folder, "");
    }

    /**
     * Constructs watcher and starts watching the given directory recursively.
     *
     * @param listener The listener where changes should be sent to.
     * @param folder The folder where this folder belongs to.
     * @param path Path to the monitored folder, relative to folder root.
     */
    private FolderObserver(OnFolderFileChangeListener listener, RestApi.Folder folder, String path) {
        super(folder.Path + "/" + path,
                ATTRIB | CLOSE_WRITE | CREATE | DELETE | DELETE_SELF | MODIFY | MOVED_FROM |
                MOVED_TO | MOVE_SELF);
        mListener = listener;
        mFolder = folder;
        mPath = path;
        Log.v(TAG, "observer created for " + path + " in " + folder.ID);
        startWatching();

        File[] directories = new File(folder.Path, path).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });

        mChilds = new ArrayList<>();
        for (File f : directories) {
            mChilds.add(new FolderObserver(mListener, mFolder, path + "/" + f.getName()));
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
                for (FolderObserver ro : mChilds) {
                    if (ro.mPath.equals(path)) {
                        mChilds.remove(ro);
                        break;
                    }
                }
                break;
            case MOVED_TO:
                // fall through
            case CREATE:
                if (new File(mPath, path).isDirectory()) {
                    mChilds.add(new FolderObserver(mListener, mFolder, path));
                }
                // fall through
            default:
                mListener.onFolderFileChange(mFolder.ID, new File(mPath, path).getPath());
        }
    }

    /**
     * Recursively stops watching the directory.
     */
    @Override
    public void stopWatching() {
        super.stopWatching();
        for (FolderObserver ro : mChilds) {
            ro.stopWatching();
        }
    }
}
