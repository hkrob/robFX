package app.robfx

import app.robfx.data.PairRate
import app.robfx.data.Quality
import app.robfx.data.source.json
import app.robfx.unitprice.Comparison
import app.robfx.unitprice.Evaluation
import app.robfx.unitprice.ItemResult
import app.robfx.unitprice.Recents
import app.robfx.unitprice.SavedComparison
import app.robfx.unitprice.UnitFormat
import app.robfx.unitprice.UnitGroup
import app.robfx.unitprice.UnitItem
import app.robfx.unitprice.UnitPrice
import app.robfx.unitprice.Units
import app.robfx.unitprice.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitPriceTest {
    private val noRates: (String, String) -> PairRate? = { _, _ -> null }

    private fun cmp(vararg items: UnitItem, group: UnitGroup = UnitGroup.Weight, basis: String = "100g", home: String = "HKD") =
        Comparison(group = group, basis = basis, currency = home, items = items.toList())

    private fun g(price: String, size: String, qty: String = "", unit: String = "g", ccy: String = "HKD") = UnitItem(price, size, qty, unit, ccy)

    private fun ready(e: Evaluation, i: Int) = e.items[i] as ItemResult.Ready

    @Test fun exactUnitDefinitions() {
        fun f(id: String) = Units.byId.getValue(id).factor
        assertEquals(453.59237, f("lb"), 1e-9)
        assertEquals(28.349523125, f("oz"), 1e-9)
        assertEquals(25.4, f("in"), 1e-9)
        assertEquals(304.8, f("ft"), 1e-9)
        assertEquals(914.4, f("yd"), 1e-9)
        assertEquals(29.5735295625, f("floz_us"), 1e-9)
        assertEquals(28.4130625, f("floz_uk"), 1e-9)
        assertEquals(3785.411784, f("gal_us"), 1e-9)
        assertEquals(4546.09, f("gal_uk"), 1e-9)
        assertEquals(473.176473, f("pint_us"), 1e-9)
        assertEquals(568.26125, f("pint_uk"), 1e-9)
        assertEquals(946.352946, f("qt_us"), 1e-9)
        assertEquals(236.5882365, f("cup_us"), 1e-9)
        assertEquals(12.0, f("dozen"), 0.0)
    }

    @Test fun everyUnitBelongsToAGroupWithBases() {
        for (g in UnitGroup.entries) {
            assertTrue(Units.ofGroup(g).isNotEmpty())
            assertTrue(Units.bases.getValue(g).isNotEmpty())
            Units.bases.getValue(g).forEach { assertEquals(g, Units.byId.getValue(it.unitId).group) }
            assertEquals(g, Units.byId.getValue(Units.defaultUnit(g, us = false)).group)
            assertEquals(g, Units.byId.getValue(Units.defaultUnit(g, us = true)).group)
        }
        assertTrue(Units.byId["pack"] == null)
    }

    @Test fun theBriefsExample() {
        // $5 for 100 g against $7 for 150 g: 5.00 vs 4.667 per 100 g, so B wins by 6.7%.
        val e = UnitPrice.evaluate(cmp(g("5", "100"), g("7", "150")), noRates)
        assertEquals(5.0, ready(e, 0).unitPrice, 1e-9)
        assertEquals(4.6667, ready(e, 1).unitPrice, 1e-4)
        val v = e.verdict as Verdict.Winner
        assertEquals(1, v.index)
        // Banner and badge agree: the loser costs 7.1% more, and that is the number the banner reports too.
        assertEquals(0.07142857, v.nextBestMore, 1e-6)
        assertEquals(v.nextBestMore, ready(e, 0).percentAboveBest, 1e-9)
        assertEquals(0, ready(e, 1).rank)
    }

    @Test fun mixedUnitsInTheSameGroup() {
        // 1 lb for 10 vs 400 g for 8: per 100 g that is 2.2046 vs 2.0, so the 400 g wins.
        val e = UnitPrice.evaluate(cmp(g("10", "1", unit = "lb"), g("8", "400")), noRates)
        assertEquals(10.0 / 453.59237 * 100, ready(e, 0).unitPrice, 1e-9)
        assertEquals(1, (e.verdict as Verdict.Winner).index)
    }

    @Test fun multipackQuantityMultipliesTheSize() {
        val six = UnitPrice.evaluate(cmp(g("12", "330", qty = "6", unit = "ml"), g("2", "330", unit = "ml"), group = UnitGroup.Volume, basis = "100ml"), noRates)
        assertEquals(ready(six, 1).unitPrice, ready(six, 0).unitPrice, 1e-9)
        assertEquals(1980.0, ready(six, 0).totalBase, 1e-9)
        assertTrue(six.verdict is Verdict.Tie)
    }

    @Test fun expressionsWorkInEveryField() {
        val e = UnitPrice.evaluate(cmp(g("2+3", "50×2"), g("7", "150")), noRates)
        assertEquals(5.0, ready(e, 0).unitPrice, 1e-9)
    }

    @Test fun incompleteOrZeroItemsAreExcludedNotTreatedAsFree() {
        val e = UnitPrice.evaluate(cmp(g("", "100"), g("5", ""), g("0", "100"), g("5", "0"), g("5", "100", qty = "0")), noRates)
        assertTrue(e.items.all { it is ItemResult.Incomplete })
        assertEquals(Verdict.None, e.verdict)
        val one = UnitPrice.evaluate(cmp(g("5", "100"), g("", "")), noRates)
        assertEquals(Verdict.None, one.verdict)
        assertEquals(1, one.readyCount)
    }

    @Test fun crossGroupUnitsCantBeCompared() {
        val e = UnitPrice.evaluate(cmp(g("5", "100"), g("5", "100", unit = "ml")), noRates)
        assertTrue(e.items[1] is ItemResult.Mismatch)
        assertEquals(Verdict.None, e.verdict)
    }

    @Test fun nearTieIsNoWinner() {
        val e = UnitPrice.evaluate(cmp(g("5", "100"), g("5.02", "100")), noRates)
        assertTrue(e.verdict is Verdict.Tie)
        assertEquals(listOf(0, 1), (e.verdict as Verdict.Tie).indices)
        val clear = UnitPrice.evaluate(cmp(g("5", "100"), g("5.1", "100")), noRates)
        assertTrue(clear.verdict is Verdict.Winner)
    }

    @Test fun threeItemsRankAndTheHeadlineIsAgainstTheRunnerUp() {
        val e = UnitPrice.evaluate(cmp(g("9", "100"), g("5", "100"), g("6", "100")), noRates)
        assertEquals(listOf(2, 0, 1), listOf(ready(e, 0).rank, ready(e, 1).rank, ready(e, 2).rank))
        val v = e.verdict as Verdict.Winner
        assertEquals(1, v.index)
        assertEquals(6.0 / 5.0 - 1, v.nextBestMore, 1e-9)
    }

    @Test fun foreignPricesConvertAndMissingRatesAreVisibleNotOneToOne() {
        val usdToHkd = PairRate(7.8, emptyMap(), 0.0, Quality.Consensus, 0)
        val rate: (String, String) -> PairRate? = { f, t -> if (f == "USD" && t == "HKD") usdToHkd else null }
        val e = UnitPrice.evaluate(cmp(g("10", "100", ccy = "USD"), g("70", "100", ccy = "HKD")), rate)
        assertEquals(78.0, ready(e, 0).unitPrice, 1e-9)
        assertTrue(ready(e, 0).converted)
        assertEquals(1, (e.verdict as Verdict.Winner).index)

        val missing = UnitPrice.evaluate(cmp(g("10", "100", ccy = "EUR"), g("70", "100")), rate)
        assertEquals(ItemResult.NoRate("EUR"), missing.items[0])
        assertEquals(Verdict.None, missing.verdict)
    }

    @Test fun staleRatesAreFlagged() {
        val stale = PairRate(7.8, emptyMap(), 0.0, Quality.Stale, 0)
        val e = UnitPrice.evaluate(cmp(g("10", "100", ccy = "USD"), g("70", "100")), { _, _ -> stale })
        assertTrue(ready(e, 0).stale)
        assertTrue(!ready(e, 1).stale)
    }

    @Test fun changingBasisChangesTheNumbersNotTheWinner() {
        val items = arrayOf(g("5", "100"), g("7", "150"))
        val kg = UnitPrice.evaluate(cmp(*items, basis = "kg"), noRates)
        assertEquals(50.0, ready(kg, 0).unitPrice, 1e-9)
        val lb = UnitPrice.evaluate(cmp(*items, basis = "lb"), noRates)
        assertEquals(5.0 / 100 * 453.59237, ready(lb, 0).unitPrice, 1e-9)
        assertEquals(1, (kg.verdict as Verdict.Winner).index)
        assertEquals(1, (lb.verdict as Verdict.Winner).index)
    }

    @Test fun groupAndItemEditingRules() {
        val c = Comparison.fresh(UnitGroup.Weight, "HKD", us = false)
        assertEquals(2, c.items.size)
        assertEquals("g", c.items[0].unit)
        assertEquals("100g", c.basis)

        val added = c.copy(items = listOf(c.items[0].copy(unit = "lb", currency = "USD"), c.items[1])).withItemAdded()
        assertEquals("lb", added.items[2].unit)
        assertEquals("USD", added.items[2].currency)

        var many = c
        repeat(10) { many = many.withItemAdded() }
        assertEquals(Comparison.MAX_ITEMS, many.items.size)
        assertEquals(2, c.withItemRemoved(0).items.size)
        assertEquals(5, many.withItemRemoved(5).items.size)

        val vol = c.withGroup(UnitGroup.Volume, us = false)
        assertEquals("ml", vol.items[0].unit)
        assertEquals("100ml", vol.basis)
        assertEquals("oz", Comparison.fresh(UnitGroup.Weight, "USD", us = true).items[0].unit)
    }

    @Test fun homeCurrencyChangeCarriesAlongItemsPricedInIt() {
        val c = cmp(g("1", "1", ccy = "HKD"), g("1", "1", ccy = "USD")).withHomeCurrency("EUR")
        assertEquals("EUR", c.currency)
        assertEquals(listOf("EUR", "USD"), c.items.map { it.currency })
    }

    @Test fun formatting() {
        assertEquals("4.67", UnitFormat.number(4.6667))
        assertEquals("1,234.50", UnitFormat.number(1234.5))
        assertEquals("0.0523", UnitFormat.number(0.05234))
        assertEquals("6.7%", UnitFormat.plainPercent(0.0667))
        assertEquals("12%", UnitFormat.plainPercent(0.12))
        val ml = Units.byId.getValue("ml")
        assertEquals("6 × 330 ml = 1,980 ml", UnitFormat.total("6", "330", ml))
        assertEquals(null, UnitFormat.total("", "330", ml))
        assertEquals(null, UnitFormat.total("1", "330", ml))
    }

    @Test fun comparisonSurvivesJson() {
        val c = cmp(g("5", "100"), g("7", "150", qty = "2", unit = "kg", ccy = "USD"))
        assertEquals(c, json.decodeFromString(Comparison.serializer(), json.encodeToString(Comparison.serializer(), c)))
    }

    @Test fun recentsDedupeCapAndOrder() {
        val c1 = cmp(g("5", "100"), g("7", "150"))
        fun s(t: Long, c: Comparison) = SavedComparison(t, "x$t", c)
        var list = emptyList<SavedComparison>()
        list = Recents.add(list, s(1, c1))
        list = Recents.add(list, s(2, cmp(g("1", "1"), g("2", "2"))))
        list = Recents.add(list, s(3, c1))
        assertEquals(listOf(3L, 2L), list.map { it.savedAt })
        repeat(20) { i -> list = Recents.add(list, s(100L + i, cmp(g("$i", "1"), g("2", "2")))) }
        assertEquals(Recents.CAP, list.size)
        assertEquals(119L, list.first().savedAt)
    }

    @Test fun tagsAreTidiedAndOptional() {
        assertEquals("", Comparison.cleanTag("   "))
        assertEquals("Pasta night", Comparison.cleanTag("  Pasta   night \n"))
        assertEquals(Comparison.MAX_TAG, Comparison.cleanTag("x".repeat(100)).length)
        val c = cmp(g("5", "100"), g("7", "150"))
        assertEquals("", c.tag)
        assertEquals("Milk", c.withTag(" Milk ").tag)
        assertEquals("", c.withTag("Milk").withTag("  ").tag)
    }

    @Test fun tagSurvivesJsonAndOldSavesWithoutOneStillLoad() {
        val tagged = cmp(g("5", "100"), g("7", "150")).withTag("Pasta")
        val back = json.decodeFromString(Comparison.serializer(), json.encodeToString(Comparison.serializer(), tagged))
        assertEquals("Pasta", back.tag)
        // A comparison saved by 1.1.0 has no "tag" key at all.
        val old = """{"version":1,"group":"Weight","basis":"100g","currency":"HKD","items":[{"price":"5","size":"100","unit":"g","currency":"HKD"}]}"""
        assertEquals("", json.decodeFromString(Comparison.serializer(), old).tag)
    }

    @Test fun retaggingASavedComparisonRenamesItInsteadOfDuplicating() {
        val plain = cmp(g("5", "100"), g("7", "150"))
        var list = Recents.add(emptyList(), SavedComparison(1, "s", plain))
        list = Recents.add(list, SavedComparison(2, "s", plain.withTag("Pasta")))
        assertEquals(1, list.size)
        assertEquals("Pasta", list[0].comparison.tag)
        // A genuinely different comparison with the same tag is its own entry.
        list = Recents.add(list, SavedComparison(3, "s", cmp(g("1", "1"), g("2", "2")).withTag("Pasta")))
        assertEquals(2, list.size)
    }

    @Test fun tagSuggestionsAreRecentFirstAndCaseInsensitivelyUnique() {
        fun s(t: Long, tag: String) = SavedComparison(t, "s", cmp(g("$t", "1"), g("2", "2")).withTag(tag))
        val list = listOf(s(4, "Milk"), s(3, ""), s(2, "pasta"), s(1, "Pasta"), s(0, "milk"))
        assertEquals(listOf("Milk", "pasta"), Recents.tags(list))
        assertEquals(listOf("Milk"), Recents.tags(list, limit = 1))
    }

    @Test fun itemsCanBeNamedAndFallBackToTheirLetter() {
        val c = cmp(g("5", "100"), g("7", "150")).withLabel(1, "  Coles   Central ")
        assertEquals("Item A", UnitPrice.itemName(c, 0))
        assertEquals("Coles Central", UnitPrice.itemName(c, 1))
        assertEquals("Item C", UnitPrice.itemName(c, 2))
        assertEquals("Item A", UnitPrice.itemName(c.withLabel(1, "x").withLabel(0, "   "), 0))
        assertEquals(Comparison.MAX_LABEL, Comparison.cleanLabel("y".repeat(60)).length)
        // Naming an item doesn't touch the numbers, and a new item starts unnamed even after the first was named.
        assertEquals(UnitPrice.evaluate(cmp(g("5", "100"), g("7", "150")), noRates), UnitPrice.evaluate(c, noRates))
        assertEquals("", c.withLabel(0, "Woolworths").withItemAdded().items[2].label)
    }

    @Test fun labelsSurviveJsonAndOldSavesWithoutOneStillLoad() {
        val named = cmp(g("5", "100"), g("7", "150")).withLabel(0, "Woolworths")
        val back = json.decodeFromString(Comparison.serializer(), json.encodeToString(Comparison.serializer(), named))
        assertEquals("Woolworths", back.items[0].label)
        val old = """{"version":1,"group":"Weight","basis":"100g","currency":"HKD","items":[{"price":"5","size":"100","unit":"g","currency":"HKD"}]}"""
        assertEquals("", json.decodeFromString(Comparison.serializer(), old).items[0].label)
    }

    @Test fun renamingAnItemInASavedComparisonRenamesItInHistory() {
        val plain = cmp(g("5", "100"), g("7", "150"))
        var list = Recents.add(emptyList(), SavedComparison(1, "s", plain))
        list = Recents.add(list, SavedComparison(2, "s", plain.withLabel(0, "Coles").withLabel(1, "Woolworths").withTag("Pasta")))
        assertEquals(1, list.size)
        assertEquals("Coles", list[0].comparison.items[0].label)
        // Different prices are a different comparison, even with the same shops.
        list = Recents.add(list, SavedComparison(3, "s", cmp(g("9", "100"), g("7", "150")).withLabel(0, "Coles").withLabel(1, "Woolworths")))
        assertEquals(2, list.size)
    }

    @Test fun labelSuggestionsAreRecentFirstAndUnique() {
        fun s(t: Long, a: String, b: String) = SavedComparison(t, "s", cmp(g("$t", "1"), g("2", "2")).withLabel(0, a).withLabel(1, b))
        val list = listOf(s(3, "Coles", "Aldi"), s(2, "", "coles"), s(1, "Woolworths", "Costco"))
        assertEquals(listOf("Coles", "Aldi", "Woolworths", "Costco"), Recents.labels(list))
        assertEquals(listOf("Coles", "Aldi"), Recents.labels(list, limit = 2))
    }

    @Test fun summaryUsesTheShopNameWhenThereIsOne() {
        val c = cmp(g("5", "100"), g("7", "150")).withLabel(1, "Costco")
        assertTrue(Recents.summary(c, UnitPrice.evaluate(c, noRates)).contains("best Costco"))
    }

    @Test fun summaryNamesTheBestItem() {
        val c = cmp(g("5", "100"), g("7", "150"))
        val e = UnitPrice.evaluate(c, noRates)
        assertTrue(Recents.summary(c, e).contains("best Item B"))
        assertTrue(Recents.summary(c, e).startsWith("Weight · 2 items"))
    }
}
