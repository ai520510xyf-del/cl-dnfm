package com.gamebot.ai.cloud

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Supabase 客户端管理器
 * 负责初始化和管理 Supabase 连接
 */
object SupabaseManager {

    private var _client: SupabaseClient? = null

    /**
     * 获取 Supabase 客户端
     */
    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException("Supabase not initialized. Call initialize() first.")

    /**
     * 初始化 Supabase
     *
     * @param context Android Context
     * @param supabaseUrl 你的 Supabase 项目 URL (https://xxx.supabase.co)
     * @param supabaseKey 你的 Supabase Anon Key
     */
    fun initialize(context: Context, supabaseUrl: String, supabaseKey: String) {
        if (_client != null) {
            return // 已经初始化
        }

        _client = createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey
        ) {
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = _client != null

    /**
     * 获取 Storage 模块
     */
    val storage: Storage
        get() = client.pluginManager.getPlugin(Storage)

    /**
     * 获取 Postgrest 模块（数据库）
     */
    val postgrest: Postgrest
        get() = client.pluginManager.getPlugin(Postgrest)

    /**
     * 获取 Realtime 模块
     */
    val realtime: Realtime
        get() = client.pluginManager.getPlugin(Realtime)

    /**
     * 获取 Functions 模块
     */
    val functions: Functions
        get() = client.pluginManager.getPlugin(Functions)
}
