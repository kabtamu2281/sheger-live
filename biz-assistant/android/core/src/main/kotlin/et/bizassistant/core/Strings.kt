package et.bizassistant.core

/**
 * All user-facing text, in English, Amharic and Afaan Oromo.
 * Keeping strings here (rather than only in Android resources) lets the language be switched
 * instantly inside the app and lets JVM tests verify that every string exists in every language.
 *
 * Amharic and Afaan Oromo wording should be reviewed by native speakers before release.
 */
enum class Str(val en: String, val am: String, val om: String) {
    APP_NAME("Business Assistant", "የንግድ ረዳት", "Gargaaraa Daldalaa"),

    // Onboarding
    CHOOSE_LANGUAGE("Choose your language", "ቋንቋ ይምረጡ", "Afaan filadhu"),
    CONTINUE("Continue", "ቀጥል", "Itti fufi"),
    BACK("Back", "ተመለስ", "Deebi'i"),
    BUSINESS_PROFILE("Your business", "የእርስዎ ንግድ", "Daldala kee"),
    BUSINESS_NAME("Business name", "የንግድ ስም", "Maqaa daldalaa"),
    OWNER_NAME("Owner name", "የባለቤት ስም", "Maqaa abbaa qabeenyaa"),
    PHONE("Phone", "ስልክ", "Bilbila"),
    ADDRESS("Address", "አድራሻ", "Teessoo"),
    TIN_OPTIONAL("TIN (optional)", "የግብር ከፋይ መለያ ቁጥር (አማራጭ)", "TIN (dirqama miti)"),
    REQUIRED_FIELD("Required", "ያስፈልጋል", "Barbaachisaa"),

    // AI consent and privacy
    AI_CONSENT_TITLE("Using the AI service", "የAI አገልግሎት አጠቃቀም", "Tajaajila AI fayyadamuu"),
    AI_CONSENT_BODY(
        "Some tasks (customer messages, product descriptions, translations, letters and report wording) can be drafted by an external AI service. " +
            "When you use one of these tasks, only the text you type into that task, your business name and your chosen language are sent through our secure server to the AI provider. " +
            "For report wording, only the totals the app calculated are sent – never your full record list. " +
            "Invoices, quotations, totals and summaries are always calculated on your phone and are never sent. " +
            "We do not keep the content on our server after the reply is returned. You can turn this off at any time in Settings; manual templates keep working offline.",
        "አንዳንድ ስራዎች (የደንበኛ መልዕክቶች፣ የምርት መግለጫዎች፣ ትርጉሞች፣ ደብዳቤዎች እና የሪፖርት አጻጻፍ) በውጭ AI አገልግሎት ሊረቀቁ ይችላሉ። " +
            "ከእነዚህ ስራዎች አንዱን ሲጠቀሙ፣ በዚያ ስራ ውስጥ የጻፉት ጽሑፍ፣ የንግድዎ ስም እና የመረጡት ቋንቋ ብቻ በደህንነቱ በተጠበቀ አገልጋያችን በኩል ወደ AI አቅራቢው ይላካሉ። " +
            "ለሪፖርት አጻጻፍ፣ መተግበሪያው ያሰላቸው ድምሮች ብቻ ይላካሉ – ሙሉ የመዝገብ ዝርዝርዎ በጭራሽ አይላክም። " +
            "ኢንቮይሶች፣ የዋጋ ማቅረቢያዎች፣ ድምሮች እና ማጠቃለያዎች ሁልጊዜ በስልክዎ ላይ ይሰላሉ እና አይላኩም። " +
            "ምላሹ ከተመለሰ በኋላ ይዘቱን በአገልጋያችን ላይ አናስቀምጥም። ይህንን በማንኛውም ጊዜ በቅንብሮች ውስጥ ማጥፋት ይችላሉ፤ በእጅ የሚሞሉ ቅጾች ያለ ኢንተርኔት መስራታቸውን ይቀጥላሉ።",
        "Hojiiwwan tokko tokko (ergaa maamilaa, ibsa oomishaa, hiikkaa, xalayaa fi barreeffama gabaasaa) tajaajila AI alaatiin qophaa'uu danda'u. " +
            "Hojiiwwan kana keessaa tokko yeroo fayyadamtu, barreeffama hojii sana keessatti barreessite, maqaa daldala keetii fi afaan filatte qofatu tajaajilaa keenya nageenyi isaa eegamee keessaan gara dhiyeessaa AI ni ergama. " +
            "Barreeffama gabaasaatiif, waliigala appiin herrege qofatu ergama – tarreen galmee kee guutuun gonkumaa hin ergamu. " +
            "Inivooysii, tilmaama gatii, waliigalaa fi cuunfaan yeroo hunda bilbila kee irratti herregamu, hin ergaman. " +
            "Erga deebiin deebi'ee booda qabiyyee sana tajaajilaa keenya irratti hin kuufnu. Yeroo kamiyyuu Qindaa'ina keessatti dhaamsuu dandeessa; unkaaleen harkaan guutaman interneetii malee hojjechuu itti fufu.",
    ),
    AI_ALLOW("Allow AI drafting", "AI እንዲያረቅ ፍቀድ", "AI akka qopheessu hayyami"),
    AI_NOT_NOW("Not now – use templates only", "አሁን አይሆንም – ቅጾችን ብቻ ተጠቀም", "Amma miti – unkaalee qofa fayyadami"),
    AI_ENABLED("AI drafting is on", "AI ማርቀቅ በርቷል", "Qopheessuun AI banaadha"),
    AI_DISABLED("AI drafting is off – templates are used", "AI ማርቀቅ ጠፍቷል – ቅጾች ጥቅም ላይ ይውላሉ", "Qopheessuun AI cufaadha – unkaaleen fayyadu"),
    WHAT_IS_SENT("What is sent to the AI service?", "ወደ AI አገልግሎት ምን ይላካል?", "Maaltu gara tajaajila AI ergama?"),
    CONFIRM_SEND(
        "This will send the text above to the AI service. Continue?",
        "ይህ ከላይ ያለውን ጽሑፍ ወደ AI አገልግሎት ይልካል። ይቀጥሉ?",
        "Kun barreeffama armaan olii gara tajaajila AI erga. Itti fufta?",
    ),
    SEND("Send", "ላክ", "Ergi"),
    CANCEL("Cancel", "ሰርዝ", "Haqi"),

    // Home
    HOME("Home", "መነሻ", "Mana"),
    WHAT_TO_DO("What would you like to do?", "ምን ማድረግ ይፈልጋሉ?", "Maal gochuu barbaadda?"),
    RECORD_SALE("Record a sale", "ሽያጭ መዝግብ", "Gurgurtaa galmeessi"),
    RECORD_EXPENSE("Record an expense", "ወጪ መዝግብ", "Baasii galmeessi"),
    USAGE_TITLE("AI allowance", "የAI አጠቃቀም መጠን", "Hanga fayyadama AI"),
    USAGE_LINE("%1\$d of %2\$d AI drafts used this month", "በዚህ ወር ከ%2\$d የAI ረቂቆች %1\$d ተጠቅመዋል", "Ji'a kana qophii AI %2\$d keessaa %1\$d fayyadamte"),
    USAGE_RESETS("Resets on %1\$s", "በ%1\$s እንደገና ይጀምራል", "Guyyaa %1\$s haaromfama"),
    USAGE_UNKNOWN("Allowance not loaded – connect to the internet", "መጠኑ አልተጫነም – ከኢንተርኔት ጋር ይገናኙ", "Hangi hin feewamne – interneetiin walqunnami"),
    PLAN_LABEL("Plan: %1\$s", "እቅድ፦ %1\$s", "Karoora: %1\$s"),
    OFFLINE_MODE("Offline – templates and record keeping still work", "ከመስመር ውጭ – ቅጾች እና መዝገብ መያዝ ይሰራሉ", "Toora ala – unkaalee fi galmeessuun ni hojjetu"),

    // Tasks
    TASK_INVOICE("Create invoice", "ኢንቮይስ ፍጠር", "Inivooysii uumi"),
    TASK_QUOTATION("Create quotation", "የዋጋ ማቅረቢያ ፍጠር", "Tilmaama gatii uumi"),
    TASK_DAILY_SUMMARY("Summarize today's sales", "የዛሬን ሽያጭ አጠቃልል", "Gurgurtaa har'aa cuunfi"),
    TASK_MONTHLY_SUMMARY("Monthly summary", "የወር ማጠቃለያ", "Cuunfaa ji'aa"),
    TASK_CUSTOMER_MESSAGE("Draft a customer message", "የደንበኛ መልዕክት አርቅቅ", "Ergaa maamilaa qopheessi"),
    TASK_PRODUCT_DESCRIPTION("Write a product description", "የምርት መግለጫ ጻፍ", "Ibsa oomishaa barreessi"),
    TASK_TRANSLATION("Translate this text", "ይህን ጽሑፍ ተርጉም", "Barreeffama kana hiiki"),
    TASK_BUSINESS_LETTER("Write a business letter", "የንግድ ደብዳቤ ጻፍ", "Xalayaa daldalaa barreessi"),
    TASK_REPORT("Guided business report", "የተመራ የንግድ ሪፖርት", "Gabaasa daldalaa qajeelfamaan"),

    // Task fields
    FIELD_CUSTOMER_NAME("Customer name", "የደንበኛ ስም", "Maqaa maamilaa"),
    FIELD_CUSTOMER_PHONE("Customer phone (optional)", "የደንበኛ ስልክ (አማራጭ)", "Bilbila maamilaa (dirqama miti)"),
    FIELD_PURPOSE("Purpose of the message", "የመልዕክቱ ዓላማ", "Kaayyoo ergaa"),
    FIELD_DETAILS("Details to include", "የሚካተቱ ዝርዝሮች", "Bal'ina hammatamuu qabu"),
    FIELD_TONE("Tone", "የአጻጻፍ ስልት", "Akkaataa barreeffamaa"),
    FIELD_PRODUCT_NAME("Product name", "የምርት ስም", "Maqaa oomishaa"),
    FIELD_FEATURES("Features and facts", "ባህሪያት እና እውነታዎች", "Amaloota fi dhugaawwan"),
    FIELD_PRICE_OPTIONAL("Price (optional)", "ዋጋ (አማራጭ)", "Gatii (dirqama miti)"),
    FIELD_AUDIENCE("Who is it for? (optional)", "ለማን ነው? (አማራጭ)", "Eenyuuf? (dirqama miti)"),
    FIELD_SOURCE_TEXT("Text to translate", "የሚተረጎም ጽሑፍ", "Barreeffama hiikamu"),
    FIELD_TARGET_LANGUAGE("Translate into", "ወደ ተርጉም", "Gara hiiki"),
    FIELD_RECIPIENT("Recipient", "ተቀባይ", "Fudhataa"),
    FIELD_LETTER_TYPE("Type of letter", "የደብዳቤ ዓይነት", "Gosa xalayaa"),
    FIELD_SUBJECT("Subject", "ጉዳይ", "Dhimma"),
    FIELD_REPORT_PERIOD("Period", "ጊዜ", "Yeroo"),
    FIELD_REPORT_AUDIENCE("Who will read it? (optional)", "ማን ያነበዋል? (አማራጭ)", "Eenyutu dubbisa? (dirqama miti)"),
    FIELD_REPORT_NOTES("Your own notes (optional)", "የራስዎ ማስታወሻ (አማራጭ)", "Yaadannoo kee (dirqama miti)"),
    HINT_DETAILS_FACTS(
        "Only facts you provide will be used. Amounts and dates are never made up.",
        "እርስዎ ያቀረቡት እውነታ ብቻ ጥቅም ላይ ይውላል። መጠኖች እና ቀኖች በጭራሽ አይፈጠሩም።",
        "Dhugaawwan ati kennite qofatu fayyada. Hangaa fi guyyaan gonkumaa hin uumaman.",
    ),
    MISSING_INFO("Please add the missing information:", "እባክዎ የጎደለውን መረጃ ያክሉ፦", "Maaloo odeeffannoo hir'ate dabali:"),
    GENERATE("Create draft", "ረቂቅ ፍጠር", "Qophii uumi"),
    GENERATING("Working…", "በመስራት ላይ…", "Hojjechaa jira…"),
    VOICE_INPUT("Speak", "ይናገሩ", "Dubbadhu"),
    READ_ALOUD("Read aloud", "ጮክ ብለህ አንብብ", "Sagaleedhaan dubbisi"),
    STOP("Stop", "አቁም", "Dhaabi"),
    VOICE_UNAVAILABLE("Voice input is not available on this phone for this language", "ለዚህ ቋንቋ በዚህ ስልክ የድምፅ ግብዓት የለም", "Galchiin sagalee afaan kanaaf bilbila kana irratti hin jiru"),
    TTS_UNAVAILABLE("Read-aloud is not available on this phone for this language", "ለዚህ ቋንቋ በዚህ ስልክ ጮክ ብሎ ማንበብ የለም", "Sagaleedhaan dubbisuun afaan kanaaf bilbila kana irratti hin jiru"),

    // Results
    RESULT("Result", "ውጤት", "Bu'aa"),
    EDIT("Edit", "አስተካክል", "Gulaali"),
    PREVIEW("Preview", "ቅድመ እይታ", "Duraan ilaali"),
    SAVE("Save", "አስቀምጥ", "Olkaa'i"),
    SAVED("Saved", "ተቀምጧል", "Olkaa'ameera"),
    EXPORT("Export / Share", "ላክ / አጋራ", "Ergi / Qoodi"),
    EXPORT_PDF("Export PDF", "PDF ላክ", "PDF ergi"),
    EXPORT_TEXT("Share as text", "እንደ ጽሑፍ አጋራ", "Akka barreeffamaatti qoodi"),
    DELETE("Delete", "ሰርዝ", "Haqi"),
    DOCUMENT_TITLE("Title", "ርዕስ", "Mata duree"),
    SOURCE_AI("Drafted by AI – please check before sending", "በAI የተረቀቀ – ከመላክዎ በፊት ያረጋግጡ", "AI'n kan qophaa'e – osoo hin ergin dura mirkaneessi"),
    SOURCE_TEMPLATE("Made from a manual template", "ከእጅ ቅጽ የተሰራ", "Unkaa harkaan irraa kan hojjetame"),
    SOURCE_CALCULATED("Totals calculated by the app from your records", "ድምሮቹ መተግበሪያው ከመዝገብዎ ያሰላቸው ናቸው", "Waliigalli appiin galmee kee irraa herregame"),
    FALLBACK_AI_UNAVAILABLE(
        "The AI service could not be reached, so a manual template was used instead. You can edit it now or try again later.",
        "የAI አገልግሎቱን ማግኘት ስላልተቻለ በምትኩ የእጅ ቅጽ ጥቅም ላይ ውሏል። አሁን ማስተካከል ወይም በኋላ እንደገና መሞከር ይችላሉ።",
        "Tajaajilli AI argamuu waan hin dandeenyeef, bakka isaa unkaan harkaa fayyadameera. Amma gulaaluu ykn booda irra deebi'ee yaaluu dandeessa.",
    ),
    FALLBACK_QUOTA(
        "You have used this month's AI allowance, so a manual template was used. Upgrade your plan or wait for the monthly reset.",
        "የዚህን ወር የAI መጠን ስለጨረሱ የእጅ ቅጽ ጥቅም ላይ ውሏል። እቅድዎን ያሳድጉ ወይም ወርሃዊ ዳግም ማስጀመሪያን ይጠብቁ።",
        "Hanga AI ji'a kanaa waan fixxeef unkaan harkaa fayyadameera. Karoora kee guddisi ykn haaromsa ji'aa eegi.",
    ),
    FALLBACK_NO_CONSENT(
        "AI drafting is off, so a manual template was used. You can turn AI on in Settings.",
        "AI ማርቀቅ ስለጠፋ የእጅ ቅጽ ጥቅም ላይ ውሏል። AIን በቅንብሮች ውስጥ ማብራት ይችላሉ።",
        "Qopheessuun AI waan cufaa ta'eef unkaan harkaa fayyadameera. Qindaa'ina keessatti AI banuu dandeessa.",
    ),
    FALLBACK_UNVERIFIED_NUMBERS(
        "The AI draft contained numbers that are not in your information (%1\$s), so it was replaced with a template built from your own figures.",
        "የAI ረቂቁ በመረጃዎ ውስጥ የሌሉ ቁጥሮችን (%1\$s) ስለያዘ ከራስዎ አሃዞች በተሰራ ቅጽ ተተክቷል።",
        "Qophiin AI lakkoofsota odeeffannoo kee keessa hin jirre (%1\$s) waan qabuuf, unkaa lakkoofsota kee irraa hojjetameen bakka bu'ameera.",
    ),
    WARNING_CHECK_NUMBERS(
        "Check these numbers before sending – they were not in your information: %1\$s",
        "ከመላክዎ በፊት እነዚህን ቁጥሮች ያረጋግጡ – በመረጃዎ ውስጥ አልነበሩም፦ %1\$s",
        "Osoo hin ergin dura lakkoofsota kana mirkaneessi – odeeffannoo kee keessa hin turre: %1\$s",
    ),
    TRANSLATION_OFFLINE(
        "Translation needs the AI service. Your text has been kept below so you can translate it later or by hand.",
        "ትርጉም የAI አገልግሎትን ይፈልጋል። በኋላ ወይም በእጅ እንዲተረጉሙት ጽሑፍዎ ከታች ተቀምጧል።",
        "Hiikkaan tajaajila AI barbaada. Booda ykn harkaan akka hiiktuuf barreeffamni kee armaan gaditti kaa'ameera.",
    ),

    // Records
    RECORDS("Records", "መዝገቦች", "Galmeewwan"),
    SALE("Sale", "ሽያጭ", "Gurgurtaa"),
    EXPENSE("Expense", "ወጪ", "Baasii"),
    DATE("Date", "ቀን", "Guyyaa"),
    DESCRIPTION("What was sold / bought", "የተሸጠው / የተገዛው", "Kan gurguramee / bitame"),
    CATEGORY_OPTIONAL("Category (optional)", "ምድብ (አማራጭ)", "Ramaddii (dirqama miti)"),
    QUANTITY("Quantity", "ብዛት", "Baay'ina"),
    UNIT_AMOUNT("Price per unit (ETB)", "የአንዱ ዋጋ (ብር)", "Gatii tokkoo (ETB)"),
    CUSTOMER_OR_SUPPLIER_OPTIONAL("Customer / supplier (optional)", "ደንበኛ / አቅራቢ (አማራጭ)", "Maamila / dhiyeessaa (dirqama miti)"),
    NOTE_OPTIONAL("Note (optional)", "ማስታወሻ (አማራጭ)", "Yaadannoo (dirqama miti)"),
    INVALID_AMOUNT("Enter an amount like 250 or 1,250.50", "እንደ 250 ወይም 1,250.50 ያለ መጠን ያስገቡ", "Hanga akka 250 ykn 1,250.50 galchi"),
    INVALID_QUANTITY("Enter a quantity greater than 0", "ከ0 በላይ የሆነ ብዛት ያስገቡ", "Baay'ina 0 ol ta'e galchi"),
    INVALID_DATE("Enter the date as YYYY-MM-DD", "ቀኑን በ YYYY-MM-DD ቅርጸት ያስገቡ", "Guyyaa akka YYYY-MM-DD galchi"),
    NO_RECORDS("No records yet", "እስካሁን ምንም መዝገብ የለም", "Hanga ammaatti galmeen hin jiru"),
    NO_RECORDS_FOR_PERIOD(
        "There are no records for this period. Nothing was estimated.",
        "ለዚህ ጊዜ ምንም መዝገብ የለም። ምንም ግምት አልተሰራም።",
        "Yeroo kanaaf galmeen hin jiru. Homtuu hin tilmaamamne.",
    ),

    // Invoice builder
    ADD_ITEM("Add item", "እቃ ጨምር", "Meeshaa dabali"),
    DOCUMENT_NUMBER("Number", "ቁጥር", "Lakkoofsa"),
    DUE_DATE_OPTIONAL("Due date (optional)", "የመክፈያ ቀን (አማራጭ)", "Guyyaa kaffaltii (dirqama miti)"),
    VALID_UNTIL_OPTIONAL("Valid until (optional)", "እስከ … የሚያገለግል (አማራጭ)", "Hanga … hojii irra oola (dirqama miti)"),
    DISCOUNT_OPTIONAL("Discount (ETB, optional)", "ቅናሽ (ብር፣ አማራጭ)", "Hir'isa (ETB, dirqama miti)"),
    TAX_LABEL("Tax name", "የታክስ ስም", "Maqaa gibiraa"),
    TAX_RATE("Tax rate %", "የታክስ መጠን %", "Sadarkaa gibiraa %"),
    ADD_TAX("Add a tax line", "የታክስ መስመር ጨምር", "Sarara gibiraa dabali"),
    TAX_NOTICE(
        "Enter only a tax rate that applies to your business. The app does not suggest tax rates or tell you what you must charge.",
        "ለንግድዎ የሚመለከተውን የታክስ መጠን ብቻ ያስገቡ። መተግበሪያው የታክስ መጠን አይጠቁምም ወይም ምን ማስከፈል እንዳለብዎ አይነግርዎትም።",
        "Sadarkaa gibiraa daldala keetiif ilaallatu qofa galchi. Appiin sadarkaa gibiraa hin yaada'u, ykn maal kaffalchiisuu akka qabdu sitti hin himu.",
    ),
    RECORD_PAYMENT("Record a payment received", "የተቀበሉትን ክፍያ መዝግብ", "Kaffaltii argatte galmeessi"),
    PAYMENT_NOTICE(
        "Only record money you have actually received. The app never marks an invoice as paid on its own.",
        "በእርግጥ የተቀበሉትን ገንዘብ ብቻ ይመዝግቡ። መተግበሪያው ኢንቮይስን በራሱ እንደተከፈለ አይመዘግብም።",
        "Maallaqa dhugumaan argatte qofa galmeessi. Appiin ofumaan inivooysii akka kaffalametti hin galmeessu.",
    ),
    NOTES_OPTIONAL("Notes (optional)", "ማስታወሻ (አማራጭ)", "Yaadannoo (dirqama miti)"),
    NEED_ONE_ITEM("Add at least one item", "ቢያንስ አንድ እቃ ያክሉ", "Yoo xiqqaate meeshaa tokko dabali"),
    REMOVE("Remove", "አስወግድ", "Balleessi"),

    // Document labels
    DOC_INVOICE("INVOICE", "ኢንቮይስ", "INIVOOYSII"),
    DOC_QUOTATION("QUOTATION", "የዋጋ ማቅረቢያ", "TILMAAMA GATII"),
    DOC_BILL_TO("Bill to", "ለ", "Kan kaffalu"),
    DOC_ITEM("Item", "እቃ", "Meeshaa"),
    DOC_QTY("Qty", "ብዛት", "Baay'ina"),
    DOC_UNIT_PRICE("Unit price", "የአንዱ ዋጋ", "Gatii tokkoo"),
    DOC_AMOUNT("Amount", "መጠን", "Hanga"),
    DOC_SUBTOTAL("Subtotal", "ንዑስ ድምር", "Ida'ama xiqqaa"),
    DOC_DISCOUNT("Discount", "ቅናሽ", "Hir'isa"),
    DOC_TOTAL("Total", "ጠቅላላ ድምር", "Waliigala"),
    DOC_PAID("Amount paid", "የተከፈለ", "Kan kaffalame"),
    DOC_BALANCE("Balance due", "ቀሪ ክፍያ", "Hambaa kaffaltii"),
    DOC_STATUS("Status", "ሁኔታ", "Haala"),
    DOC_DUE("Due date", "የመክፈያ ቀን", "Guyyaa kaffaltii"),
    DOC_VALID_UNTIL("Valid until", "እስከ … ያገለግላል", "Hanga … hojii irra oola"),
    DOC_THANKS("Thank you for your business.", "ስለ ንግድዎ እናመሰግናለን።", "Daldala keessaniif galatoomaa."),
    STATUS_UNPAID("Unpaid", "ያልተከፈለ", "Hin kaffalamne"),
    STATUS_PARTIAL("Partly paid", "በከፊል የተከፈለ", "Gartokkoon kaffalame"),
    STATUS_PAID("Paid (recorded by you)", "የተከፈለ (በእርስዎ የተመዘገበ)", "Kaffalame (siin galmeeffame)"),
    STATUS_OVERPAID("Overpaid", "ከመጠን በላይ የተከፈለ", "Caalaa kaffalame"),

    // Summary labels
    SUM_SALES("Total sales", "ጠቅላላ ሽያጭ", "Gurgurtaa waliigalaa"),
    SUM_EXPENSES("Total expenses", "ጠቅላላ ወጪ", "Baasii waliigalaa"),
    SUM_NET("Sales minus expenses", "ሽያጭ ሲቀነስ ወጪ", "Gurgurtaa hir'isa baasii"),
    SUM_SALE_COUNT("Number of sales", "የሽያጭ ብዛት", "Baay'ina gurgurtaa"),
    SUM_EXPENSE_COUNT("Number of expenses", "የወጪ ብዛት", "Baay'ina baasii"),
    SUM_TOP_ITEMS("Sales by item", "ሽያጭ በእቃ", "Gurgurtaa meeshaadhaan"),
    SUM_EXPENSES_BY_CATEGORY("Expenses by category", "ወጪ በምድብ", "Baasii ramaddiidhaan"),
    SUM_PERIOD("Period", "ጊዜ", "Yeroo"),

    // Library
    LIBRARY("Library", "ማህደር", "Kuusaa"),
    SEARCH("Search saved documents", "የተቀመጡ ሰነዶችን ፈልግ", "Galmee olkaa'aman barbaadi"),
    NO_DOCUMENTS("Nothing saved yet", "እስካሁን ምንም አልተቀመጠም", "Hanga ammaatti homtuu hin olkaa'amne"),
    HISTORY("Conversation history", "የውይይት ታሪክ", "Seenaa haasawaa"),
    DELETE_CONFIRM("Delete permanently?", "በቋሚነት ይሰረዝ?", "Dhaabbataan haqamu?"),

    // Settings
    SETTINGS("Settings", "ቅንብሮች", "Qindaa'ina"),
    LANGUAGE("Language", "ቋንቋ", "Afaan"),
    PRIVACY("Privacy", "ግላዊነት", "Iccitii"),
    KEEP_RECORDS_FOR("Keep sales and expense records for", "የሽያጭ እና የወጪ መዝገቦችን ለምን ያህል ጊዜ ያቆዩ", "Galmee gurgurtaa fi baasii hammam turtu"),
    MONTHS("%1\$d months", "%1\$d ወራት", "Ji'a %1\$d"),
    FOREVER("Until I delete them", "እስክሰርዛቸው ድረስ", "Hanga ani haqutti"),
    DELETE_CONVERSATIONS("Delete all conversations", "ሁሉንም ውይይቶች ሰርዝ", "Haasawa hunda haqi"),
    DELETE_DOCUMENTS("Delete all saved documents", "ሁሉንም የተቀመጡ ሰነዶች ሰርዝ", "Galmee olkaa'aman hunda haqi"),
    DELETE_RECORDS("Delete all sales and expense records", "ሁሉንም የሽያጭ እና የወጪ መዝገቦች ሰርዝ", "Galmee gurgurtaa fi baasii hunda haqi"),
    DELETE_EVERYTHING("Delete all my data", "ሁሉንም መረጃዬን ሰርዝ", "Odeeffannoo koo hunda haqi"),
    DELETED("Deleted", "ተሰርዟል", "Haqameera"),
    PLANS("Plans", "እቅዶች", "Karoorawwan"),
    UPGRADE("Upgrade", "አሳድግ", "Guddisi"),
    CURRENT_PLAN("Current plan", "የአሁኑ እቅድ", "Karoora ammaa"),
    PAYMENTS_NOT_CONFIGURED(
        "Paid plans are not available yet: no payment provider has been set up for this app. You have not been charged.",
        "የሚከፈልባቸው እቅዶች ገና አይገኙም፤ ለዚህ መተግበሪያ የክፍያ አቅራቢ አልተዘጋጀም። ምንም ክፍያ አልተወሰደብዎትም።",
        "Karoorawwan kaffaltii qaban ammaaf hin jiran: appii kanaaf dhiyeessaan kaffaltii hin qophoofne. Homtuu sirraa hin kaffalamne.",
    ),
    PER_MONTH("%1\$s per month", "በወር %1\$s", "Ji'aan %1\$s"),
    AI_DRAFTS_PER_MONTH("%1\$d AI drafts per month", "በወር %1\$d የAI ረቂቆች", "Ji'aan qophii AI %1\$d"),
    BUNDLE_EXTRA("+%1\$d AI drafts, one time", "+%1\$d የAI ረቂቆች፣ አንድ ጊዜ", "+qophii AI %1\$d, al tokko"),
    ERROR_GENERIC("Something went wrong. Please try again.", "የሆነ ችግር ተፈጥሯል። እባክዎ እንደገና ይሞክሩ።", "Wanti tokko dogoggora. Maaloo irra deebi'ii yaali."),
    ;

    fun get(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> en
        AppLanguage.AMHARIC -> am
        AppLanguage.OROMO -> om
    }

    fun format(lang: AppLanguage, vararg args: Any): String = String.format(java.util.Locale.ROOT, get(lang), *args)
}

/** Labels for choice values used in task forms. */
object ChoiceLabels {
    private val labels: Map<String, Triple<String, String, String>> = mapOf(
        "payment_reminder" to Triple("Payment reminder", "የክፍያ ማስታወሻ", "Yaadachiisa kaffaltii"),
        "thank_you" to Triple("Thank you", "ምስጋና", "Galata"),
        "order_ready" to Triple("Order is ready", "ትዕዛዙ ዝግጁ ነው", "Ajajni qophaa'eera"),
        "new_product" to Triple("New product", "አዲስ ምርት", "Oomisha haaraa"),
        "apology" to Triple("Apology", "ይቅርታ", "Dhiifama"),
        "other" to Triple("Other", "ሌላ", "Kan biraa"),
        "friendly" to Triple("Friendly", "ወዳጃዊ", "Michummaa"),
        "formal" to Triple("Formal", "መደበኛ", "Idilee"),
        "short" to Triple("Short", "አጭር", "Gabaabaa"),
        "request" to Triple("Request", "ጥያቄ", "Gaaffii"),
        "complaint" to Triple("Complaint", "ቅሬታ", "Komii"),
        "introduction" to Triple("Introduction", "መግቢያ", "Seensa"),
        "confirmation" to Triple("Confirmation", "ማረጋገጫ", "Mirkaneessa"),
        "price_change" to Triple("Price change", "የዋጋ ለውጥ", "Jijjiirama gatii"),
        "today" to Triple("Today", "ዛሬ", "Har'a"),
        "this_month" to Triple("This month", "ይህ ወር", "Ji'a kana"),
        "last_month" to Triple("Last month", "ያለፈው ወር", "Ji'a darbe"),
    )

    fun label(key: String, lang: AppLanguage): String {
        val t = labels[key] ?: return key
        return when (lang) {
            AppLanguage.ENGLISH -> t.first
            AppLanguage.AMHARIC -> t.second
            AppLanguage.OROMO -> t.third
        }
    }

    fun allKeys(): Set<String> = labels.keys
}
