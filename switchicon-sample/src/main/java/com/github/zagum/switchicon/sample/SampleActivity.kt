package com.github.zagum.switchicon.sample

import android.app.Activity
import android.os.Bundle
import com.github.zagum.switchicon.sample.databinding.ActivitySampleBinding

class SampleActivity : Activity() {
    private lateinit var binding: ActivitySampleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySampleBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.button1
            .setOnClickListener { binding.switchIconView1.switchState() }
        binding.button2
            .setOnClickListener { binding.switchIconView2.switchState() }
        binding.button3
            .setOnClickListener { binding.switchIconView3.switchState() }
    }
}
