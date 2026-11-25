# robFX changelog

Newest first. Each "## version" section is used for that version's GitHub release notes
and for "What's new" on the app's About tab, so write it for people, not for developers.

## 1.9.1
- Fixed: rates could be a day behind. Two of the three sources were still serving the previous day's rates while AUD/HKD had moved 1%, and the app showed their median (5.20 instead of 5.15) while saying the sources agreed.
- Added Coinbase as a fourth source. It follows the market through the day, unlike the once-a-day feeds.
- A source whose data is more than 18 hours behind the freshest one is now left out of the rate, and the update time shows when the numbers were set, not just when they were downloaded.
- Currency-API is read from its own always-fresh mirror first, not a CDN copy that could be a day old.
- The chart now ends at the live rate, so its last point and change match the rate above it.

## 1.9.0
- In other currencies: from the converter's ⋮ menu, see your amount in a list of currencies at once. Add or remove currencies; tap one to convert into it.
- Export rate history: daily ECB rates for the pair as a CSV file, for the past month, quarter or year, calendar years, and Australian (Jul–Jun) and Hong Kong (Apr–Mar) financial years. Weekends and holidays use the last published rate and say which day it came from.
- Move alerts: get notified when a pair moves a set percentage either way.
- Keep watching: an alert can stay on after it fires. A target alert fires again once the rate has come back and crosses again; a move alert watches for the next move from where it ended.
- Markets shows a banner when no rate source can be reached, with how old the rates are and a Retry.
- The converter's settings button now lives in the ⋮ menu, which keeps the top bar uncluttered.

## 1.8.0
- Rate alerts: a target the rate has already passed is no longer accepted (it used to fire on the very next check). The direction follows the target you type, and comma decimals work.
- Alerts are now also checked whenever the app refreshes rates, not only every 30 minutes in the background.
- A fired alert shows the rate and date it hit; one you switched off by hand says "paused". Re-arming clears it.
- Tapping an alert notification opens that pair; tapping the update notification opens the update, ready to install.
- Saved rates are written safely, so a crash mid-save can't lose them.

## 1.7.5
- Tapping the "update available" banner now opens straight to the update: it is the first thing on the screen, titled "Update available", and the Update button pulses so the next step is obvious.

## 1.7.4
- Chart high and low labels now sit on the side away from the line, so they never cover it.
- Slightly shorter keypad keys on phones, giving the chart about 25dp more height.

## 1.7.3
- Small charts are easier to read: the line is coloured green or red by direction, and the high and low are labelled on the chart itself.
- With the keypad open, the chart header takes one line instead of two, giving the line more height.
- Update times now include the date ("21 Nov 2026 10:07"), so a screenshot always shows when it was taken.

## 1.7.2
- Tapping a field selects it: the next number you type replaces what was there. The selected value is highlighted.
- Operators and backspace still carry on from the existing value, so 5 then + 2 works as before.
- Unit price: tap a unit price to change the currency everything is compared in (same as "In ..." at the top).

## 1.7.1
- Unit price, Count: size defaults to 1, so a price alone is enough to compare.
- Unit price: "Clear" is now "Save & Clear", which is what it does.

## 1.7.0
- New About tab: what robFX does, what's new, and how to get in touch.
- Releases now publish themselves, with these notes, whenever a new version lands.

## 1.6.0
- Decimals settings for rates, converted amounts and unit prices (Settings, Decimals).

## 1.5.1
- Widgets: choose how many decimals each widget shows (2 by default).

## 1.5.0
- Three widgets, each configured on its own: Single pair, Watchlist and Quick convert.
- Per widget: theme, transparency, flags, change, updated time, change period and tap action.

## 1.4.0
- Every screen adapts to small phones, large fonts and landscape.
- Tapping an amount in the converter brings back a folded keypad.

## 1.3.2
- Recently used names and tags are always offered when naming items.

## 1.3.0
- Unit price: name each item, for example by shop, and see it in the verdict.

## 1.2.2
- Unit price: tapping a field opens the keypad.

## 1.2.1
- Fixed a freeze when opening Unit price on a slow connection.
- Diagnostic report in Settings for bug reports.

## 1.2.0
- Unit price: tag comparisons and find them again in history.

## 1.1.0
- New Unit price tab: compare shop items by price per unit across weight, volume, length and count.

## 1.0.4
- Targets Android 17. Markets sorting. Layout for tablets and landscape.

## 1.0.3
- Automatic updates from GitHub releases.

## 1.0.0
- First release: currency converter with rates cross-checked across three sources, charts, alerts and a widget.
