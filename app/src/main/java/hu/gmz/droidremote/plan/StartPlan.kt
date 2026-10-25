package hu.gmz.droidremote.plan

object StartPlan {

    fun needsHome(currentPackage: String?, ownPackage: String): Boolean =
        currentPackage != null && currentPackage == ownPackage
}
