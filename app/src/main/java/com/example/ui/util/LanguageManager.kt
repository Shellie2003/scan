package com.example.ui.util

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object LanguageManager {
    private val _currentLang = mutableStateOf("mg") // Default is Malagasy ("mg")
    val currentLang: State<String> = _currentLang

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        _currentLang.value = prefs.getString("selected_language", "mg") ?: "mg"
    }

    fun setLanguage(context: Context, lang: String) {
        if (lang in listOf("mg", "fr", "en")) {
            _currentLang.value = lang
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("selected_language", lang).apply()
        }
    }

    // Translation Map
    private val translations = mapOf(
        "mg" to mapOf(
            "app_title" to "SCAN",
            "nav_scan" to "Scan",
            "nav_offers" to "Tolotra",
            "nav_operators" to "Mpanome",
            "camera_required" to "Mila fakantsary",
            "camera_required_desc" to "Mila alalana hampiasa ny fakantsary ny fampiharana mba hamakiana ny kaody.",
            "allow_access" to "Hanome alalana",
            "suspicious_code" to "Kaody mampiahiahy (miverimberina loatra ny isa). Hamarino tsara.",
            "duplicate_code" to "Efa nanandrana nampiditra ity kaody ity ianao vao haingana.",
            "no_sim" to "Tsy misy karatra SIM {operator} hita ao amin'ity finday ity.",
            "auto_validation" to "Fampidirana avy hatrany",
            "auto_validation_desc" to "Raha alefa dia handefa ny antso USSD avy hatrany rehefa mahita kaody 14 isa.",
            "flash" to "Jiro",
            "enter_manually" to "Hampiditra amin'ny tanana",
            "enter_code_here" to "Ampidiro eto ny kaody (isa 14)",
            "copy" to "Adika",
            "recharge" to "Hampiditra",
            "code_detected" to "Kaody hita",
            "validate_code" to "Hanamarina",
            "details" to "Andinindininy",
            "my_operators" to "Ireo Mpanome tolotra",
            "operators_desc" to "Amboary ny firafitry ny fampidirana fahana USSD an'ny SIM-nao.",
            "operators_configured" to "Mpanome tolotra {count} voalamina",
            "no_operators_configured" to "Tsy misy mpanome tolotra voalamina",
            "add_operator_desc" to "Manampia mpanome tolotra iray mba hanombohana mikarakara sy mampiditra fahana.",
            "add_first_operator" to "Hanampy mpanome tolotra voalohany",
            "recharge_template" to "Modely fampidirana",
            "new_operator" to "Mpanome tolotra vaovao",
            "edit_operator" to "Hanova mpanome tolotra",
            "operator_dialog_desc" to "Soraty ny anarana sy ny kaody USSD ampiasaina hampidirana fahana (ampiasao {CODE} ho solon'ny toerana hisian'ny kaody).",
            "operator_name" to "Anaran'ny mpanome tolotra",
            "ussd_template" to "Modely USSD",
            "preview_generated" to "Aperçu ny kaody vokarina :",
            "add" to "Hanampy",
            "save" to "Tehirizina",
            "cancel" to "Hanafoana",
            "confirm_delete" to "Hanamarina ny famafana",
            "confirm_delete_desc" to "Tena te hamafa an'i {operator} tokoa ve ianao? Ho very daholo ny firafitra USSD nataonao.",
            "delete" to "Hafama",
            "language" to "Fiteny",
            "lang_mg" to "Malagasy",
            "lang_fr" to "Français",
            "lang_en" to "English",
            "reading_frame" to "CADRE DE LECTURE",
            "detected_recharge_code" to "KAODY RECHARGE HITA",
            "manual_entry_placeholder" to "Soraty eto ny kaody",
            "recharge_btn_text" to "HAMPIDITRA {operator}",
            "recharge_btn_default" to "HAMPIDITRA",
            "plan_internet" to "Internet",
            "plan_appel" to "Antso",
            "plan_sms" to "SMS",
            "step_1_choose_operator" to "Dingana 1 : Safidio ny mpanome tolotra",
            "step_2_frame_digits" to "Dingana 2 : Ampifanaraho ny isa 14",
            "auto_validation_short_desc" to "Mampiditra ny kaody USSD avy hatrany rehefa hita",
            "type_code_manually" to "Tapez le code manuellement",
            "cooldown_active" to "Fampiatoana kely: {seconds}s... Andraso azafady",
            "operator_auto_detected" to "Mpanome tolotra hita: {operator}",
            "scan_success_msg" to "Kaody hita soa aman-tsara!",
            "scan_success_sub" to "Hita ny isa 14",
            "nav_about" to "A propos",
            "about_title" to "Mombamomba",
            "about_desc" to "Ity application ity dia namboarina mba hanamoràna ny fampidirana fahana amin'ireo tambazotra misy eto Madagasikara.\nMaimaimpoana ny fampiasana azy, ary tsy haka vola na Ariary amin'ireo mpampiasa ity application ity mihitsy izahay.\nTsy misy tolotra ka mangataka ny Kaody Miafinanao ato, mba hiarovana ny filaminan'ny daloholobe, ary tsy misy tolotra ambadika na (API) mampifandray ny developpeur sy ny mpampiasa ato ka 100 % mahaleo tena ny fampiasàna azy",
            "about_dev" to "Developer : AinaDigit",
            "about_compat" to "Android compatible : Android 5.0 (Lollipop) na ambony",
            "about_contact" to "Contact d'AinaDigit : 0347720585",
            "about_email" to "Email : shellinopierre@gmail.com"
        ),
        "fr" to mapOf(
            "app_title" to "SCAN",
            "nav_scan" to "Scan",
            "nav_offers" to "Offres",
            "nav_operators" to "Opérateurs",
            "camera_required" to "Caméra requise",
            "camera_required_desc" to "L'accès à la caméra est nécessaire pour scanner automatiquement les tickets de recharge.",
            "allow_access" to "Autoriser l'accès",
            "suspicious_code" to "Code suspect détecté (chiffres très répétitifs). Vérifiez le scan pour éviter le blocage.",
            "duplicate_code" to "Ce code a déjà été tenté récemment. Validation bloquée par sécurité.",
            "no_sim" to "Pas de carte SIM {operator} détectée dans cet appareil.",
            "auto_validation" to "Validation automatique",
            "auto_validation_desc" to "Si activé, l'appel USSD sera lancé automatiquement dès qu'un code à 14 chiffres est détecté.",
            "flash" to "Flash",
            "enter_manually" to "Saisir manuellement",
            "enter_code_here" to "Saisir le code ici (14 chiffres)",
            "copy" to "Copier",
            "recharge" to "Recharger",
            "code_detected" to "Code détecté",
            "validate_code" to "Valider le code",
            "details" to "Détails",
            "my_operators" to "Mes Opérateurs",
            "operators_desc" to "Configurez les formats de recharge USSD de vos puces SIM pour envoyer automatiquement le code détecté.",
            "operators_configured" to "{count} opérateur(s) configuré(s)",
            "no_operators_configured" to "Aucun opérateur configuré",
            "add_operator_desc" to "Ajoutez un opérateur pour commencer à scanner et recharger vos tickets.",
            "add_first_operator" to "Ajouter le premier opérateur",
            "recharge_template" to "Template de Recharge",
            "new_operator" to "Nouvel Opérateur",
            "edit_operator" to "Modifier Opérateur",
            "operator_dialog_desc" to "Renseignez le nom et le code USSD utilisé pour recharger vos crédits (utilisez {CODE} pour marquer l'emplacement du code de recharge).",
            "operator_name" to "Nom de l'opérateur",
            "ussd_template" to "Format USSD Template",
            "preview_generated" to "Aperçu du code généré :",
            "add" to "Ajouter",
            "save" to "Enregistrer",
            "cancel" to "Annuler",
            "confirm_delete" to "Confirmer la suppression",
            "confirm_delete_desc" to "Voulez-vous vraiment supprimer l'opérateur {operator} ? Ses configurations USSD de recharge seront perdues.",
            "delete" to "Supprimer",
            "language" to "Langue",
            "lang_mg" to "Malagasy",
            "lang_fr" to "Français",
            "lang_en" to "English",
            "reading_frame" to "CADRE DE LECTURE",
            "detected_recharge_code" to "CODE DE RECHARGE DÉTECTÉ",
            "manual_entry_placeholder" to "Saisir le code manuellement",
            "recharge_btn_text" to "RECHARGER {operator}",
            "recharge_btn_default" to "RECHARGER",
            "plan_internet" to "Internet",
            "plan_appel" to "Appel",
            "plan_sms" to "SMS",
            "step_1_choose_operator" to "Étape 1 : Choisissez l'opérateur",
            "step_2_frame_digits" to "Étape 2 : Cadrez les 14 chiffres",
            "auto_validation_short_desc" to "Exécute le code USSD dès qu'il est détecté",
            "type_code_manually" to "Tapez le code manuellement",
            "cooldown_active" to "Pause de sécurité : {seconds}s... Veuillez patienter",
            "operator_auto_detected" to "Opérateur détecté : {operator}",
            "scan_success_msg" to "Code détecté avec succès !",
            "scan_success_sub" to "14 chiffres reconnus",
            "nav_about" to "À propos",
            "about_title" to "À propos",
            "about_desc" to "Cette application a été conçue pour faciliter la recharge de crédit sur les réseaux de Madagascar.\nElle est gratuite et nous ne prélèverons jamais d'argent aux utilisateurs.\nAucun service ne vous demandera votre code secret afin de garantir la sécurité, et aucun service d'arrière-plan ou API ne relie le développeur et l'utilisateur, ce qui rend son utilisation 100% autonome.",
            "about_dev" to "Développeur : AinaDigit",
            "about_compat" to "Android compatible : Android 5.0 (Lollipop) ou version supérieure",
            "about_contact" to "Contact d'AinaDigit : 0347720585",
            "about_email" to "E-mail : shellinopierre@gmail.com"
        ),
        "en" to mapOf(
            "app_title" to "SCAN",
            "nav_scan" to "Scan",
            "nav_offers" to "Offers",
            "nav_operators" to "Operators",
            "camera_required" to "Camera Required",
            "camera_required_desc" to "Camera access is necessary to automatically scan recharge tickets.",
            "allow_access" to "Allow Access",
            "suspicious_code" to "Suspicious code detected (highly repetitive numbers). Verify the scan.",
            "duplicate_code" to "This code has already been tried recently. Blocked for safety.",
            "no_sim" to "No SIM card {operator} detected in this device.",
            "auto_validation" to "Auto-validation",
            "auto_validation_desc" to "If enabled, USSD call will launch automatically once a 14-digit code is detected.",
            "flash" to "Flash",
            "enter_manually" to "Enter manually",
            "enter_code_here" to "Enter code here (14 digits)",
            "copy" to "Copy",
            "recharge" to "Recharge",
            "code_detected" to "Code detected",
            "validate_code" to "Validate Code",
            "details" to "Details",
            "my_operators" to "My Operators",
            "operators_desc" to "Configure the USSD recharge formats for your SIM cards to automatically send the detected code.",
            "operators_configured" to "{count} operator(s) configured",
            "no_operators_configured" to "No operator configured",
            "add_operator_desc" to "Add an operator to start scanning and recharging your tickets.",
            "add_first_operator" to "Add the first operator",
            "recharge_template" to "Recharge Template",
            "new_operator" to "New Operator",
            "edit_operator" to "Edit Operator",
            "operator_dialog_desc" to "Enter the name and USSD code used to recharge credits (use {CODE} for the recharge code placeholder).",
            "operator_name" to "Operator Name",
            "ussd_template" to "USSD Template Format",
            "preview_generated" to "Generated code preview:",
            "add" to "Add",
            "save" to "Save",
            "cancel" to "Cancel",
            "confirm_delete" to "Confirm Deletion",
            "confirm_delete_desc" to "Do you really want to delete operator {operator}? Recharge USSD configurations will be lost.",
            "delete" to "Delete",
            "language" to "Language",
            "lang_mg" to "Malagasy",
            "lang_fr" to "Français",
            "lang_en" to "English",
            "reading_frame" to "READING FRAME",
            "detected_recharge_code" to "DETECTED RECHARGE CODE",
            "manual_entry_placeholder" to "Saisir le code manuellement",
            "recharge_btn_text" to "RECHARGE {operator}",
            "recharge_btn_default" to "RECHARGE",
            "plan_internet" to "Internet",
            "plan_appel" to "Call",
            "plan_sms" to "SMS",
            "step_1_choose_operator" to "Step 1: Choose the operator",
            "step_2_frame_digits" to "Step 2: Frame the 14 digits",
            "auto_validation_short_desc" to "Runs the USSD code once it is detected",
            "type_code_manually" to "Type code manually",
            "cooldown_active" to "Security pause: {seconds}s... Please wait",
            "operator_auto_detected" to "Operator detected: {operator}",
            "scan_success_msg" to "Code detected successfully!",
            "scan_success_sub" to "14 digits recognized",
            "nav_about" to "About",
            "about_title" to "About",
            "about_desc" to "This application was designed to make it easy to top up credit across networks in Madagascar.\nIt is free, and we will never charge any money from the users.\nNo feature will ever request your secret code to ensure safety, and there are no background services or APIs connecting the developer and the user, making its use 100% independent.",
            "about_dev" to "Developer: AinaDigit",
            "about_compat" to "Android compatible: Android 5.0 (Lollipop) or higher",
            "about_contact" to "AinaDigit Contact: 0347720585",
            "about_email" to "E-mail: shellinopierre@gmail.com"
        )
    )

    fun getString(key: String, vararg args: Pair<String, Any>): String {
        val lang = _currentLang.value
        var result = translations[lang]?.get(key) ?: translations["mg"]?.get(key) ?: key
        args.forEach { arg ->
            result = result.replace("{${arg.first}}", arg.second.toString())
        }
        return result
    }

    @Composable
    fun LanguageSelector(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val current = currentLang.value
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("mg" to "MG", "fr" to "FR", "en" to "EN").forEach { (code, label) ->
                val isSelected = current == code
                val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                
                Surface(
                    onClick = { setLanguage(context, code) },
                    shape = RoundedCornerShape(12.dp),
                    color = containerColor,
                    contentColor = contentColor,
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.size(height = 32.dp, width = 48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
