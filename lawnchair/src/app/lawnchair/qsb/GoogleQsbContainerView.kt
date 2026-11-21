package app.lawnchair.qsb

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.AttributeSet
import com.android.launcher3.qsb.QsbContainerView

class GoogleQsbContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : QsbContainerView(context, attrs, defStyleAttr) {

    class QsbFragment : QsbContainerView.QsbFragment() {
        override fun isQsbEnabled(): Boolean = true

        /**
         * Fugueti: the Google app exposes two search bar widgets. The "searchbox" category one
         * (PremiumSearchWidgetProvider) is what upstream picks, but it renders nothing outside the
         * Pixel launcher, so the dock bar looked empty. Prefer the regular Google search widget
         * (the one the Pixel launcher itself hosts) and fall back to the upstream choice.
         */
        override fun getSearchWidgetProvider(): AppWidgetProviderInfo? {
            val context = context ?: return super.getSearchWidgetProvider()
            val packageName = getSearchWidgetPackageName(context)
            if (packageName == GOOGLE_PACKAGE) {
                AppWidgetManager.getInstance(context)
                    .getInstalledProvidersForPackage(GOOGLE_PACKAGE, null)
                    .firstOrNull { it.provider.className == GOOGLE_SEARCH_WIDGET }
                    ?.let { return it }
            }
            return super.getSearchWidgetProvider()
        }

        private companion object {
            const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
            const val GOOGLE_SEARCH_WIDGET =
                "com.google.android.apps.gsa.staticplugins.searchwidget.GoogleSearchWidgetProvider"
        }
    }
}
