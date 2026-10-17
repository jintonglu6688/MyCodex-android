package com.mycodex.mobile

import androidx.annotation.DrawableRes

enum class MainTabDestination(
    val route: String,
    val label: String,
    @param:DrawableRes val iconRes: Int,
) {
    Coding(
        route = "coding",
        label = "编程",
        iconRes = R.drawable.ic_tab_coding,
    ),
    Office(
        route = "office",
        label = "办公",
        iconRes = R.drawable.ic_tab_office,
    ),
    Profile(
        route = "profile",
        label = "我的",
        iconRes = R.drawable.ic_tab_profile,
    ),
}
