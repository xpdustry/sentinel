/*
 * This file is part of Sentinel, a powerful security plugin for Mindustry.
 *
 * MIT License
 *
 * Copyright (c) 2024 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.sentinel

import arc.func.Cons2
import arc.struct.ObjectMap
import arc.util.CommandHandler
import com.google.common.net.InetAddresses
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addPathSource
import com.xpdustry.distributor.api.DistributorProvider
import com.xpdustry.distributor.api.annotation.PluginAnnotationProcessor
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.command.cloud.MindustryCommandManager
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.render.ComponentStringBuilder
import com.xpdustry.distributor.api.key.DynamicKeyContainer
import com.xpdustry.distributor.api.key.StandardKeys
import com.xpdustry.distributor.api.player.MUUID
import com.xpdustry.distributor.api.plugin.AbstractMindustryPlugin
import com.xpdustry.distributor.api.plugin.PluginListener
import com.xpdustry.distributor.api.translation.BundleTranslationSource
import com.xpdustry.distributor.api.translation.ResourceBundles
import com.xpdustry.distributor.api.translation.TranslationSource
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.sentinel.gatekeeper.CrackedClientProcessor
import com.xpdustry.sentinel.gatekeeper.GatekeeperContext
import com.xpdustry.sentinel.gatekeeper.GatekeeperPipeline
import com.xpdustry.sentinel.gatekeeper.GatekeeperPipelineImpl
import com.xpdustry.sentinel.gatekeeper.GatekeeperResult
import com.xpdustry.sentinel.gatekeeper.LinkDetectionProcessor
import com.xpdustry.sentinel.gatekeeper.blocker.AddressBlockerProcessor
import com.xpdustry.sentinel.history.HistoryCommand
import com.xpdustry.sentinel.history.HistoryRendererImpl
import com.xpdustry.sentinel.history.LiveHistoryReader
import com.xpdustry.sentinel.util.service
import java.net.http.HttpClient
import java.util.Locale
import kotlin.io.path.notExists
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mindustry.Vars
import mindustry.net.Net
import mindustry.net.NetConnection
import mindustry.net.Packet
import mindustry.net.Packets
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.execution.ExecutionCoordinator

@Suppress("unused")
internal class SentinelPlugin : AbstractMindustryPlugin(), SentinelAPI {
    private val processor = PluginAnnotationProcessor.events(this)
    private val http = HttpClient.newHttpClient()
    private lateinit var clientsCommand: MindustryCommandManager<CommandSender>
    private lateinit var serversCommand: MindustryCommandManager<CommandSender>
    private lateinit var config: SentinelConfig

    override val gatekeeper = GatekeeperPipelineImpl()
    override val renderer = HistoryRendererImpl()
    override val liveHistoryReader = LiveHistoryReader(SentinelConfig().history)

    override fun onInit() {
        val file = directory.resolve("config.yaml")
        if (file.notExists()) {
            logger.debug("Config file not found. Defaulting to empty config.")
            config = SentinelConfig()
        } else {
            config =
                ConfigLoaderBuilder.empty()
                    .withClassLoader(SentinelPlugin::class.java.classLoader)
                    .addDefaults()
                    .addPathSource(file)
                    .strict()
                    .build()
                    .loadConfigOrThrow()
        }
    }

    override fun onClientCommandsRegistration(handler: CommandHandler) {
        clientsCommand =
            MindustryCommandManager(
                this, ExecutionCoordinator.simpleCoordinator(), SenderMapper.identity())
        clientsCommand.initialize(handler)
    }

    override fun onServerCommandsRegistration(handler: CommandHandler) {
        serversCommand =
            MindustryCommandManager(
                this, ExecutionCoordinator.simpleCoordinator(), SenderMapper.identity())
        serversCommand.initialize(handler)
    }

    override fun onLoad() {
        loadTranslations()
        loadHistory()
        loadGatekeeper()
    }

    override fun addListener(listener: PluginListener) {
        super.addListener(listener)
        processor.process(listener)
    }

    private fun loadTranslations() {
        val bundle = BundleTranslationSource.create(Locale.ENGLISH)
        bundle.registerAll(
            ResourceBundles.fromClasspathDirectory(
                javaClass, "com/xpdustry/sentinel/bundles", "bundle"),
            ResourceBundles::getMessageFormatTranslation)
        DistributorProvider.get()
            .serviceManager
            .register(this, TranslationSource::class.java, bundle, Priority.LOW)
    }

    private fun loadHistory() {
        if (config.history.enabled) {
            addListener(liveHistoryReader)
            addListener(HistoryCommand(clientsCommand, serversCommand, liveHistoryReader, renderer))
        }
    }

    private fun loadGatekeeper() {
        if (config.gatekeeper.enabled) {
            val serverListenersField = Net::class.java.getDeclaredField("serverListeners")
            serverListenersField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val serverListeners =
                serverListenersField.get(Vars.net)
                    as ObjectMap<Class<out Packet>, Cons2<NetConnection, Any>>
            val previous = serverListeners[Packets.ConnectPacket::class.java]!!
            Vars.net.handleServer(Packets.ConnectPacket::class.java) { con, packet ->
                SentinelScope.launch {
                    val locale = Locale.forLanguageTag((packet.locale ?: "en").replace('_', '-'))
                    val result =
                        withTimeoutOrNull(10.seconds) {
                            gatekeeper
                                .pump(
                                    GatekeeperContext(
                                        packet.name,
                                        MUUID.of(packet.uuid, packet.usid),
                                        InetAddresses.forString(con.address),
                                        locale))
                                .await()
                        }
                    var kick: Component? = null
                    var time = 0L
                    when (result) {
                        is GatekeeperResult.Failure -> {
                            kick = result.reason
                            time = result.time.toMillis()
                        }
                        null -> {
                            kick = translatable("sentinel.gatekeeper.timeout")
                            time = 0L
                        }
                        GatekeeperResult.Success -> Unit
                    }
                    if (kick == null) {
                        previous.get(con, packet)
                    } else {
                        con.kick(
                            ComponentStringBuilder.mindustry(
                                    DynamicKeyContainer.builder()
                                        .putConstant(StandardKeys.LOCALE, locale)
                                        .build())
                                .append(kick)
                                .toString(),
                            time)
                    }
                }
            }
        }
        service(GatekeeperPipeline.PROCESSOR_TYPE) {
            register(AddressBlockerProcessor(config.gatekeeper.filters.address, http))
            if (config.gatekeeper.filters.crackedClient) {
                register(CrackedClientProcessor)
            }
            if (config.gatekeeper.filters.link) {
                register(LinkDetectionProcessor)
            }
        }
    }

    companion object {
        internal val INSTANCE: SentinelPlugin
            get() = Vars.mods.getMod(SentinelPlugin::class.java).main as SentinelPlugin
    }
}
