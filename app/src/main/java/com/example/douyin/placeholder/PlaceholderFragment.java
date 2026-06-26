package com.example.douyin.placeholder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.example.douyin.R;

public class PlaceholderFragment extends Fragment {

    private static final String ARG_TITLE_RES = "title_res";
    private static final String ARG_SUBTITLE_RES = "subtitle_res";

    public static PlaceholderFragment newInstance(@StringRes int titleRes) {
        return newInstance(titleRes, R.string.placeholder_coming_soon);
    }

    public static PlaceholderFragment newInstance(@StringRes int titleRes,
                                                  @StringRes int subtitleRes) {
        PlaceholderFragment fragment = new PlaceholderFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE_RES, titleRes);
        args.putInt(ARG_SUBTITLE_RES, subtitleRes);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_placeholder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        TextView titleView = view.findViewById(R.id.tv_title);
        TextView subtitleView = view.findViewById(R.id.tv_subtitle);

        int titleRes = args.getInt(ARG_TITLE_RES, R.string.placeholder_coming_soon);
        int subtitleRes = args.getInt(ARG_SUBTITLE_RES, R.string.placeholder_coming_soon);
        titleView.setText(titleRes);
        subtitleView.setText(subtitleRes);
        subtitleView.setVisibility(View.VISIBLE);
    }
}
