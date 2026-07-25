package com.example.data

import com.example.data.model.MessageTemplate
import com.example.data.model.Offer
import com.example.data.model.Operator

/**
 * Factory settings.
 *
 * Everything here is only a starting point: operators, recharge templates, plan
 * codes and message templates all live in the database and can be edited, added
 * or removed by the user from the Settings and Plans screens.
 */
object DefaultData {

    const val CATEGORY_INTERNET = "Internet"
    const val CATEGORY_CALL = "Appel"
    const val CATEGORY_SMS = "SMS"

    val categories = listOf(CATEGORY_INTERNET, CATEGORY_CALL, CATEGORY_SMS)

    val operators: List<Operator> = listOf(
        Operator(
            name = "Yas",
            template = "#321*{CODE}#",
            isDefault = true,
            codeLength = 14,
            balanceTemplate = "#321#",
            transferTemplate = "#321*{NUMBER}*{AMOUNT}#",
            shareTemplate = "#322*{NUMBER}*{AMOUNT}#",
            serviceNumber = "321",
            prefixes = "034,038",
            keywords = "yas,telma,ye'low,yelow,yellow,mora,m'ora,first",
            colorHex = "#063970",
            accentHex = "#FFD700",
            stripUssdSymbols = false,
            sortOrder = 0
        ),
        Operator(
            name = "Orange",
            template = "202{CODE}",
            isDefault = true,
            codeLength = 14,
            balanceTemplate = "#123#",
            transferTemplate = "#144*{NUMBER}*{AMOUNT}#",
            shareTemplate = "224*{NUMBER}*{AMOUNT}",
            serviceNumber = "555",
            prefixes = "032,037",
            keywords = "orange,benet,akama,max it,maxit",
            colorHex = "#F57C00",
            accentHex = "#FFFFFF",
            stripUssdSymbols = true,
            sortOrder = 1
        ),
        Operator(
            name = "Airtel",
            template = "*888*{CODE}#",
            isDefault = true,
            codeLength = 15,
            balanceTemplate = "*100#",
            transferTemplate = "*100*{NUMBER}*{AMOUNT}#",
            shareTemplate = "*114*{NUMBER}*{AMOUNT}#",
            serviceNumber = "111",
            prefixes = "033",
            keywords = "airtel,malaza,mlay,m'lay,jiaby",
            colorHex = "#D32F2F",
            accentHex = "#FFFFFF",
            stripUssdSymbols = false,
            sortOrder = 2
        )
    )

    val messageTemplates: List<MessageTemplate> = listOf(
        MessageTemplate(
            title = "Recharge effectuee",
            body = "Salama {NAME}, vita ny fampidirana fahana. Misaotra!",
            sortOrder = 0
        ),
        MessageTemplate(
            title = "Rappel de paiement",
            body = "Bonjour {NAME}, petit rappel concernant votre paiement. Merci !",
            sortOrder = 1
        ),
        MessageTemplate(
            title = "Nouveau numero",
            body = "Bonjour {NAME}, voici mon nouveau numero. Merci de l'enregistrer.",
            sortOrder = 2
        )
    )

    private fun o(
        operator: String,
        category: String,
        title: String,
        price: String,
        description: String,
        validity: String,
        code: String,
        order: Int
    ) = Offer(
        operatorName = operator,
        category = category,
        title = title,
        price = price,
        description = description,
        validity = validity,
        ussdCode = code,
        sortOrder = order
    )

    /** The plans shipped with the app, used to seed the database and to restore defaults. */
    val offers: List<Offer> = listOf(
        o("Yas", "Internet", "Ye’low 500", "500 Ar", "130 Mo pour surfer sur le réseau N°1 à Madagascar", "24H", "#322*65#", 0),
        o("Yas", "Internet", "Ye’low One", "1 000 Ar", "1 Go pour surfer sur le réseau N°1 à Madagascar", "24H", "#322*64#", 1),
        o("Yas", "Internet", "Ye’low 2000", "2 000 Ar", "450 Mo pour surfer sur le réseau N°1 à Madagascar", "48H", "#322*68#", 2),
        o("Yas", "Internet", "Ye’low 2500", "2 500 Ar", "2,4 Go pour surfer sur le réseau N°1 à Madagascar", "24H", "#322*69#", 3),
        o("Yas", "Internet", "Net Week 3000", "3 000 Ar", "700 Mo pour surfer sur le réseau N°1 à Madagascar", "7 jours", "#322*70#", 4),
        o("Yas", "Internet", "Net Week 5000", "5 000 Ar", "1,2 Go pour surfer sur le réseau N°1 à Madagascar", "7 jours", "#322*71#", 5),
        o("Yas", "Internet", "Net Week 10000", "10 000 Ar", "3 Go pour surfer sur le réseau N°1 à Madagascar", "7 jours", "#322*73#", 6),
        o("Yas", "Internet", "Net Month 15000", "15 000 Ar", "2,5 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*80#", 7),
        o("Yas", "Internet", "Net Month 25000", "25 000 Ar", "4,5 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*8#", 8),
        o("Yas", "Internet", "Net Month 75000", "75 000 Ar", "15 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*13#", 9),
        o("Yas", "Internet", "Net Month 125000", "125 000 Ar", "29 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*17#", 10),
        o("Yas", "Internet", "Net Month 175000", "175 000 Ar", "58 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*19#", 11),
        o("Yas", "Internet", "Net Month 200000", "200 000 Ar", "100 Go pour surfer sur le réseau N°1 à Madagascar", "30 jours", "#322*15#", 12),
        o("Yas", "Appel", "M'Ora 500", "500 Ar", "500 Ar de crédit d'appels inclus, 25 Mo, 25 SMS. Avec renouvellement automatique", "1 jour", "#322*20#", 0),
        o("Yas", "Appel", "M'Ora One", "1 000 Ar", "1000 Ar de crédit d'appels inclus, 40 Mo, 40 SMS. Avec renouvellement automatique", "1 jour", "#322*21#", 1),
        o("Yas", "Appel", "M'Ora +2000", "2 000 Ar", "2000 Ar de crédit d'appels inclus, 100 Mo d'Internet, 50 SMS. Avec renouvellement automatique", "2 jours", "#322*32#", 2),
        o("Yas", "Appel", "M'Ora +5000", "5 000 Ar", "5000 Ar de crédit d'appels inclus, 200 Mo d'Internet, 100 SMS. Avec renouvellement automatique", "7 jours", "#322*35#", 3),
        o("Yas", "Appel", "International", "5 000 Ar", "15 minutes vers l'international*", "24H", "#322*52#", 4),
        o("Yas", "Appel", "First Premium", "10 000 Ar", "10 000 Ar de crédits d'appels inclus, 350 Mo, 100 SMS vers mon réseau. Avec renouvellement automatique", "30 jours", "#322*91#", 5),
        o("Yas", "Appel", "First Premium +", "15 000 Ar", "15 000 Ar de crédit d'appels inclus, 5 minutes vers l'international*, 500 Mo d'Internet, 150 SMS vers mon réseau. Avec renouvellement automatique", "30 jours", "#322*92#", 6),
        o("Yas", "Appel", "First Prestige", "30 000 Ar", "30 000 Ar de crédit d'appels inclus, 10 minutes vers l'international*, 15 Go d'Internet, 400 SMS vers mon réseau. Avec renouvellement automatique", "30 jours", "#322*93#", 7),
        o("Yas", "Appel", "First Royal", "50 000 Ar", "50 000 Ar de crédit d'appels inclus, 15 minutes vers l'international*, 2 Go d'Internet, 700 SMS vers mon réseau. Avec renouvellement automatique", "30 jours", "#322*95#", 8),
        o("Yas", "SMS", "Ye’low 100", "100 Ar", "20 Mo et/ou 20 SMS vers mon réseau", "1 jour", "#322*67#", 0),
        o("Yas", "SMS", "Ye’low 200", "200 Ar", "40 Mo et/ou 40 SMS vers mon réseau", "24H", "#322*63#", 1),
        o("Yas", "SMS", "Ye’low SMS", "200 Ar", "100 SMS vers mon réseau, 25 SMS vers tous les réseaux. Limité à 1 souscription/jour", "24H", "#322*62#", 2),
        o("Yas", "SMS", "Ye’low 1000", "1 000 Ar", "100 SMS et/ou 50 Mo jour + 50 Mo nuit", "30 jours", "#322*66#", 3),
        o("Orange", "Internet", "Akama Go", "100 Ar", "20 Mo. Disponible aussi sur Max it ou composez le 222*1", "24 heures", "222*1", 0),
        o("Orange", "Internet", "Akama Go+", "250 Ar", "50 Mo. Disponible aussi sur Max it ou composez le 222*11", "24 heures", "222*11", 1),
        o("Orange", "Internet", "Akama Up", "500 Ar", "130 Mo. Disponible aussi sur Max it ou composez le 222*33", "24 heures", "222*33", 2),
        o("Orange", "Internet", "Akama Plus", "1 000 Ar", "500 Mo d'Internet + 500 Mo Nuit (22h à 6h). Disponible aussi sur Max it ou composez le 222*7", "24 heures", "222*7", 3),
        o("Orange", "Internet", "Akama Al 1Go", "1 000 Ar", "1 Go + 1 nuit (22h à 6h). Disponible aussi sur Max it ou composez le 222*01", "1 jour", "222*01", 4),
        o("Orange", "Internet", "Akama Full", "2 000 Ar", "1 Go + 1 Go Nuit (22h à 6h). Disponible aussi sur Max it ou composez le 222*77", "24 heures", "222*77", 5),
        o("Orange", "Internet", "Akama Al 2Go", "2 500 Ar", "2 Go + 3 nuits (22h à 6h). Disponible aussi sur Max it ou composez le 222*03", "3 jours", "222*03", 6),
        o("Orange", "Internet", "Akama Fun", "6 000 Ar", "1.4 Go. Disponible aussi sur Max it ou composez le 222*9", "5 jours", "222*9", 7),
        o("Orange", "Internet", "Be Connect 450Mo", "2 000 Ar", "450 Mo d'Internet + 150 Mo Spotify + 125 Mo Bonus Orange Money. Disponible aussi sur Max it ou composez le #123#", "7 jours", "#123#", 8),
        o("Orange", "Internet", "Be Connect 800Mo", "3 500 Ar", "800 Mo d'Internet + 150 Mo Spotify + 200 Mo Bonus Orange Money. Disponible aussi sur Max it ou composez le #123#", "7 jours", "#123#", 9),
        o("Orange", "Internet", "Be Connect 1,2Go", "5 000 Ar", "1.2 Go d'Internet + 150 Mo Spotify + 300 Mo Bonus Orange Money. Disponible aussi sur Max it ou composez le #123#", "7 jours", "#123#", 10),
        o("Orange", "Appel", "Be 500", "500 Ar", "SMS vers Orange : 25, Internet : 25 Mo, Bonus Orange Money : 100 Ar", "24 heures", "224*1*1", 0),
        o("Orange", "Appel", "Be 1 000", "1 000 Ar", "SMS vers Orange : 40, Internet : 40 Mo, Bonus Orange Money : 200 Ar", "24 heures", "224*1*2", 1),
        o("Orange", "Appel", "Be 2 000", "2 000 Ar", "SMS vers Orange : 50, Internet : 100 Mo, Bonus Orange Money : 400 Ar", "3 jours", "224*7*1", 2),
        o("Orange", "Appel", "Be 5 000", "5 000 Ar", "SMS vers Orange : 100, Internet : 200 Mo, Bonus Orange Money : 1 000 Ar", "7 jours", "224*7*2", 3),
        o("Orange", "Appel", "Be 10 000", "10 000 Ar", "SMS vers Orange : 100, Internet : 300 Mo, Appel International : 5 min, Bonus Orange Money : 2 000 Ar", "30 jours", "224*30*1", 4),
        o("Orange", "Appel", "Be 20 000", "20 000 Ar", "SMS vers Orange : 200, Internet : 500 Mo, Appel International : 10 min, Bonus Orange Money : 4 000 Ar", "30 jours", "224*30*2", 5),
        o("Orange", "Appel", "Be 30 000", "30 000 Ar", "SMS vers Orange : 400, Internet : 1 Go, Appel International : 10 min, Bonus Orange Money : 6 000 Ar", "30 jours", "224*30*3", 6),
        o("Orange", "Appel", "Be 50 000", "50 000 Ar", "SMS vers Orange : 600, Internet : 2 Go, Appel International : 15 min, Bonus Orange Money : 10 000 Ar", "30 jours", "224*30*4", 7),
        o("Orange", "Appel", "Be 100 000", "100 000 Ar", "SMS vers Orange : 800, Internet : 4 Go, Appel International : 30 min, Bonus Orange Money : 2 000 Ar", "30 jours", "224*30*5", 8),
        o("Orange", "Appel", "Be 150 000", "150 000 Ar", "SMS vers Orange : 900, Internet : 6 Go, Appel International : 30 min, Bonus Orange Money : 30 000 Ar", "30 jours", "224*30*6", 9),
        o("Orange", "SMS", "Be SMS 1", "250 Ar", "75 SMS vers Orange, 25 SMS vers autres opérateurs. Les SMS vers Orange sont partageables à des numéros Orange. Disponible aussi sur Max it ou composez le 224*1*0", "24 heures", "224*1*0", 0),
        o("Orange", "SMS", "Be SMS 3", "450 Ar", "150 SMS vers Orange, 50 SMS vers autres opérateurs. Les SMS vers Orange sont partageables à des numéros Orange. Disponible aussi sur Max it ou composez le 224*3*0", "3 jours", "224*3*0", 1),
        o("Orange", "SMS", "Be SMS 7", "450 Ar", "450 SMS vers Orange. Les SMS vers Orange sont partageables à des numéros Orange. Disponible aussi sur Max it ou composez le 224*7*0", "7 jours", "224*7*0", 2),
        o("Orange", "SMS", "Be SMS 30", "1 000 Ar", "100 SMS vers tout opérateur. Les SMS vers Orange sont partageables à des numéros Orange. Disponible aussi sur Max it ou composez le 224*30*0", "30 jours", "224*30*0", 3),
        o("Orange", "SMS", "SMS Free 200", "200 Ar TTC", "100 SMS tout opérateur. Disponible aussi sur Max it ou composez le 202*3*1", "24 h", "202*3*1", 4),
        o("Orange", "SMS", "SMS Free 500", "500 Ar TTC", "300 SMS tout opérateur. Disponible aussi sur Max it ou composez le 202*3*2", "3 j", "202*3*2", 5),
        o("Airtel", "Internet", "Net Mlay Boost-500", "500 Ar", "500 Mo d'Internet", "1 heure", "*114*500*1#", 0),
        o("Airtel", "Internet", "Net Mlay-100", "100 Ar", "20 Mo d'Internet", "24 heures", "*114*0100#", 1),
        o("Airtel", "Internet", "Net Mlay-200", "200 Ar", "50 Mo d'Internet", "24 heures", "*114*0200#", 2),
        o("Airtel", "Internet", "Net Mlay-500", "500 Ar", "130 Mo d'Internet", "2 jours", "*114*500*2#", 3),
        o("Airtel", "Internet", "Net Mlay-1000", "1 000 Ar", "500 Mo Jour + 500 Mo Nuit d'Internet", "2 jours", "*114*01000#", 4),
        o("Airtel", "Internet", "Net Mlay-2000", "2 000 Ar", "1 Go Jour + 1 Go Nuit d'Internet", "2 jours", "*114*02000#", 5),
        o("Airtel", "Internet", "Net Mlay-3000", "3 000 Ar", "1,4 Go d'Internet", "7 jours", "*114*03000#", 6),
        o("Airtel", "Internet", "Net Mlay-5000", "5 000 Ar", "2,4 Go d'Internet", "7 jours", "*114*05000#", 7),
        o("Airtel", "Internet", "Net Mlay-6000", "6 000 Ar", "2,9 Go d'Internet", "7 jours", "*114*06000#", 8),
        o("Airtel", "Internet", "Net Mlay-10000", "10 000 Ar", "4,8 Go d'Internet", "7 jours", "*114*010000#", 9),
        o("Airtel", "Internet", "Net Mlay-12500", "12 500 Ar", "6 Go d'Internet", "7 jours", "*114*012500#", 10),
        o("Airtel", "Internet", "Net Mlay-15000", "15 000 Ar", "6 Go d'Internet", "30 jours", "*114*015000#", 11),
        o("Airtel", "Internet", "Net Mlay-25000", "25 000 Ar", "10 Go d'Internet", "30 jours", "*114*025000#", 12),
        o("Airtel", "Internet", "Net Mlay-30000", "30 000 Ar", "13 Go d'Internet", "30 jours", "*114*030000#", 13),
        o("Airtel", "Internet", "Net Mlay-49000", "49 000 Ar", "23,5 Go d'Internet", "30 jours", "*114*049000#", 14),
        o("Airtel", "Internet", "Net Mlay-80000", "80 000 Ar", "39 Go d'Internet", "30 jours", "*114*080000#", 15),
        o("Airtel", "Internet", "Net Mlay-125000", "125 000 Ar", "66 Go d'Internet", "30 jours", "*114*0125000#", 16),
        o("Airtel", "Internet", "Net Mlay-199000", "199 000 Ar", "210 Go d'Internet", "30 jours", "*114*0199000#", 17),
        o("Airtel", "Appel", "M'Lay Jiaby 500", "500 Ar", "5 min d'appels tout opérateur + 10 SMS tout opérateur + 25 Mo d'Internet", "24 heures", "*100*00500#", 0),
        o("Airtel", "Appel", "M'Lay Jiaby 1 000", "1 000 Ar", "10 min d'appels tout opérateur + 20 SMS tout opérateur + 50 Mo d'Internet", "24 heures", "*100*001000#", 1),
        o("Airtel", "Appel", "M'Lay Jiaby 2 000", "2 000 Ar", "20 min d'appels tout opérateur + 50 SMS tout opérateur + 100 Mo d'Internet", "2 jours", "*100*002000#", 2),
        o("Airtel", "Appel", "M'Lay Jiaby 10 000", "10 000 Ar", "100 min d'appels tout opérateur + 200 SMS tout opérateur + 500 Mo d'Internet", "30 jours", "*100*0010000#", 3),
        o("Airtel", "Appel", "M'Lay 500", "500 Ar", "500 Ar de crédit d'appels tout opérateur + 25 SMS Airtel + 25 Mo d'Internet", "24 heures", "*100*500#", 4),
        o("Airtel", "Appel", "M'Lay 1 000", "1 000 Ar", "1 000 Ar de crédit d'appels tout opérateur + 75 SMS Airtel + 75 Mo d'Internet", "24 heures", "*100*1000#", 5),
        o("Airtel", "Appel", "M'Lay 3 500", "3 500 Ar", "3 500 Ar de crédit d'appels tout opérateur + 100 SMS Airtel + 100 Mo d'Internet", "7 jours", "*100*3500#", 6),
        o("Airtel", "Appel", "M'Lay 5 000", "5 000 Ar", "5 000 Ar de crédit d'appels tout opérateur + 300 SMS Airtel + 300 Mo d'Internet", "7 jours", "*100*5000#", 7),
        o("Airtel", "Appel", "M'Lay 10 000", "10 000 Ar", "10 000 Ar de crédit d'appels tout opérateur + 300 SMS Airtel + 350 Mo d'Internet + 5 min à l'international", "30 jours", "*100*10000#", 8),
        o("Airtel", "Appel", "M'Lay 15 000", "15 000 Ar", "15 000 Ar de crédit d'appels tout opérateur + 500 SMS Airtel + 500 Mo d'Internet + 5 min à l'international", "30 jours", "*100*15000#", 9),
        o("Airtel", "Appel", "M'Lay 20 000", "20 000 Ar", "20 000 Ar de crédit d'appels tout opérateur + 750 SMS Airtel + 750 Mo d'Internet + 10 min à l'international", "30 jours", "*100*20000#", 10),
        o("Airtel", "Appel", "M'Lay 30 000", "30 000 Ar", "30 000 Ar de crédit d'appels tout opérateur + 1 000 SMS Airtel + 1 024 Mo d'Internet + 10 min à l'international", "30 jours", "*100*30000#", 11),
        o("Airtel", "Appel", "M'Lay 50 000", "50 000 Ar", "50 000 Ar de crédit d'appels tout opérateur + 2 000 SMS Airtel + 2 048 Mo d'Internet + 15 min à l'international", "30 jours", "*100*50000#", 12),
        o("Airtel", "SMS", "SMS M'Lay 100", "100 Ar", "20 SMS vers Airtel", "24 heures", "*100*111#", 0),
        o("Airtel", "SMS", "SMS M'Lay 200", "200 Ar", "200 SMS vers Airtel + 10 SMS vers tous les opérateurs", "24 heures", "*100*222#", 1),
        o("Airtel", "SMS", "SMS M'Lay 500", "500 Ar", "1 400 SMS vers Airtel + 70 SMS vers tous les opérateurs", "7 jours", "*100*555#", 2),
        o("Airtel", "SMS", "SMS M'Lay 1 000", "1 000 Ar", "100 SMS vers tous les opérateurs", "30 jours", "*100*1111#", 3),
    )
}
