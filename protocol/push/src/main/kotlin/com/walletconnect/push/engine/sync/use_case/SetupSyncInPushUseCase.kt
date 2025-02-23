package com.walletconnect.push.engine.sync.use_case

import com.walletconnect.android.cacao.signature.SignatureType
import com.walletconnect.android.internal.common.model.AccountId
import com.walletconnect.android.internal.common.scope
import com.walletconnect.android.internal.common.signing.cacao.Cacao
import com.walletconnect.android.sync.client.Sync
import com.walletconnect.android.sync.client.SyncInterface
import com.walletconnect.android.sync.common.model.Store
import com.walletconnect.foundation.util.Logger
import com.walletconnect.push.engine.sync.PushSyncStores
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


internal class SetupSyncInPushUseCase(
    private val syncClient: SyncInterface,
    private val getMessagesFromHistoryUseCase: GetMessagesFromHistoryUseCase,
    private val logger: Logger,
) {
    suspend operator fun invoke(accountId: AccountId, onSign: (String) -> Cacao.Signature?, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        syncClient.isRegistered(Sync.Params.IsRegistered(accountId), onError = { onError(Throwable("Failed to check account is registered")) }, onSuccess = { isRegistered ->
            if (!isRegistered) {
                // If account is not registered then register in sync client and later register required push stores
                registerAccountInSync(accountId, onSign, onSuccess, onError)
            } else {
                // If account is registered then only register required push stores
                registerPushStoresInSync(accountId, onSuccess, onError)
            }
        })
    }

    /**
     * Registers account in Sync Client, fetches history messages and calls [onSuccess] on success
     */
    private fun registerAccountInSync(accountId: AccountId, onSign: (String) -> Cacao.Signature?, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        val syncSignature = onSign(syncClient.getMessage(Sync.Params.GetMessage(accountId))) ?: return onError(Throwable("Signing Sync SDK message is required to use Push SDK"))

        val params = Sync.Params.Register(accountId, Sync.Model.Signature(syncSignature.t, syncSignature.s, syncSignature.m), SignatureType.headerOf(syncSignature.t))

        syncClient.register(params, onSuccess = {
            // Get message history if account was not previously registered
            registerPushStoresInSync(accountId, onSuccess = {
                scope.launch { getMessagesFromHistoryUseCase(accountId, onSuccess, onError) }
            }, onError)
        }, onError = { error -> onError(error.throwable) })
    }


    /**
     * Creates required Push stores in Sync Client and calls [onSuccess] on success
     */
    private fun registerPushStoresInSync(accountId: AccountId, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        // Register blocking current thread all stores necessary to sync push state
        val countDownLatch = CountDownLatch(PushSyncStores.values().size)

        // Note: When I tried registering all stores simultaneously I had issues with getting right values, when doing it sequentially it works
        PushSyncStores.values().forEach { store ->
            syncClient.create(
                Sync.Params.Create(accountId, Store(store.value)),
                onSuccess = { countDownLatch.countDown() },
                onError = { error -> logger.error("Error while registering ${store.value}: ${error.throwable.stackTraceToString()}") }
            )
        }

        if (!countDownLatch.await(5, TimeUnit.SECONDS)) {
            onError(Throwable("Required Push Stores initialization timeout"))
        } else {
            onSuccess()
        }
    }
}