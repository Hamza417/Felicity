package app.simple.felicity.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.R
import app.simple.felicity.core.utils.ProcessUtils
import app.simple.felicity.decorations.ripple.DynamicRippleTextView
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.factories.misc.ErrorViewModelFactory
import app.simple.felicity.preferences.CrashPreferences
import app.simple.felicity.repository.database.instances.StackTraceDatabase
import app.simple.felicity.repository.models.normal.StackTrace
import app.simple.felicity.shared.utils.ConditionUtils.invert
import app.simple.felicity.utils.DateUtils.toDate
import app.simple.felicity.viewmodels.misc.ErrorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CrashReporterActivity : BaseActivity() {

    private lateinit var warning: TypeFaceTextView
    private lateinit var message: TypeFaceTextView
    private lateinit var cause: TypeFaceTextView
    private lateinit var timestamp: TypeFaceTextView
    private lateinit var error: TypeFaceTextView
    private lateinit var send: DynamicRippleTextView
    private lateinit var close: DynamicRippleTextView
    private lateinit var errorViewModel: ErrorViewModel

    private var isPreview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        warning = findViewById(R.id.warning)
        message = findViewById(R.id.message)
        cause = findViewById(R.id.cause)
        warning = findViewById(R.id.warning)
        timestamp = findViewById(R.id.timestamp)
        error = findViewById(R.id.error)
        send = findViewById(R.id.send)
        close = findViewById(R.id.close)

        intent.getStringExtra(MODE_NORMAL)?.let { crash ->
            warning.setText(R.string.the_app_has_crashed)
            timestamp.text = CrashPreferences.getCrashLog().toDate()
            cause.text = CrashPreferences.getCause()
                ?: getString(R.string.not_available)
            message.text = CrashPreferences.getMessage()
                ?: getString(R.string.desc_not_available)
            showTrace(crash)
            saveTraceToDataBase(crash)
            isPreview = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(MODE_PREVIEW, StackTrace::class.java)?.let { crash ->
                isPreview = true
                warning.setText(R.string.crash_report)
                timestamp.text = crash.timestamp.toDate()
                cause.text = crash.cause ?: getString(R.string.not_available)
                message.text = crash.message ?: getString(R.string.desc_not_available)
                showTrace(crash.trace)
            }
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<StackTrace>(MODE_PREVIEW)?.let { crash ->
                isPreview = true
                warning.setText(R.string.crash_report)
                timestamp.text = crash.timestamp.toDate()
                cause.text = crash.cause ?: getString(R.string.not_available)
                message.text = crash.message ?: getString(R.string.desc_not_available)
                showTrace(crash.trace)
            }
        }

        close.setOnClickListener {
            close()
        }
    }

    private fun showTrace(crash: String) {
        errorViewModel =
            ViewModelProvider(this, ErrorViewModelFactory(crash))[ErrorViewModel::class.java]

        errorViewModel.getSpanned().observe(this) {
            error.text = it
        }

        send.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.the_app_has_crashed))
            shareIntent.putExtra(Intent.EXTRA_TEXT, crash.trim().trimIndent())
            startActivity(Intent.createChooser(shareIntent, "Crash Log"))
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        close()
    }

    private fun close() {
        if (isPreview.invert()) {
            if (CrashPreferences.getCrashLog() != CrashPreferences.crashTimestampEmptyDefault) {
                CrashPreferences.saveCrashLog(CrashPreferences.crashTimestampEmptyDefault)
                CrashPreferences.saveMessage(null)
                CrashPreferences.saveCause(null)
            }
        }
        finish()
    }

    private fun saveTraceToDataBase(trace: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            ProcessUtils.ensureNotOnMainThread {
                kotlin.runCatching {
                    val stackTrace =
                        StackTrace(
                            trace,
                            CrashPreferences.getMessage() ?: getString(R.string.desc_not_available),
                            CrashPreferences.getCause() ?: getString(R.string.not_available),
                            System.currentTimeMillis()
                        )
                    val stackTraceDatabase = StackTraceDatabase.getInstance(applicationContext)
                    stackTraceDatabase!!.stackTraceDao()!!.insertTrace(stackTrace)
                    stackTraceDatabase.close()
                }
            }
        }
    }

    companion object {
        const val MODE_PREVIEW = "crashLog_preview"
        const val MODE_NORMAL = "crashLog"
    }
}
