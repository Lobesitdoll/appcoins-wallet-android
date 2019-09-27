package com.asfoundation.wallet.topup

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.view.inputmethod.InputMethodManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.asf.wallet.R
import com.asfoundation.wallet.topup.TopUpData.Companion.APPC_C_CURRENCY
import com.asfoundation.wallet.topup.TopUpData.Companion.DEFAULT_VALUE
import com.asfoundation.wallet.topup.TopUpData.Companion.FIAT_CURRENCY
import com.asfoundation.wallet.topup.paymentMethods.PaymentMethodData
import com.asfoundation.wallet.topup.paymentMethods.TopUpPaymentMethodAdapter
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import com.jakewharton.rxrelay2.PublishRelay
import dagger.android.support.DaggerFragment
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_top_up.*
import kotlinx.android.synthetic.main.view_purchase_bonus.view.*
import java.math.BigDecimal
import javax.inject.Inject


class TopUpFragment : DaggerFragment(), TopUpFragmentView {

  @Inject
  lateinit var interactor: TopUpInteractor

  private lateinit var adapter: TopUpPaymentMethodAdapter
  private lateinit var presenter: TopUpFragmentPresenter
  private lateinit var paymentMethodClick: PublishRelay<String>
  private lateinit var fragmentContainer: ViewGroup
  private lateinit var paymentMethods: List<PaymentMethodData>
  private var topUpActivityView: TopUpActivityView? = null
  private var selectedCurrency = FIAT_CURRENCY
  private var switchingCurrency = false
  private var bonusMessageValue: String = ""
  private var localCurrency = LocalCurrency()

  companion object {
    private const val PARAM_APP_PACKAGE = "APP_PACKAGE"
    private const val APPC_C_SYMBOL = "APPC-C"

    private const val SELECTED_CURRENCY_PARAM = "SELECTED_CURRENCY"
    private const val LOCAL_CURRENCY_PARAM = "LOCAL_CURRENCY"


    @JvmStatic
    fun newInstance(packageName: String): TopUpFragment {
      val bundle = Bundle()
      bundle.putString(PARAM_APP_PACKAGE, packageName)
      val fragment = TopUpFragment()
      fragment.arguments = bundle
      return fragment
    }
  }

  val appPackage: String by lazy {
    if (arguments!!.containsKey(PARAM_APP_PACKAGE)) {
      arguments!!.getString(PARAM_APP_PACKAGE)
    } else {
      throw IllegalArgumentException("application package name data not found")
    }
  }

  override fun onDetach() {
    super.onDetach()
    topUpActivityView = null
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    if (context !is TopUpActivityView) {
      throw IllegalStateException(
          "Express checkout buy fragment must be attached to IAB activity")
    }
    topUpActivityView = context
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    paymentMethodClick = PublishRelay.create()
    presenter =
        TopUpFragmentPresenter(this, topUpActivityView, interactor, AndroidSchedulers.mainThread(),
            Schedulers.io())

  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                            savedInstanceState: Bundle?): View? {
    fragmentContainer = container!!
    return inflater.inflate(R.layout.fragment_top_up, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    if (savedInstanceState?.containsKey(SELECTED_CURRENCY_PARAM) == true) {
      selectedCurrency = savedInstanceState.getString(SELECTED_CURRENCY_PARAM) ?: FIAT_CURRENCY
      localCurrency = savedInstanceState.getSerializable(LOCAL_CURRENCY_PARAM) as LocalCurrency
    }
    topUpActivityView?.showToolbar()
    presenter.present(appPackage)

  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(SELECTED_CURRENCY_PARAM, selectedCurrency)
    outState.putSerializable(LOCAL_CURRENCY_PARAM, localCurrency)
  }

  override fun setupUiElements(paymentMethods: List<PaymentMethodData>,
                               localCurrency: LocalCurrency) {
    this@TopUpFragment.paymentMethods = paymentMethods
    this@TopUpFragment.localCurrency = localCurrency
    setupCurrencyData(selectedCurrency, localCurrency.code, DEFAULT_VALUE,
        APPC_C_SYMBOL, DEFAULT_VALUE)
    main_value.isEnabled = true
    main_value.setMinTextSize(
        resources.getDimensionPixelSize(R.dimen.topup_main_value_min_size).toFloat())

    adapter = TopUpPaymentMethodAdapter(paymentMethods, paymentMethodClick)
    payment_methods.adapter = adapter
    payment_methods.layoutManager = LinearLayoutManager(context)
    payment_methods.visibility = View.VISIBLE
    swap_value_button.isEnabled = true
    swap_value_button.visibility = View.VISIBLE
    swap_value_label.visibility = View.VISIBLE
  }

  override fun onDestroy() {
    presenter.stop()
    super.onDestroy()
  }

  override fun getChangeCurrencyClick(): Observable<Any> {
    return RxView.clicks(swap_value_button)
  }

  override fun getEditTextChanges(): Observable<TopUpData> {
    return RxTextView.afterTextChangeEvents(main_value)
        .filter { !switchingCurrency }
        .map {
          TopUpData(getCurrencyData(), selectedCurrency, getSelectedPaymentMethod())
        }
  }

  override fun getPaymentMethodClick(): Observable<String> {
    return paymentMethodClick
  }

  override fun getNextClick(): Observable<TopUpData> {
    return RxView.clicks(button)
        .map {
          TopUpData(getCurrencyData(), selectedCurrency, getSelectedPaymentMethod(),
              bonusMessageValue)
        }
  }

  override fun setNextButtonState(enabled: Boolean) {
    button.isEnabled = enabled
  }

  override fun hideKeyboard() {
    val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    imm?.hideSoftInputFromWindow(fragmentContainer.windowToken, 0)
    payment_methods.requestFocus()
  }

  override fun showLoading() {
    fragment_braintree_credit_card_form.visibility = View.GONE
    payment_methods.visibility = View.INVISIBLE
    bonus_layout.visibility = View.GONE
    bonus_msg.visibility = View.GONE
    loading.visibility = View.VISIBLE
  }

  override fun showPaymentDetailsForm() {
    payment_methods.visibility = View.GONE
    loading.visibility = View.GONE
    fragment_braintree_credit_card_form.visibility = View.VISIBLE
    bonus_layout.visibility = View.GONE
    bonus_msg.visibility = View.GONE
  }

  override fun showPaymentMethods() {
    fragment_braintree_credit_card_form.visibility = View.GONE
    loading.visibility = View.GONE
    payment_methods.visibility = View.VISIBLE
  }

  override fun rotateChangeCurrencyButton() {
    val rotateAnimation = RotateAnimation(
        0f,
        180f,
        Animation.RELATIVE_TO_SELF,
        0.5f,
        Animation.RELATIVE_TO_SELF,
        0.5f)
    rotateAnimation.duration = 250
    rotateAnimation.interpolator = AccelerateDecelerateInterpolator()
    swap_value_button.startAnimation(rotateAnimation)
  }

  override fun switchCurrencyData() {
    val currencyData = getCurrencyData()
    selectedCurrency =
        if (selectedCurrency == APPC_C_CURRENCY) FIAT_CURRENCY else APPC_C_CURRENCY
    // We just have to switch the current information being shown
    setupCurrencyData(selectedCurrency, currencyData.fiatCurrencyCode, currencyData.fiatValue,
        currencyData.appcCode, currencyData.appcValue)
  }

  override fun setConversionValue(topUpData: TopUpData) {
    if (topUpData.selectedCurrency == selectedCurrency) {
      when (selectedCurrency) {
        FIAT_CURRENCY -> {
          converted_value.text = "${topUpData.currency.appcValue} ${topUpData.currency.appcSymbol}"
        }
        APPC_C_CURRENCY -> {
          converted_value.text =
              "${topUpData.currency.fiatValue} ${topUpData.currency.fiatCurrencyCode}"
        }
      }
    } else {
      when (selectedCurrency) {
        FIAT_CURRENCY -> {
          if (topUpData.currency.fiatValue != DEFAULT_VALUE) main_value.setText(
              topUpData.currency.fiatValue) else main_value.setText("")
        }
        APPC_C_CURRENCY -> {
          if (topUpData.currency.appcValue != DEFAULT_VALUE) main_value.setText(
              topUpData.currency.appcValue) else main_value.setText("")
        }
      }
    }
  }

  override fun toggleSwitchCurrencyOn() {
    switchingCurrency = true
  }

  override fun toggleSwitchCurrencyOff() {
    switchingCurrency = false
  }

  override fun hideBonus() {
    bonus_layout.visibility = View.INVISIBLE
    bonus_msg.visibility = View.INVISIBLE
  }

  override fun showBonus(bonus: BigDecimal, currency: String) {
    buildBonusString(bonus, currency)
    bonus_layout.visibility = View.VISIBLE
    bonus_msg.visibility = View.VISIBLE
  }

  private fun buildBonusString(bonus: BigDecimal, bonusCurrency: String) {
    var scaledBonus = bonus.stripTrailingZeros()
        .setScale(2, BigDecimal.ROUND_FLOOR)
    var currency = bonusCurrency
    if (scaledBonus < BigDecimal(0.01)) {
      currency = "~$currency"
    }
    scaledBonus = scaledBonus.max(BigDecimal("0.01"))

    bonusMessageValue = currency + scaledBonus.toPlainString()
    bonus_layout.bonus_header_1.text = getString(R.string.topup_bonus_header_part_1)
    bonus_layout.bonus_value.text = getString(R.string.topup_bonus_header_part_2,
        currency + scaledBonus.toPlainString())
  }

  private fun setupCurrencyData(selectedCurrency: String, fiatCode: String, fiatValue: String,
                                appcCode: String, appcValue: String) {

    when (selectedCurrency) {
      FIAT_CURRENCY -> {
        setCurrencyInfo(fiatCode, fiatValue,
            "$appcValue $appcCode", appcCode)
      }
      APPC_C_CURRENCY -> {
        setCurrencyInfo(appcCode, appcValue,
            "$fiatValue $fiatCode", fiatCode)
      }
    }
  }

  private fun setCurrencyInfo(mainCode: String, mainValue: String,
                              conversionValue: String, conversionCode: String) {
    main_currency_code.text = mainCode
    if (mainValue != DEFAULT_VALUE) {
      main_value.setText(mainValue)
      main_value.setSelection(main_value.text!!.length)
    }
    swap_value_label.text = conversionCode
    converted_value.text = conversionValue
  }

  private fun getSelectedPaymentMethod(): String {
    return if (payment_methods.adapter != null) {
      val data = (payment_methods.adapter as TopUpPaymentMethodAdapter).getSelectedItemData()
      data.id
    } else {
      "credit_card"
    }
  }

  private fun getCurrencyData(): CurrencyData {
    return if (selectedCurrency == FIAT_CURRENCY) {
      val appcValue = converted_value.text.toString()
          .replace(APPC_C_SYMBOL, "")
          .replace(" ", "")
      val localCurrencyValue =
          if (main_value.text.toString().isEmpty()) DEFAULT_VALUE else main_value.text.toString()
      CurrencyData(localCurrency.code, localCurrency.symbol, localCurrencyValue,
          APPC_C_SYMBOL, APPC_C_SYMBOL, appcValue)
    } else {
      val localCurrencyValue = converted_value.text.toString()
          .replace(localCurrency.code, "")
          .replace(" ", "")
      val appcValue =
          if (main_value.text.toString().isEmpty()) DEFAULT_VALUE else main_value.text.toString()
      CurrencyData(localCurrency.code, localCurrency.symbol, localCurrencyValue,
          APPC_C_SYMBOL, APPC_C_SYMBOL, appcValue)
    }
  }
}
