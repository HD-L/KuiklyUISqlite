package net.shantu.kuiklysqlite.example.adapter

import android.app.Activity
import android.content.Context
import com.tencent.kuikly.core.render.android.adapter.IKRRouterAdapter
import net.shantu.kuiklysqlite.example.KuiklyRenderActivity
import org.json.JSONObject

object KRRouterAdapter : IKRRouterAdapter {

    override fun openPage(
        context: Context,
        pageName: String,
        pageData: JSONObject,
    ) {
        KuiklyRenderActivity.start(context, pageName, pageData)
    }

    override fun closePage(context: Context) {
        (context as? Activity)?.finish()
    }
}