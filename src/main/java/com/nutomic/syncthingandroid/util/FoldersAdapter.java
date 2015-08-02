package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Generates item views for folder items.
 */
public class FoldersAdapter extends ArrayAdapter<RestApi.Folder>
        implements RestApi.OnReceiveModelListener {

    private HashMap<String, RestApi.Model> mModels = new HashMap<>();

    private final static Comparator<RestApi.Folder> COMPARATOR = new Comparator<RestApi.Folder>() {
        @Override
        public int compare(RestApi.Folder lhs, RestApi.Folder rhs) {
            return lhs.id.compareTo(rhs.id);
        }
    };

    public FoldersAdapter(Context context) {
        super(context, R.layout.item_folder_list);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_folder_list, parent, false);
        }

        TextView id = (TextView) convertView.findViewById(R.id.id);
        TextView state = (TextView) convertView.findViewById(R.id.state);
        TextView directory = (TextView) convertView.findViewById(R.id.directory);
        TextView items = (TextView) convertView.findViewById(R.id.items);
        TextView size = (TextView) convertView.findViewById(R.id.size);
        TextView invalid = (TextView) convertView.findViewById(R.id.invalid);

        RestApi.Folder folder = getItem(position);
        RestApi.Model model = mModels.get(folder.id);
        id.setText(folder.id);
        state.setTextColor(getContext().getResources().getColor(R.color.text_green));
        directory.setText((folder.path));
        if (model != null) {
            int percentage = (model.globalBytes != 0)
                    ? (int) Math.floor(100 * model.inSyncBytes / model.globalBytes)
                    : 100;
            state.setText(getContext().getString(R.string.folder_progress_format,
                    RestApi.getLocalizedState(getContext(), model.state),
                    percentage));
            items.setText(getContext()
                    .getString(R.string.files, model.inSyncFiles, model.globalFiles));
            size.setText(RestApi.readableFileSize(getContext(), model.inSyncBytes) + " / " +
                    RestApi.readableFileSize(getContext(), model.globalBytes));
            if (folder.invalid.equals("")) {
                invalid.setText(model.invalid);
                invalid.setVisibility((model.invalid.equals("")) ? View.GONE : View.VISIBLE);
            }
        } else {
            invalid.setText(folder.invalid);
            invalid.setVisibility((folder.invalid.equals("")) ? View.GONE : View.VISIBLE);
        }

        return convertView;
    }

    /**
     * Replacement for addAll, which is not implemented on lower API levels.
     */
    public void add(List<RestApi.Folder> devices) {
        for (RestApi.Folder r : devices) {
            add(r);
        }
    }

    /**
     * Sorts adapter after insert.
     */
    @Override
    public void add(RestApi.Folder object) {
        super.add(object);
        sort(COMPARATOR);
    }

    /**
     * Requests updated model info from the api for all visible items.
     */
    public void updateModel(RestApi api, ListView listView) {
        for (int i = 0; i < getCount(); i++) {
            if (i >= listView.getFirstVisiblePosition() &&
                    i <= listView.getLastVisiblePosition()) {
                api.getModel(getItem(i).id, this);
            }
        }
    }

    @Override
    public void onReceiveModel(String folderId, RestApi.Model model) {
        mModels.put(folderId, model);
        notifyDataSetChanged();
    }

}
