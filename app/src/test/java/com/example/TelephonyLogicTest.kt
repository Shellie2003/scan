package com.example

import com.example.data.DefaultData
import com.example.data.model.Operator
import com.example.telephony.Contact
import com.example.telephony.ContactIndex
import com.example.telephony.ContactsRepository
import com.example.telephony.NumberUtils
import com.example.telephony.SimCard
import com.example.telephony.SimManager
import com.example.telephony.UssdFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure logic behind number routing, SIM matching and code formatting. */
class TelephonyLogicTest {

    private val operators = DefaultData.operators

    private fun operator(name: String) = operators.first { it.name == name }

    // ------------------------------------------------------------------ numbers

    @Test
    fun `national form accepts every way of writing a number`() {
        assertEquals("0341234567", NumberUtils.toNational("0341234567"))
        assertEquals("0341234567", NumberUtils.toNational("+261 34 12 345 67"))
        assertEquals("0341234567", NumberUtils.toNational("261341234567"))
        assertEquals("0341234567", NumberUtils.toNational("00261341234567"))
    }

    @Test
    fun `operator is recognised from the prefix of the number`() {
        assertEquals("Yas", NumberUtils.operatorForNumber("0341234567", operators)?.name)
        assertEquals("Yas", NumberUtils.operatorForNumber("+261381234567", operators)?.name)
        assertEquals("Orange", NumberUtils.operatorForNumber("0321234567", operators)?.name)
        assertEquals("Airtel", NumberUtils.operatorForNumber("0331234567", operators)?.name)
        assertNull(NumberUtils.operatorForNumber("0301234567", operators))
    }

    @Test
    fun `same number ignores the international form`() {
        assertTrue(NumberUtils.sameNumber("0341234567", "+261341234567"))
        assertFalse(NumberUtils.sameNumber("0341234567", "0341234568"))
    }

    @Test
    fun `service codes are never reformatted`() {
        assertTrue(NumberUtils.isServiceCode("#321#"))
        assertTrue(NumberUtils.isServiceCode("222*1"))
        assertTrue(NumberUtils.isServiceCode("111"))
        assertFalse(NumberUtils.isServiceCode("0341234567"))
    }

    @Test
    fun `a pasted list is split on every usual separator`() {
        val numbers = NumberUtils.splitNumbers("0341234567, 0321234567; 0331234567\n0341234567")
        assertEquals(listOf("0341234567", "0321234567", "0331234567"), numbers)
    }

    // -------------------------------------------------------------------- codes

    @Test
    fun `a complete ussd string is dialled as it is`() {
        assertEquals("#322*65#", UssdFormatter.format("#322*65#", operator("Yas")))
        assertEquals("*114*0100#", UssdFormatter.format("*114*0100#", operator("Airtel")))
        // Even for an operator that strips symbols: what is stored is what is dialled.
        assertEquals("#123#", UssdFormatter.format("#123#", operator("Orange")))
    }

    @Test
    fun `orange codes keep their plain form`() {
        assertEquals("222*1", UssdFormatter.format("222*1", operator("Orange")))
        assertEquals("20212345678901", UssdFormatter.format("20212345678901", operator("Orange")))
    }

    @Test
    fun `an incomplete code is wrapped for the other operators`() {
        assertEquals("*100*555#", UssdFormatter.format("100*555", operator("Airtel")))
        // A plain number stays a number.
        assertEquals("321", UssdFormatter.format("321", operator("Yas")))
    }

    @Test
    fun `recharge templates produce the expected code`() {
        val code = "12345678901234"
        assertEquals(
            "#321*12345678901234#",
            UssdFormatter.format(UssdFormatter.fill(operator("Yas").template, code = code), operator("Yas"))
        )
        assertEquals(
            "20212345678901234",
            UssdFormatter.format(
                UssdFormatter.fill(operator("Orange").template, code = code),
                operator("Orange")
            )
        )
    }

    @Test
    fun `a placeholder written twice is filled twice`() {
        // Orange repeats the number of the cashpoint inside its withdrawal code.
        val filled = UssdFormatter.fill(
            template = "#144*1*{NUMBER}*{NUMBER}*{AMOUNT}#",
            number = "0341234567",
            amount = "20000"
        )
        assertEquals("#144*1*0341234567*0341234567*20000#", filled)
    }

    @Test
    fun `a withdrawal code never carries a secret code`() {
        // {PIN} left empty is not a bug: the operator asks for it in its own
        // prompt, and the application must never build it into a string.
        val filled = UssdFormatter.fill(
            template = "#111*4*{NUMBER}*{AMOUNT}#",
            number = "0341234567",
            amount = "5000"
        )
        assertFalse(filled.contains("{PIN}"))
        assertEquals("#111*4*0341234567*5000#", filled)
    }

    @Test
    fun `transfer templates fill every placeholder`() {
        val filled = UssdFormatter.fill(
            template = "#321*{NUMBER}*{AMOUNT}*{PIN}#",
            number = "0341234567",
            amount = "1000",
            pin = "1234"
        )
        assertEquals("#321*0341234567*1000*1234#", filled)
    }

    // ---------------------------------------------------------------------- SIM

    private fun sim(
        subscriptionId: Int,
        slot: Int,
        carrier: String,
        number: String = "",
        override: String = ""
    ) = SimCard(
        subscriptionId = subscriptionId,
        slotIndex = slot,
        carrierName = carrier,
        displayName = carrier,
        number = number,
        operatorOverride = override
    )

    @Test
    fun `a sim is matched from the keywords of the operator`() {
        assertTrue(SimManager.matches(sim(1, 0, "Telma"), operator("Yas")))
        assertTrue(SimManager.matches(sim(1, 0, "YAS Madagascar"), operator("Yas")))
        assertTrue(SimManager.matches(sim(2, 1, "Orange MG"), operator("Orange")))
        assertFalse(SimManager.matches(sim(2, 1, "Orange MG"), operator("Airtel")))
    }

    @Test
    fun `a sim without a readable carrier is matched from its number`() {
        assertTrue(SimManager.matches(sim(3, 2, "", number = "0331234567"), operator("Airtel")))
    }

    @Test
    fun `an operator forced by the user wins over the carrier name`() {
        val forced = sim(4, 0, "Telma", override = "Airtel")
        assertTrue(SimManager.matches(forced, operator("Airtel")))
        assertFalse(SimManager.matches(forced, operator("Yas")))
    }

    @Test
    fun `three cards of the same operator are all candidates`() {
        val cards = listOf(
            sim(1, 0, "Yas"),
            sim(2, 1, "Telma"),
            sim(3, 2, "Yas")
        )
        val candidates = SimManager.simsForOperator(cards, operator("Yas"))
        assertEquals(3, candidates.size)
        assertEquals(listOf(1, 2, 3), candidates.map { it.subscriptionId })
    }

    @Test
    fun `a disabled card is left out of the candidates`() {
        val cards = listOf(sim(1, 0, "Yas").copy(enabled = false), sim(2, 1, "Yas"))
        val candidates = SimManager.simsForOperator(cards, operator("Yas"))
        assertEquals(listOf(2), candidates.map { it.subscriptionId })
    }

    // ----------------------------------------------------------------- contacts

    private val someContacts = listOf(
        Contact(1, "Rakoto", "0341234567"),
        Contact(2, "Naivo", "0321112233")
    )

    @Test
    fun `contact search matches the name the number and the keypad letters`() {
        assertEquals(1, ContactsRepository.search(someContacts, "rako").size)
        assertEquals(1, ContactsRepository.search(someContacts, "0321").size)
        // 7 2 5 6 = "rako" on a phone keypad
        assertEquals("Rakoto", ContactsRepository.search(someContacts, "7256").first().name)
        assertEquals(2, ContactsRepository.search(someContacts, "").size)
    }

    @Test
    fun `the prepared index answers exactly like the plain search`() {
        val index = ContactIndex(someContacts)
        listOf("", "rako", "RAKO", " naivo ", "0321", "7256", "zzz").forEach { query ->
            assertEquals(
                query,
                ContactsRepository.search(someContacts, query).map { it.number },
                index.search(query).map { it.number }
            )
        }
    }

    @Test
    fun `an empty contact list searches without failing`() {
        assertTrue(ContactIndex(emptyList()).search("rako").isEmpty())
        assertTrue(ContactIndex(emptyList()).search("").isEmpty())
    }

    @Test
    fun `a contact without a name is still found by its number`() {
        val index = ContactIndex(listOf(Contact(3, "", "0331234567")))
        assertEquals(1, index.search("0331").size)
        assertEquals(0, index.search("rako").size)
    }

    // ---------------------------------------------------------------- operators

    @Test
    fun `operator configuration exposes its prefixes and keywords`() {
        val yas = operator("Yas")
        assertEquals(listOf("034", "038"), yas.prefixList)
        assertTrue(yas.keywordList.contains("telma"))
        assertTrue(yas.keywordList.contains("yas"))
        assertEquals(15, operator("Airtel").codeLength)
    }

    @Test
    fun `every shipped plan has a dialable code`() {
        assertTrue(DefaultData.offers.isNotEmpty())
        DefaultData.offers.forEach { offer ->
            assertTrue(offer.title, offer.ussdCode.isNotBlank())
            assertTrue(offer.title, offer.operatorName.isNotBlank())
            assertTrue(offer.title, offer.category in DefaultData.categories)
        }
    }

    @Test
    fun `an unknown operator falls back to a neutral formatting`() {
        val unknown = Operator(name = "Test", template = "*999*{CODE}#")
        assertEquals("*999*1234#", UssdFormatter.format(UssdFormatter.fill(unknown.template, code = "1234"), unknown))
    }
}
