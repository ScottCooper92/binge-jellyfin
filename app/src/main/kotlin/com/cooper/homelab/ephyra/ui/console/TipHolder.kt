package com.cooper.homelab.ephyra.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.homelab.core.presentation.tip.TipViewModel
import com.cooper.homelab.core.review.ReviewPrompt
import com.cooper.homelab.core.tip.TipBilling

/** Keeps the shared tip view model across a rotation: it runs on this holder's scope, which lives as long as the page does. */
internal class TipHolder(
    billing: TipBilling,
    reviewPrompt: ReviewPrompt,
) : ViewModel() {
    val tip = TipViewModel(viewModelScope, billing, reviewPrompt)
}
