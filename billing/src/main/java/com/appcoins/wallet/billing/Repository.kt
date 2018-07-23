package com.appcoins.wallet.billing

import com.appcoins.wallet.billing.repository.BillingSupportedType
import com.appcoins.wallet.billing.repository.entity.Purchase
import io.reactivex.Single

internal interface Repository {

  fun isSupported(packageName: String, type: BillingSupportedType): Single<Boolean>

  fun getPurchases(packageName: String, walletAddress: String, walletSignature: String,
                   type: BillingSupportedType): Single<List<Purchase>>

}
