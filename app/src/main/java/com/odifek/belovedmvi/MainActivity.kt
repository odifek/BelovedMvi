package com.odifek.belovedmvi

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.odifek.belovedmvi.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val viewModel: CounterViewModel by viewModels()

        viewModel.counter.onEach { binding.textViewCounter.text = it.toString() }
            .launchIn(lifecycleScope)

        binding.buttonInc.setOnClickListener { viewModel.accept(CounterViewModel.UiEvent.ButtonInc) }
    }
}