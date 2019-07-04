package com.github.zagum.switchicon.sample

import android.app.Activity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_sample.*

class SampleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)
        button1.setOnClickListener { switchIconView1.switchState() }
        button2.setOnClickListener { switchIconView2.switchState() }
        button3.setOnClickListener { switchIconView3.switchState() }
    }
}
