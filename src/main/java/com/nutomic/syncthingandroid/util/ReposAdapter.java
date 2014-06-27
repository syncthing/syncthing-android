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

import java.util.HashMap;
import java.util.List;

/**
 * Generates item views for repository items.
 */
public class ReposAdapter extends ArrayAdapter<RestApi.Repo>
		implements RestApi.OnReceiveModelListener {

	private HashMap<String, RestApi.Model> mModels = new HashMap<String, RestApi.Model>();

	public ReposAdapter(Context context) {
		super(context, R.layout.repo_list_item);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = inflater.inflate(R.layout.repo_list_item, parent, false);
		}

		TextView id = (TextView) convertView.findViewById(R.id.id);

		TextView state = (TextView) convertView.findViewById(R.id.state);
		TextView folder = (TextView) convertView.findViewById(R.id.folder);
		TextView progress = (TextView) convertView.findViewById(R.id.progress);
		TextView invalid = (TextView) convertView.findViewById(R.id.invalid);

		id.setText(getItem(position).ID);
		state.setTextColor(getContext().getResources().getColor(R.color.text_green));
		folder.setText((getItem(position).Directory));
		RestApi.Model model = mModels.get(getItem(position).ID);
		if (model != null) {
			state.setText(getContext().getString(R.string.repo_progress_format, model.state,
					(model.globalBytes <= 0)
							? 100
							: (int) ((model.localBytes / (float) model.globalBytes) * 100)));
			progress.setText(
					RestApi.readableFileSize(getContext(), model.localBytes) + " / " +
					RestApi.readableFileSize(getContext(), model.globalBytes)
			);
			invalid.setText(model.invalid);
			invalid.setVisibility((model.invalid.equals("")) ? View.INVISIBLE : View.VISIBLE);
		}
		else {
			invalid.setVisibility(View.INVISIBLE);
		}

		return convertView;
	}

	/**
	 * Replacement for addAll, which is not implemented on lower API levels.
	 */
	public void add(List<RestApi.Repo> nodes) {
		for (RestApi.Repo r : nodes) {
			add(r);
		}
	}

	/**
	 * Requests updated model info from the api for all visible items.
	 */
	public void updateModel(RestApi api, ListView listView) {
		for (int i = 0; i < getCount(); i++) {
			if ( i >= listView.getFirstVisiblePosition() &&
					i <= listView.getLastVisiblePosition()) {
				api.getModel(getItem(i).ID, this);
			}
		}
	}

	@Override
	public void onReceiveModel(String repoId, RestApi.Model model) {
		mModels.put(repoId, model);
		notifyDataSetChanged();
	}
}
