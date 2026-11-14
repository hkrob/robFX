package app.robfx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import app.robfx.unitprice.Recents
import app.robfx.Field
import app.robfx.FocusTarget
import app.robfx.HomeTab
import app.robfx.UnitPriceViewModel
import app.robfx.calc.Format
import app.robfx.data.PairRate
import app.robfx.unitprice.Comparison
import app.robfx.unitprice.Evaluation
import app.robfx.unitprice.ItemResult
import app.robfx.unitprice.MeasureUnit
import app.robfx.unitprice.SavedComparison
import app.robfx.unitprice.UnitFormat
import app.robfx.unitprice.UnitGroup
import app.robfx.unitprice.UnitItem
import app.robfx.unitprice.UnitPrice
import app.robfx.unitprice.Units
import app.robfx.unitprice.Verdict
import app.robfx.ui.theme.Pal

data class UnitPriceUi(
    val comparison: Comparison,
    val evaluation: Evaluation,
    val focus: FocusTarget?,
    val keypadOpen: Boolean,
    val hasRecents: Boolean,
    val status: PairRate?,
    val haptics: Boolean = true,
)

interface UnitPriceActions {
    fun focusOn(item: Int, field: Field)
    fun key(k: String)
    fun next()
    fun setGroup(g: UnitGroup)
    fun setBasis(id: String)
    fun addItem()
    fun removeItem(i: Int)
    fun clearAll()
    fun toggleKeypad()
    fun pickUnit(item: Int)
    fun pickCurrency(item: Int)
    fun pickHomeCurrency()
    fun openRecents()
    fun pickTag()
    fun openSettings()
    fun selectTab(t: HomeTab)
}

private sealed interface UnitSheet {
    data object None : UnitSheet
    data class Unit(val item: Int) : UnitSheet
    data class Currency(val item: Int) : UnitSheet
    data object Home : UnitSheet
    data object Recents : UnitSheet
    data object Tag : UnitSheet
}

/** Stateful wrapper: owns which sheet is open, wires everything else to the ViewModel. */
@Composable
fun UnitPriceScreen(vm: UnitPriceViewModel, haptics: Boolean, onSelectTab: (HomeTab) -> Unit, onOpenSettings: () -> Unit) {
    val comparison by vm.comparison.collectAsState()
    val evaluation by vm.evaluation.collectAsState()
    val focus by vm.focus.collectAsState()
    val keypadOpen by vm.keypadOpen.collectAsState()
    val recents by vm.recents.collectAsState()
    val status by vm.status.collectAsState()
    val currencyRecents by vm.currencyRecents.collectAsState()
    val tagSuggestions by vm.tagSuggestions.collectAsState()
    var sheet by remember { mutableStateOf<UnitSheet>(UnitSheet.None) }

    val actions = remember(vm) {
        object : UnitPriceActions {
            override fun focusOn(item: Int, field: Field) = vm.focusOn(item, field)
            override fun key(k: String) = vm.key(k)
            override fun next() = vm.next()
            override fun setGroup(g: UnitGroup) = vm.setGroup(g)
            override fun setBasis(id: String) = vm.setBasis(id)
            override fun addItem() = vm.addItem()
            override fun removeItem(i: Int) = vm.removeItem(i)
            override fun clearAll() = vm.clearAll()
            override fun toggleKeypad() = vm.toggleKeypad()
            override fun pickUnit(item: Int) { sheet = UnitSheet.Unit(item) }
            override fun pickCurrency(item: Int) { sheet = UnitSheet.Currency(item) }
            override fun pickHomeCurrency() { sheet = UnitSheet.Home }
            override fun openRecents() { sheet = UnitSheet.Recents }
            override fun pickTag() { sheet = UnitSheet.Tag }
            override fun openSettings() = onOpenSettings()
            override fun selectTab(t: HomeTab) = onSelectTab(t)
        }
    }

    UnitPriceContent(UnitPriceUi(comparison, evaluation, focus, keypadOpen, recents.isNotEmpty(), status, haptics), actions)

    val close = { sheet = UnitSheet.None }
    when (val s = sheet) {
        UnitSheet.None -> {}
        is UnitSheet.Unit -> AppSheet(close) {
            UnitPicker(comparison.group, comparison.items.getOrNull(s.item)?.unit) { vm.setUnit(s.item, it); close() }
        }
        is UnitSheet.Currency -> AppSheet(close) {
            CurrencyPicker(currencyRecents, "Price currency", comparison.items.getOrNull(s.item)?.currency) { vm.setCurrency(s.item, it); close() }
        }
        UnitSheet.Home -> AppSheet(close) {
            CurrencyPicker(currencyRecents, "Compare in", comparison.currency) { vm.setHomeCurrency(it); close() }
        }
        UnitSheet.Tag -> AppSheet(close) {
            TagEditor(comparison.tag, tagSuggestions) { vm.setTag(it); close() }
        }
        UnitSheet.Recents -> AppSheet(close) {
            RecentsList(recents, onPick = { vm.restore(it); close() }, onClear = { vm.clearRecents(); close() })
        }
    }
}

@Composable
fun UnitPriceContent(ui: UnitPriceUi, actions: UnitPriceActions) {
    val pal = Pal.current
    val cmp = ui.comparison
    BoxWithConstraints(Modifier.fillMaxSize().background(pal.bg)) {
        val screenW = maxWidth
        val wide = maxWidth > maxHeight && maxWidth >= 560.dp

        @Composable
        fun ColumnScope.Body() {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(ui.status) { actions.openSettings() }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable(onClick = actions::pickHomeCurrency).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("In ", color = pal.subtext, fontSize = 13.sp)
                    Text(cmp.currency, color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ExpandMore, "Compare in ${cmp.currency}", tint = pal.accent, modifier = Modifier.size(16.dp))
                }
                IconBtn(Icons.Outlined.History, "Recent comparisons", actions::openRecents, if (ui.hasRecents) pal.keyText else pal.subtext.copy(alpha = 0.5f))
                IconBtn(Icons.Outlined.Settings, "Settings", actions::openSettings, pal.keyText)
            }
            HomeTabs(HomeTab.UnitPrice, actions::selectTab)

            // Group chips: switching group resets units to that group's default, never mixes grams with millilitres.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                UnitGroup.entries.forEach { g -> Chip(g.label, g == cmp.group) { actions.setGroup(g) } }
            }

            VerdictBanner(ui.evaluation, cmp, cmp.tag, actions::pickTag)

            val listState = rememberLazyListState()
            LaunchedEffect(ui.focus?.item) {
                val i = ui.focus?.item ?: return@LaunchedEffect
                if (i >= cmp.items.size) return@LaunchedEffect
                val info = listState.layoutInfo
                val v = info.visibleItemsInfo.firstOrNull { it.index == i }
                val fully = v != null && v.offset >= 0 && v.offset + v.size <= info.viewportEndOffset
                if (!fully) listState.animateScrollToItem(i)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(cmp.items.size, key = { it }) { i ->
                    ItemCard(i, cmp.items[i], ui.evaluation.items.getOrNull(i) ?: ItemResult.Incomplete, ui.evaluation.verdict, cmp, ui.focus, cmp.items.size > 2, actions)
                }
                if (cmp.items.size < Comparison.MAX_ITEMS) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.key).clickable(onClick = actions::addItem).padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Add, null, tint = pal.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add item", color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PER", color = pal.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
                Units.bases.getValue(cmp.group).forEach { b -> Chip(b.label, b.id == cmp.basis) { actions.setBasis(b.id) } }
                Spacer(Modifier.weight(1f))
                Text(
                    "Clear",
                    color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = actions::clearAll).padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize().statusBarsPadding()) {
                Column(Modifier.weight(1f).fillMaxHeight()) { Body() }
                BoxWithConstraints(Modifier.width(minOf(screenW * 0.4f, 380.dp)).fillMaxHeight().navigationBarsPadding()) {
                    val keyH = ((maxHeight - 4.dp) / 5).coerceIn(44.dp, 150.dp)
                    Keypad(
                        keyHeight = keyH, haptics = ui.haptics, onKey = actions::key, onSwap = actions::next,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        swapIcon = Icons.AutoMirrored.Outlined.ArrowForward, swapDesc = "Next field",
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Body()
                val rot by androidx.compose.animation.core.animateFloatAsState(if (ui.keypadOpen) 0f else 180f, label = "chev")
                Box(Modifier.fillMaxWidth().height(32.dp).clickable(onClick = actions::toggleKeypad), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ExpandMore, if (ui.keypadOpen) "Hide keypad" else "Show keypad", tint = pal.subtext, modifier = Modifier.rotate(rot))
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = ui.keypadOpen,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                ) {
                    BoxWithConstraints {
                        val keyH: Dp = (maxHeight.coerceAtLeast(600.dp) * 0.06f).coerceIn(44.dp, 58.dp)
                        Keypad(
                            keyHeight = keyH, haptics = ui.haptics, onKey = actions::key, onSwap = actions::next,
                            modifier = Modifier.navigationBarsPadding(),
                            swapIcon = Icons.AutoMirrored.Outlined.ArrowForward, swapDesc = "Next field",
                        )
                    }
                }
                if (!ui.keypadOpen) Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val pal = Pal.current
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) pal.key else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(label, color = if (selected) pal.accent else pal.subtext, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun VerdictBanner(e: Evaluation, c: Comparison, tag: String, onTag: () -> Unit) {
    val pal = Pal.current
    val basis = Units.basis(c.group, c.basis)
    val stale = e.items.any { it is ItemResult.Ready && it.stale }
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 4.dp).height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            when (val v = e.verdict) {
                // One yardstick throughout: everything is measured against the best item, so the banner and the badges agree.
                is Verdict.Winner -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(pal.positive))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${UnitPrice.itemLabel(v.index)} is the best value", color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Next best costs ${UnitFormat.plainPercent(v.nextBestMore)} more per ${basis.label}", color = pal.subtext, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                is Verdict.Tie -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(pal.accent))
                    Spacer(Modifier.width(10.dp))
                    Text("Same value", color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("  within 0.5%", color = pal.subtext, fontSize = 13.sp)
                }
                Verdict.None -> Text(
                    if (e.readyCount == 1) "Fill in a second item" else "Enter a price and size for two items",
                    color = pal.subtext, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (stale) Text("Using offline exchange rates", color = pal.warning, fontSize = 11.sp)
        }
        TagChip(tag, onTag)
    }
}

/** Optional label for the comparison. Quiet when empty, accent-coloured once it says something. */
@Composable
private fun TagChip(tag: String, onClick: () -> Unit) {
    val pal = Pal.current
    val set = tag.isNotBlank()
    Row(
        Modifier.widthIn(max = 120.dp).clip(RoundedCornerShape(50)).background(pal.key).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocalOffer, if (set) "Tag: $tag" else "Add a tag", tint = if (set) pal.accent else pal.subtext, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (set) tag else "Tag", color = if (set) pal.accent else pal.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ItemCard(
    index: Int,
    item: UnitItem,
    result: ItemResult,
    verdict: Verdict,
    cmp: Comparison,
    focus: FocusTarget?,
    canRemove: Boolean,
    actions: UnitPriceActions,
) {
    val pal = Pal.current
    val basis = Units.basis(cmp.group, cmp.basis)
    val unit = Units.byId[item.unit]
    val isBest = verdict is Verdict.Winner && verdict.index == index
    val isTie = verdict is Verdict.Tie && index in verdict.indices

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.key).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(UnitPrice.itemLabel(index), color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                when {
                    isBest -> Badge("BEST", pal.positive)
                    isTie -> Badge("SAME", pal.accent)
                    result is ItemResult.Ready && result.rank > 0 -> Badge("${UnitFormat.plainPercent(result.percentAboveBest)} more", pal.subtext)
                }
            }
            // The answer sits on the title row, so both cards stay on screen with the keypad open.
            when (result) {
                is ItemResult.Ready -> Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(150.dp)) {
                    FitText(
                        candidates = listOf(UnitFormat.money(cmp.currency, result.unitPrice)),
                        color = if (isBest) pal.positive else pal.text,
                        maxSize = 26, preferredMin = 18, hardMin = 12,
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                    )
                    Text("per ${basis.label}", color = pal.subtext, fontSize = 11.sp, maxLines = 1)
                }
                is ItemResult.NoRate -> Text("No rate for ${result.currency}", color = pal.negative, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                ItemResult.Mismatch -> Text("Unit mismatch", color = pal.negative, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                ItemResult.Incomplete -> Text("Enter price & size", color = pal.subtext.copy(alpha = 0.8f), fontSize = 13.sp)
            }
            if (canRemove) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { actions.removeItem(index) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "Remove ${UnitPrice.itemLabel(index)}", tint = pal.subtext, modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("PRICE", item.price, "0", focus == FocusTarget(index, Field.Price), Modifier.weight(1.15f),
                chip = item.currency, chipAccent = item.currency != cmp.currency, onChip = { actions.pickCurrency(index) }) { actions.focusOn(index, Field.Price) }
            Tile("SIZE", item.size, "0", focus == FocusTarget(index, Field.Size), Modifier.weight(1.15f),
                chip = unit?.short ?: "?", chipAccent = false, onChip = { actions.pickUnit(index) }) { actions.focusOn(index, Field.Size) }
            Tile("QTY", item.qty, "1", focus == FocusTarget(index, Field.Qty), Modifier.weight(0.7f), chip = null, chipAccent = false, onChip = {}) { actions.focusOn(index, Field.Qty) }
        }
        if (result is ItemResult.Ready) {
            val notes = buildList {
                unit?.let { UnitFormat.total(item.qty, item.size, it) }?.let(::add)
                if (result.converted) add("${Format.rate(result.rate)} ${cmp.currency} per ${item.currency}${if (result.stale) " \u00B7 offline rate" else ""}")
            }
            notes.forEach { Text(it, color = pal.subtext, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** One input well. Tapping it gives it the keypad; the chip in its corner changes currency or unit without moving focus. */
@Composable
private fun Tile(
    label: String,
    value: String,
    placeholder: String,
    focused: Boolean,
    modifier: Modifier,
    chip: String?,
    chipAccent: Boolean,
    onChip: () -> Unit,
    onFocus: () -> Unit,
) {
    val pal = Pal.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(pal.bg)
            .border(2.dp, if (focused) pal.accent else Color.Transparent, shape)
            .clickable(onClick = onFocus)
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = pal.subtext, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
            if (chip != null) {
                Text(
                    "$chip ▾", color = if (chipAccent) pal.accent else pal.subtext, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onChip).padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        val shown = if (value.isEmpty()) placeholder else Format.pretty(value)
        FitText(
            candidates = listOf(shown),
            color = if (value.isEmpty()) pal.subtext.copy(alpha = 0.55f) else pal.text,
            maxSize = 24, preferredMin = 15, hardMin = 11,
            modifier = Modifier.fillMaxWidth().height(32.dp).padding(end = 6.dp),
            textAlign = TextAlign.Start, alignment = Alignment.CenterStart,
        )
    }
}

@Composable
private fun UnitPicker(group: UnitGroup, selected: String?, onPick: (String) -> Unit) {
    val pal = Pal.current
    Column(Modifier.fillMaxHeight(0.8f).navigationBarsPadding()) {
        Title("${group.label} unit")
        LazyColumn(Modifier.weight(1f)) {
            items(Units.ofGroup(group), key = { it.id }) { u: MeasureUnit ->
                Row(Modifier.fillMaxWidth().clickable { onPick(u.id) }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(u.short, color = if (u.id == selected) pal.accent else pal.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp))
                    Text(u.name, color = pal.subtext, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (u.id == selected) Box(Modifier.size(8.dp).clip(CircleShape).background(pal.accent))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TagEditor(current: String, suggestions: List<String>, onSave: (String) -> Unit) {
    val pal = Pal.current
    var text by remember { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
        Title("Tag this comparison")
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.key).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.LocalOffer, null, tint = pal.subtext, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("e.g. Pasta, Milk, Hardware store", color = pal.subtext, fontSize = 16.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(Comparison.MAX_TAG) },
                    singleLine = true,
                    textStyle = TextStyle(color = pal.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(pal.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Text("${text.length}/${Comparison.MAX_TAG}", color = pal.subtext, fontSize = 11.sp)
        }
        if (suggestions.isNotEmpty()) {
            Header("Used before")
            FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s ->
                    Text(
                        s, color = if (s.equals(text.trim(), ignoreCase = true)) pal.accent else pal.keyText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(pal.key).clickable { text = s }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (current.isNotBlank()) {
                Text(
                    "Remove tag", color = pal.negative, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSave("") }.padding(horizontal = 4.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(50)).background(pal.accent).clickable { onSave(text) }.padding(horizontal = 28.dp, vertical = 12.dp)) {
                Text("Save", color = pal.onAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
internal fun RecentsList(list: List<SavedComparison>, onPick: (SavedComparison) -> Unit, onClear: () -> Unit) {
    val pal = Pal.current
    var filter by remember { mutableStateOf<String?>(null) }
    val tags = remember(list) { Recents.tags(list, limit = 20) }
    val shown = remember(list, filter) { filter?.let { f -> list.filter { it.comparison.tag.equals(f, ignoreCase = true) } } ?: list }
    Column(Modifier.fillMaxHeight(0.8f).navigationBarsPadding()) {
        Title("Recent comparisons")
        if (tags.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { t ->
                    val sel = t.equals(filter, ignoreCase = true)
                    Text(
                        t, color = if (sel) pal.accent else pal.subtext, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) pal.key else Color.Transparent)
                            .clickable { filter = if (sel) null else t }.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        if (list.isEmpty()) {
            Text("Comparisons you finish are kept here.", color = pal.subtext, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.savedAt }) { s ->
                Column(Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    if (s.comparison.tag.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocalOffer, null, tint = pal.accent, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.comparison.tag, color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(s.summary, color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(ago(s.savedAt), color = pal.subtext, fontSize = 12.sp)
                }
            }
        }
        if (list.isNotEmpty()) {
            Text(
                "Clear history", color = pal.negative, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClear).padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}
