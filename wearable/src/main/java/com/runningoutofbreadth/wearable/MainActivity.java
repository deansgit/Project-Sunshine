package com.runningoutofbreadth.wearable;

import android.app.Activity;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView mTextView;
//    private FrameLayout mRectLayout;
//    private FrameLayout mRoundLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
//                mRectLayout = (FrameLayout) findViewById(R.id.rect_layout);
//                mRoundLayout = (FrameLayout) findViewById(R.id.round_layout)
//                mTextView.setText(Utility.getFriendlyDayString(mContext, dateInMillis, useLongToday));
//              TODO: export utility class into a library,
//              refactor code to make references to said library,
//              add dependencies
//              reference Utility class and the ForecastAdapter class

            }
        });
    }
}
