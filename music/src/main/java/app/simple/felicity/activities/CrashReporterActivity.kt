package app.simple.felicity.activities

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.R
import app.simple.felicity.databinding.ActivityCrashBinding
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.factories.misc.ErrorViewModelFactory
import app.simple.felicity.preferences.CrashPreferences
import app.simple.felicity.repository.database.instances.StackTraceDatabase
import app.simple.felicity.repository.models.normal.StackTrace
import app.simple.felicity.shared.utils.ConditionUtils.invert
import app.simple.felicity.shared.utils.ProcessUtils
import app.simple.felicity.utils.DateUtils.toDate
import app.simple.felicity.viewmodels.misc.ErrorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CrashReporterActivity : BaseActivity() {

    private lateinit var binding: ActivityCrashBinding
    private lateinit var errorViewModel: ErrorViewModel

    private var isPreview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getStringExtra(MODE_NORMAL)?.let { crash ->
            binding.warning.setText(R.string.the_app_has_crashed)
            binding.timestamp.text = CrashPreferences.getCrashLog().toDate()
            binding.cause.text = CrashPreferences.getCause()
                ?: getString(R.string.not_available)
            binding.message.text = CrashPreferences.getMessage()
                ?: getString(R.string.desc_not_available)
            showTrace(crash)
            saveTraceToDataBase(crash)
            isPreview = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(MODE_PREVIEW, StackTrace::class.java)?.let { crash ->
                isPreview = true
                binding.warning.setText(R.string.crash_report)
                binding.timestamp.text = crash.timestamp.toDate()
                binding.cause.text = crash.cause ?: getString(R.string.not_available)
                binding.message.text = crash.message ?: getString(R.string.desc_not_available)
                showTrace(crash.trace)
            }
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<StackTrace>(MODE_PREVIEW)?.let { crash ->
                isPreview = true
                binding.warning.setText(R.string.crash_report)
                binding.timestamp.text = crash.timestamp.toDate()
                binding.cause.text = crash.cause ?: getString(R.string.not_available)
                binding.message.text = crash.message ?: getString(R.string.desc_not_available)
                showTrace(crash.trace)
            }
        }

        binding.close.setOnClickListener {
            close()
        }
    }

    private fun showTrace(crash: String) {
        errorViewModel =
            ViewModelProvider(this, ErrorViewModelFactory(crash))[ErrorViewModel::class.java]

        errorViewModel.getSpanned().observe(this) {
            binding.error.text = it
        }

        binding.copy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Crash Trace", crash)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
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
                runCatching {
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
