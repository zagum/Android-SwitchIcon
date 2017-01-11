package com.github.zagum.switchicon.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import com.github.zagum.switchicon.SwitchIconView;

public class SampleActivity extends Activity {

  private SwitchIconView switchIcon1;
  private SwitchIconView switchIcon2;
  private View button1;
  private View button2;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_sample);

    switchIcon1 = (SwitchIconView) findViewById(R.id.switchIconView1);
    switchIcon2 = (SwitchIconView) findViewById(R.id.switchIconView2);
    button1 = findViewById(R.id.button1);
    button2 = findViewById(R.id.button2);

    button1.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        switchIcon1.switchState();
      }
    });
    button2.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        switchIcon2.switchState();
        switchIcon1.setIconEnabled(!switchIcon1.isIconEnabled());
      }
    });
  }
}
