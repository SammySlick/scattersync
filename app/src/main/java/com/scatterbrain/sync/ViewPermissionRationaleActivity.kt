package com.scatterbrain.sync

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ViewPermissionRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.setPadding(48, 48, 48, 48)
        tv.textSize = 16f
        tv.text = ("ScatterSync reads your health data (heart rate, steps, sleep, weight, " +
                "body fat, exercise, nutrition) from Health Connect on this phone and syncs it " +
                "to your own private server (hcgateway-api.sam-6e1.workers.dev). " +
                "The data is encrypted with your password before upload and is never sent anywhere else. " +
                "No third party receives any data.")
        setContentView(tv)
    }
}
