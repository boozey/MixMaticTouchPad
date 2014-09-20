package com.nakedape.mixmatictouchpad;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;


public class LaunchPadActivity extends Activity {

    private boolean isEditMode = false;

    View.OnClickListener TouchPadClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isEditMode) {
                Intent intent = new Intent(getBaseContext(), SampleEditActivity.class);
                startActivity(intent);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch_pad);
        LinearLayout mainLayout = (LinearLayout)findViewById(R.id.mainLayout);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        for (int i = 0; i < 6; i++){
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.HORIZONTAL);
            mainLayout.addView(l);
            for (int j = 0; j < 4; j++){
                TouchPad t = new TouchPad(this);
                t.setWidth(metrics.widthPixels / 4);
                t.setHeight((metrics.heightPixels - 150) / 6);
                t.setOnClickListener(TouchPadClick);
                l.addView(t);
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.touch_pad, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == R.id.action_edit_mode) {
            if (isEditMode){
                isEditMode = false;
                item.setTitle(R.string.action_edit_mode);
            }
            else {
                isEditMode = true;
                item.setTitle(R.string.action_play_mode);
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
