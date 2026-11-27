package app.lawnchair.qsb

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.AttributeSet
import androidx.core.view.children
import app.fugueti.qsb.DockSearchBarSize
import com.android.launcher3.BaseActivity
import com.android.launcher3.qsb.QsbContainerView
import com.android.launcher3.views.ActivityContext

class GoogleQsbContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : QsbContainerView(context, attrs, defStyleAttr) {

    /** Fugueti: height of Google's widget inside its slot (Search bar > Size). */
    private var barHeight = 0

    /**
     * Fugueti: Google's widget fills whatever it gets, so it was taller and wider than the
     * Lawnchair bar. On phones it gets the size of Search bar > Size (100% = Lawnchair's bar),
     * centred in its slot, in the dock or at the top of the screen.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dp = runCatching { ActivityContext.lookupContext<BaseActivity>(context).deviceProfile }.getOrNull()
        if (dp == null || !dp.deviceProperties.isPhone) {
            barHeight = 0
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val slotHeight = MeasureSpec.getSize(heightMeasureSpec)
        val size = DockSearchBarSize.measure(context, dp, MeasureSpec.getSize(widthMeasureSpec), slotHeight)
        barHeight = size.height
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(size.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(size.height, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(size.width, slotHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (barHeight <= 0) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }
        val offset = (bottom - top - barHeight) / 2
        children.forEach { it.layout(0, offset, it.measuredWidth, offset + it.measuredHeight) }
    }

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
