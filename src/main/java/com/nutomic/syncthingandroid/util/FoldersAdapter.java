package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.text.TextUtils;
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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.nutomic.syncthingandroid.syncthing.RestApi.readableFileSize;

/**
 * Generates item views for folder items.
 */
public class FoldersAdapter extends ArrayAdapter<RestApi.Folder>
        implements RestApi.OnReceiveModelListener {

    private HashMap<String, RestApi.Model> mModels = new HashMap<>();
    private LayoutInflater mInflater;

    private final static Comparator<RestApi.Folder> COMPARATOR = new Comparator<RestApi.Folder>() {
        @Override
        public int compare(RestApi.Folder lhs, RestApi.Folder rhs) {
            return lhs.id.compareTo(rhs.id);
        }
    };

    public FoldersAdapter(Context context) {
        super(context, R.layout.item_folder_list);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_folder_list, parent, false);

            viewHolder = new ViewHolder();
            viewHolder.id = (TextView) convertView.findViewById(R.id.id);
            viewHolder.state = (TextView) convertView.findViewById(R.id.state);
            viewHolder.directory = (TextView) convertView.findViewById(R.id.directory);
            viewHolder.items = (TextView) convertView.findViewById(R.id.items);
            viewHolder.size = (TextView) convertView.findViewById(R.id.size);
            viewHolder.invalid = (TextView) convertView.findViewById(R.id.invalid);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        RestApi.Folder folder = getItem(position);
        RestApi.Model model = mModels.get(folder.id);
        viewHolder.id.setText(folder.id);
        viewHolder.state.setTextColor(getContext().getResources().getColor(R.color.text_green));
        viewHolder.directory.setText((folder.path));
        if (model != null) {
            int percentage = (model.globalBytes != 0)
                    ? (int) Math.floor(100 * model.inSyncBytes / model.globalBytes)
                    : 100;
            viewHolder.state.setText(getContext().getString(R.string.folder_progress_format,
                    RestApi.getLocalizedState(getContext(), model.state),
                    percentage));
            viewHolder.items.setVisibility(VISIBLE);
            viewHolder.items.setText(getContext()
                    .getString(R.string.files, model.inSyncFiles, model.globalFiles));
            viewHolder.size.setVisibility(VISIBLE);
            viewHolder.size.setText(readableFileSize(getContext(), model.inSyncBytes) + " / " +
                    readableFileSize(getContext(), model.globalBytes));
            if (TextUtils.isEmpty(folder.invalid)) {
                setTextOrHide(viewHolder.invalid, model.invalid);
            }
        } else {
            viewHolder.items.setVisibility(GONE);
            viewHolder.size.setVisibility(GONE);
            setTextOrHide(viewHolder.invalid, folder.invalid);
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

    private void setTextOrHide(TextView view, String text) {
        boolean isEmpty = TextUtils.isEmpty(text);
        if (isEmpty) {
            view.setVisibility(GONE);
        } else {
            view.setText(text);
            view.setVisibility(VISIBLE);
        }
    }

    private static class ViewHolder {
        TextView id;
        TextView state;
        TextView directory;
        TextView items;
        TextView size;
        TextView invalid;
    }
}
