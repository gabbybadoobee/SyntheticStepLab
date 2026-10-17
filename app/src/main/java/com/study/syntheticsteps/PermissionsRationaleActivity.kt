package com.study.syntheticsteps

import android.app.Activity
import android.os.Bundle
import android.widget.Button

class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions_rationale)
        findViewById<Button>(R.id.closeButton).setOnClickListener { finish() }
    }
}
