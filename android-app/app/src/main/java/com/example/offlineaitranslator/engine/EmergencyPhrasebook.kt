package com.example.offlineaitranslator.engine

/**
 * Built-in, fully offline catalogue of urgent English phrases grouped by
 * real-world scenario. No translation service or network call is involved.
 */
object EmergencyPhrasebook {

    data class EmergencyPhrase(
        val id: String,
        val category: String,
        val english: String
    )

    /** The six supported emergency / field scenarios, in display order. */
    val CATEGORIES: List<String> = listOf(
        "Disaster Relief & SOS",
        "Healthcare & Pharmacy",
        "Tourists & Pilgrims",
        "Defence & Field Ops",
        "Buyer-to-Buyer Trade",
        "Banking, Legal & Marine"
    )

    /** At least four ready-to-use phrases per category. */
    val PHRASES: List<EmergencyPhrase> = listOf(
        // Disaster Relief & SOS
        EmergencyPhrase("sos_01", "Disaster Relief & SOS", "Help! We need immediate rescue at this location."),
        EmergencyPhrase("sos_02", "Disaster Relief & SOS", "There has been an earthquake; please evacuate the building."),
        EmergencyPhrase("sos_03", "Disaster Relief & SOS", "Is there a shelter or relief camp nearby?"),
        EmergencyPhrase("sos_04", "Disaster Relief & SOS", "My family is missing; have you seen them?"),
        EmergencyPhrase("sos_05", "Disaster Relief & SOS", "We need clean water, food and medical supplies urgently."),

        // Healthcare & Pharmacy
        EmergencyPhrase("med_01", "Healthcare & Pharmacy", "Where is the nearest hospital or clinic?"),
        EmergencyPhrase("med_02", "Healthcare & Pharmacy", "I need a doctor; I am having severe chest pain."),
        EmergencyPhrase("med_03", "Healthcare & Pharmacy", "Please give me the medicine prescribed on this sheet."),
        EmergencyPhrase("med_04", "Healthcare & Pharmacy", "I am allergic to penicillin; which medicine is safe?"),
        EmergencyPhrase("med_05", "Healthcare & Pharmacy", "What is the dosage and how often should I take it?"),

        // Tourists & Pilgrims
        EmergencyPhrase("tour_01", "Tourists & Pilgrims", "Where is the entrance to the temple?"),
        EmergencyPhrase("tour_02", "Tourists & Pilgrims", "How far is the railway station from here?"),
        EmergencyPhrase("tour_03", "Tourists & Pilgrims", "Can you show me the way to the guest house?"),
        EmergencyPhrase("tour_04", "Tourists & Pilgrims", "Is this the correct platform for the pilgrim train?"),
        EmergencyPhrase("tour_05", "Tourists & Pilgrims", "How much does a ticket to the shrine cost?"),

        // Defence & Field Ops
        EmergencyPhrase("def_01", "Defence & Field Ops", "Identify your unit and show your authorization pass."),
        EmergencyPhrase("def_02", "Defence & Field Ops", "Report your position and status to base immediately."),
        EmergencyPhrase("def_03", "Defence & Field Ops", "Maintain radio silence until further orders."),
        EmergencyPhrase("def_04", "Defence & Field Ops", "The supply convoy is delayed; request an alternate route."),
        EmergencyPhrase("def_05", "Defence & Field Ops", "Request medical evacuation for the injured soldier."),

        // Buyer-to-Buyer Trade
        EmergencyPhrase("trade_01", "Buyer-to-Buyer Trade", "What is the best price if I buy in bulk?"),
        EmergencyPhrase("trade_02", "Buyer-to-Buyer Trade", "Can you show me a sample of the goods?"),
        EmergencyPhrase("trade_03", "Buyer-to-Buyer Trade", "Please confirm the quality and the delivery date."),
        EmergencyPhrase("trade_04", "Buyer-to-Buyer Trade", "I will pay after the goods are inspected."),
        EmergencyPhrase("trade_05", "Buyer-to-Buyer Trade", "Do you offer a discount for repeat orders?"),

        // Banking, Legal & Marine
        EmergencyPhrase("blm_01", "Banking, Legal & Marine", "I need to withdraw money from my account."),
        EmergencyPhrase("blm_02", "Banking, Legal & Marine", "Where can I sign these legal documents?"),
        EmergencyPhrase("blm_03", "Banking, Legal & Marine", "Please issue a receipt for this payment."),
        EmergencyPhrase("blm_04", "Banking, Legal & Marine", "The cargo shipment is delayed at the port."),
        EmergencyPhrase("blm_05", "Banking, Legal & Marine", "I need to speak with the bank manager about a transfer.")
    )

    /** Returns all phrases in [category] (case-insensitive match). */
    fun phrasesFor(category: String): List<EmergencyPhrase> =
        PHRASES.filter { it.category.equals(category, ignoreCase = true) }
}

/** Convenience alias so callers may also use `EmergencyPhrase` directly. */
typealias EmergencyPhrase = EmergencyPhrasebook.EmergencyPhrase
