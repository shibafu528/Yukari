package shibafu.yukari.activity

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import shibafu.yukari.R

class WelcomeActivity : AppCompatActivity() {

    val btnLogin by lazy { findViewById<Button>(R.id.btnLogin) }
    val btnImport by lazy { findViewById<Button>(R.id.btnImport) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_welcome)

        val txtTitle = findViewById<TextView>(R.id.textView)
        val typeface = Typeface.createFromAsset(assets, "Roboto-Thin.ttf")
        txtTitle.typeface = typeface

        btnLogin.setOnClickListener {
            val intent = Intent(applicationContext, OAuthActivity::class.java)
            intent.putExtra(OAuthActivity.EXTRA_REBOOT, true)
            startActivity(intent)
            finish()
        }
        btnImport.setOnClickListener {
            val intent = Intent(applicationContext, BackupActivity::class.java)
            intent.putExtra(BackupActivity.EXTRA_MODE, BackupActivity.EXTRA_MODE_IMPORT)
            startActivity(intent)
            finish()
        }
    }
}