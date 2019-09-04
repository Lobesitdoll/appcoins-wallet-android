package com.asfoundation.wallet.referrals

import io.reactivex.disposables.CompositeDisposable

class InviteFriendsFragmentPresenter(private val view: InviteFriendsFragmentView,
                                     private val activity: InviteFriendsActivityView?,
                                     private val disposable: CompositeDisposable) {

  fun present() {
    handleInfoButtonClick()
    handleShareClicks()
    handleAppsGamesClicks()
  }

  private fun handleShareClicks() {
    disposable.add(view.shareLinkClick()
        .doOnNext { view.showShare() }
        .subscribe({}, { it.printStackTrace() }))
  }

  private fun handleAppsGamesClicks() {
    disposable.add(view.appsAndGamesButtonClick()
        .doOnNext { view.navigateToAptoide() }
        .subscribe({}, { it.printStackTrace() }))
  }

  private fun handleInfoButtonClick() {
    activity?.let {
      disposable.add(it.getInfoButtonClick()
          .doOnNext { view.changeBottomSheetState() }
          .subscribe())
    }
  }

  fun stop() {
    disposable.clear()
  }
}