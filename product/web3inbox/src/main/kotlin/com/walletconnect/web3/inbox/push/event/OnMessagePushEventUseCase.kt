package com.walletconnect.web3.inbox.push.event

import com.walletconnect.push.client.Push
import com.walletconnect.web3.inbox.common.proxy.PushProxyInteractor
import com.walletconnect.web3.inbox.json_rpc.Web3InboxParams
import com.walletconnect.web3.inbox.json_rpc.Web3InboxRPC

internal class OnMessagePushEventUseCase(
    proxyInteractor: PushProxyInteractor,
) : PushEventUseCase<Push.Event.Message>(proxyInteractor) {

    override fun invoke(model: Push.Event.Message) =
        call(Web3InboxRPC.Call.Push.Message(params = model.toParams()))

    private fun Push.Event.Message.toParams() =
        Web3InboxParams.Call.Push.MessageParams(message.toParams())

    private fun Push.Model.MessageRecord.toParams() =
        Web3InboxParams.Call.Push.MessageParams.MessageRecord(id, topic, publishedAt, message.toParams())


    private fun Push.Model.Message.toParams() = Web3InboxParams.Call.Push.MessageParams.Message(title, body, icon, url)
}
