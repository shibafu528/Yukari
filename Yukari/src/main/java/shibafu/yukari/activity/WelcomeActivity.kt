package shibafu.yukari.activity

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.support.v7.app.ActionBarActivity
import android.widget.Button
import android.widget.TextView
import shibafu.yukari.R

class WelcomeActivity : ActionBarActivity() {

    val btnLogin by lazy { findViewById(R.id.btnLogin) as Button }
    val btnImport by lazy { findViewById(R.id.btnImport) as Button }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar.hide()
        setContentView(R.layout.activity_welcome)

        val txtTitle = findViewById(R.id.textView) as TextView
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