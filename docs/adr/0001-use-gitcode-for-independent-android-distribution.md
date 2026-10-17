# Use GitCode for independent Android distribution

MyCodex Android is currently an independently distributed application, so its official builds will check and install updates from GitCode rather than introduce a Google Play build variant now. The update capability must remain isolated so a future Google Play build can omit independent self-update behavior and comply with Play distribution rules without redesigning the rest of the application.
