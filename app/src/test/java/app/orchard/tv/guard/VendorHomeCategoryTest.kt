package app.orchard.tv.guard

import org.junit.Assert.*
import org.junit.Test

class VendorHomeCategoryTest {
    @Test fun rejectsFactoryAndSecondaryHomesWithoutRejectingRealVendorHomes() {
        assertFalse(HomeGuardService.isVendorHomeCategory("android.intent.category.CVTE_AT_HOME"))
        assertFalse(HomeGuardService.isVendorHomeCategory("android.intent.category.SECONDARY_HOME"))
        assertFalse(HomeGuardService.isVendorHomeCategory("android.intent.category.LAUNCHER"))
        assertTrue(HomeGuardService.isVendorHomeCategory("android.intent.category.HOME.CC"))
        assertTrue(HomeGuardService.isVendorHomeCategory("com.tcl.category.TCL_HOME"))
        assertTrue(HomeGuardService.isVendorHomeCategory("android.intent.category.COMMON_HOME"))
    }
}
