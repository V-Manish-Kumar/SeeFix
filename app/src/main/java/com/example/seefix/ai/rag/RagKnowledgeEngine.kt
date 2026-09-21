package com.example.seefix.ai.rag

import android.content.Context
import com.example.seefix.domain.model.BoundingBox
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.TroubleshootingStep
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.domain.model.WorkDomain
import kotlinx.serialization.json.Json

/**
 * Generalized Local Knowledge / RAG Engine for field-work technical manuals.
 * Parses structured TechnicalDocuments and indexes them into flattened RagChunks
 * for multi-factor domain-aware retrieval.
 */
class RagKnowledgeEngine(private val context: Context? = null) {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val indexedDocuments = mutableListOf<TechnicalDocument>()
    private val indexedChunks = mutableListOf<RagChunk>()

    init {
        loadAndIndexAllDocuments()
    }

    fun clearKnowledgeBase() {
        indexedDocuments.clear()
        indexedChunks.clear()
    }

    fun getIndexedDocuments(): List<TechnicalDocument> = indexedDocuments.toList()
    fun getIndexedChunks(): List<RagChunk> = indexedChunks.toList()

    private fun loadAndIndexAllDocuments() {
        val loadedDocs = mutableListOf<TechnicalDocument>()

        // 1. Try reading all JSON assets from Android Context
        if (context != null) {
            try {
                val assetFiles = context.assets.list("")?.filter { it.endsWith(".json") } ?: emptyList()
                for (fileName in assetFiles) {
                    try {
                        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
                        val doc = jsonParser.decodeFromString<TechnicalDocument>(jsonString)
                        loadedDocs.add(doc)
                    } catch (_: Exception) {
                        // Skip unparseable JSON asset
                    }
                }
            } catch (_: Exception) {
                // Assets listing failed
            }
        }

        // 2. Fallback to built-in fallback technical documents if no documents loaded from assets
        if (loadedDocs.isEmpty()) {
            loadedDocs.addAll(getFallbackDocuments())
        }

        // 3. Ingest and index all loaded documents
        for (doc in loadedDocs) {
            ingestDocument(doc)
        }
    }

    /**
     * Ingests a new TechnicalDocument dynamically at runtime,
     * adding it to the document index and flattening its sections into RagChunks.
     */
    fun ingestDocument(document: TechnicalDocument) {
        // Prevent duplicate ingestion by documentId
        indexedDocuments.removeAll { it.documentId == document.documentId }
        indexedChunks.removeAll { it.documentId == document.documentId }

        indexedDocuments.add(document)

        for (section in document.sections) {
            val metadata = buildMap {
                put("version", document.version)
                put("language", document.language)
                if (section.problemKeywords.isNotEmpty()) {
                    put("problemKeywords", section.problemKeywords.joinToString(","))
                }
                if (section.safetyWarnings.isNotEmpty()) {
                    put("safetyWarnings", section.safetyWarnings.joinToString(" | "))
                }
                if (section.steps.isNotEmpty()) {
                    put("steps", section.steps.joinToString(" | "))
                }
                section.specifications.forEach { (k, v) ->
                    put("spec_$k", v)
                }
            }

            val chunk = RagChunk(
                chunkId = "${document.documentId}_${section.sectionId}",
                documentId = document.documentId,
                documentTitle = document.title,
                source = document.source,
                sectionTitle = section.sectionTitle,
                pageNumber = section.pageNumber,
                content = section.content,
                domain = document.domain,
                documentType = document.documentType,
                equipmentType = document.equipmentType,
                component = document.component,
                manufacturer = document.manufacturer,
                model = document.model,
                relevanceScore = 0f,
                safetyLevel = document.safetyLevel,
                metadata = metadata
            )

            indexedChunks.add(chunk)
        }
    }

    /**
     * Multi-factor domain-aware ranking retrieval method.
     */
    fun retrieve(queryContext: RagQueryContext): List<RagChunk> {
        if (indexedChunks.isEmpty()) return emptyList()

        val queryTerms = queryContext.query
            .lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotBlank() }

        val scoredChunks = indexedChunks.map { chunk ->
            var baseKeywordScore = 0f

            val sectionTitleLower = chunk.sectionTitle.lowercase()
            val contentLower = chunk.content.lowercase()
            val docTitleLower = chunk.documentTitle.lowercase()
            val problemKeywordsLower = (chunk.metadata["problemKeywords"] ?: "").lowercase()

            for (term in queryTerms) {
                if (problemKeywordsLower.contains(term)) {
                    baseKeywordScore += 2.5f
                }
                if (sectionTitleLower.contains(term)) {
                    baseKeywordScore += 2.0f
                }
                if (contentLower.contains(term)) {
                    baseKeywordScore += 1.0f
                }
                if (docTitleLower.contains(term)) {
                    baseKeywordScore += 1.0f
                }
            }

            // Domain Match Boost (+0.3f if chunk.domain == queryContext.domain)
            var domainBoost = 0f
            if (queryContext.domain != null && chunk.domain == queryContext.domain) {
                domainBoost = 0.3f
            }

            // Equipment / Component Match Boost (+0.25f)
            var eqCompBoost = 0f
            val eqTypeMatch = !queryContext.equipmentType.isNullOrBlank() &&
                    chunk.equipmentType?.contains(queryContext.equipmentType, ignoreCase = true) == true
            val compMatch = !queryContext.component.isNullOrBlank() &&
                    chunk.component?.contains(queryContext.component, ignoreCase = true) == true

            if (eqTypeMatch || compMatch) {
                eqCompBoost = 0.25f
            }

            // Manufacturer / Model Match Boost (+0.2f)
            var mfgModelBoost = 0f
            val mfgMatch = !queryContext.manufacturer.isNullOrBlank() &&
                    chunk.manufacturer?.contains(queryContext.manufacturer, ignoreCase = true) == true
            val modelMatch = !queryContext.model.isNullOrBlank() &&
                    chunk.model?.contains(queryContext.model, ignoreCase = true) == true

            if (mfgMatch || modelMatch) {
                mfgModelBoost = 0.2f
            }

            // DocumentType Relevance Boost (+0.15f)
            var docTypeBoost = 0f
            if (queryContext.documentType != null && chunk.documentType == queryContext.documentType) {
                docTypeBoost = 0.15f
            }

            var totalScore = baseKeywordScore + domainBoost + eqCompBoost + mfgModelBoost + docTypeBoost

            // Safety Weighting
            if (queryContext.safetyFilterOnly) {
                val isSafetyWarning = chunk.safetyLevel != SafetySeverity.LOW ||
                        chunk.documentType == DocumentType.SAFETY_DOCUMENT ||
                        !chunk.metadata["safetyWarnings"].isNullOrBlank()
                if (isSafetyWarning) {
                    totalScore += 0.5f
                } else {
                    totalScore *= 0.1f
                }
            }

            chunk.copy(relevanceScore = totalScore)
        }

        // Filter out chunks with 0 score unless queryTerms are empty and boosts applied
        val validChunks = if (scoredChunks.any { it.relevanceScore > 0f }) {
            scoredChunks.filter { it.relevanceScore > 0f }
        } else {
            scoredChunks
        }

        return validChunks
            .sortedByDescending { it.relevanceScore }
            .take(queryContext.maxChunks)
    }

    /**
     * Builds RagQueryContext directly from active WorkContext and retrieves matching chunks.
     */
    fun retrieveForWorkContext(workContext: WorkContext, query: String): List<RagChunk> {
        val queryContext = RagQueryContext(
            query = query,
            domain = workContext.domain,
            equipmentType = workContext.machineInfo?.category ?: workContext.task?.title,
            manufacturer = workContext.machineInfo?.deviceName,
            model = workContext.machineInfo?.modelNumber,
            maxChunks = 3
        )
        return retrieve(queryContext)
    }

    /**
     * Legacy compatibility method for querying technical manuals and returning structured step guidance.
     */
    fun queryKnowledge(
        queryText: String,
        deviceHint: String? = null
    ): RagQueryResult {
        val queryContext = RagQueryContext(
            query = queryText,
            equipmentType = deviceHint,
            manufacturer = deviceHint,
            maxChunks = 3
        )
        val chunks = retrieve(queryContext)
        val topChunk = chunks.firstOrNull()

        val matchingDoc = topChunk?.let { chunk ->
            indexedDocuments.find { it.documentId == chunk.documentId }
        } ?: indexedDocuments.firstOrNull() ?: getFallbackDocuments().first()

        val title = topChunk?.documentTitle ?: matchingDoc.title
        val docId = topChunk?.documentId ?: matchingDoc.documentId
        val safetyWarnings = chunks.flatMap { chunk ->
            chunk.metadata["safetyWarnings"]?.split(" | ") ?: emptyList()
        }.distinct().ifEmpty {
            matchingDoc.sections.flatMap { it.safetyWarnings }
        }

        val specs = topChunk?.let { chunk ->
            chunk.metadata.filterKeys { it.startsWith("spec_") }
                .mapKeys { it.key.removePrefix("spec_") }
        } ?: matchingDoc.sections.firstOrNull()?.specifications ?: emptyMap()

        val stepsList = chunks.flatMap { chunk ->
            chunk.metadata["steps"]?.split(" | ") ?: emptyList()
        }.ifEmpty {
            matchingDoc.sections.flatMap { it.steps }
        }

        val convertedSteps = stepsList.mapIndexed { index, stepText ->
            TroubleshootingStep(
                stepNumber = index + 1,
                title = "Step ${index + 1}",
                instructionText = stepText,
                spokenInstruction = stepText,
                visualVerificationPrompt = "Align camera with step target area.",
                targetBoundingBox = getBoundingBoxForStep(matchingDoc.domain.name, index)
            )
        }

        val guide = TroubleshootingGuide(
            id = topChunk?.chunkId ?: "guide_1",
            problemKeywords = topChunk?.metadata["problemKeywords"]?.split(",") ?: emptyList(),
            symptomDescription = queryText,
            rootCause = topChunk?.content ?: "Technical Manual Guidance",
            safetyLevel = topChunk?.safetyLevel?.name ?: "SAFE",
            steps = convertedSteps.map { s ->
                ManualGuideStep(
                    stepNumber = s.stepNumber,
                    title = s.title,
                    instructionText = s.instructionText,
                    spokenInstruction = s.spokenInstruction,
                    visualVerificationPrompt = s.visualVerificationPrompt
                )
            }
        )

        val score = topChunk?.relevanceScore ?: 0f
        val confidence = if (score > 0f) (score / 5f).coerceIn(0.70f, 0.98f) else 0.82f

        return RagQueryResult(
            matchingManualTitle = title,
            manualId = docId,
            relevantSafetyWarnings = safetyWarnings,
            specifications = specs,
            matchedGuide = guide,
            recommendedSteps = convertedSteps,
            confidenceScore = confidence
        )
    }

    private fun getBoundingBoxForStep(category: String, stepIndex: Int): BoundingBox {
        return when (stepIndex) {
            0 -> BoundingBox(0.25f, 0.30f, 0.65f, 0.65f)
            1 -> BoundingBox(0.35f, 0.40f, 0.75f, 0.75f)
            2 -> BoundingBox(0.20f, 0.25f, 0.60f, 0.60f)
            else -> BoundingBox(0.15f, 0.20f, 0.85f, 0.80f)
        }
    }

    private fun getFallbackDocuments(): List<TechnicalDocument> {
        return listOf(
            TechnicalDocument(
                documentId = "DOC-AUTO-BRAKE-001",
                title = "Automotive Brake System Service & Maintenance Manual",
                source = "Automotive_Brake_Manual.json",
                domain = WorkDomain.AUTOMOTIVE,
                documentType = DocumentType.SERVICE_MANUAL,
                manufacturer = "Brembo",
                model = "ABS-Pro-4000",
                equipmentType = "Brake System",
                component = "Brake Caliper & Bleeder",
                safetyLevel = SafetySeverity.HIGH,
                sections = listOf(
                    DocumentSection(
                        sectionId = "sec_auto_safety",
                        sectionTitle = "Brake System Safety & Hydraulic Fluid Handling",
                        content = "DANGER: Brake fluid DOT 4 is hazardous and corrosive to vehicle paint and eyes. Always wear nitrile gloves and protective eyewear. Never inspect or service brake lines while the hydraulic system is hot or under pressure. Vehicle must be securely raised using jack stands.",
                        pageNumber = 1,
                        problemKeywords = listOf("brake", "fluid", "leak", "corrosive", "safety", "jack stand", "danger"),
                        safetyWarnings = listOf(
                            "DANGER: Corrosive DOT4 brake fluid hazard. Wear eye protection and nitrile gloves.",
                            "WARNING: Use heavy-duty jack stands under chassis frame before removing wheels."
                        ),
                        specifications = mapOf("Fluid Specification" to "DOT 4 Synthetic Brake Fluid", "Minimum Rotor Thickness" to "22.0 mm"),
                        steps = listOf(
                            "Lift vehicle with floor jack and seat chassis on 3-ton rated jack stands.",
                            "Inspect master cylinder fluid reservoir level."
                        )
                    ),
                    DocumentSection(
                        sectionId = "sec_bleed_procedure",
                        sectionTitle = "Hydraulic Brake Bleed & Air Purge Procedure",
                        content = "Connect transparent bleeder hose to caliper bleeder valve starting at the furthest wheel from master cylinder (rear right). Submerge hose end in brake fluid reservoir. Depress brake pedal while opening bleeder valve until fluid runs clear without air bubbles.",
                        pageNumber = 3,
                        problemKeywords = listOf("bleed", "spongy", "pedal", "air bubble", "hydraulic", "caliper"),
                        safetyWarnings = listOf("WARNING: Ensure master cylinder reservoir does not run dry during bleeding."),
                        specifications = mapOf("Bleeder Nipple Torque" to "10 Nm", "System Pressure" to "1.5 bar"),
                        steps = listOf(
                            "Attach transparent tubing to rear right caliper bleeder screw.",
                            "Press brake pedal slowly and open bleeder screw 1/4 turn until clear fluid flows."
                        )
                    )
                )
            ),
            TechnicalDocument(
                documentId = "DOC-ELEC-MOTOR-001",
                title = "3-Phase Industrial Induction Motor Maintenance Manual",
                source = "Electrical_Motor_Manual.json",
                domain = WorkDomain.ELECTRICAL,
                documentType = DocumentType.TECHNICAL_MANUAL,
                manufacturer = "Siemens",
                model = "1LE1001-1AC4",
                equipmentType = "Induction Motor",
                component = "Stator Winding & Terminal Box",
                safetyLevel = SafetySeverity.CRITICAL_STOP,
                sections = listOf(
                    DocumentSection(
                        sectionId = "sec_motor_safety",
                        sectionTitle = "High Voltage Safety & Lockout Tagout (LOTO)",
                        content = "CRITICAL HAZARD: 415V 3-phase AC voltage presents severe shock and electrocution hazard. Before opening junction box or touching motor leads, lock out main disconnect switch and verify zero voltage with CAT III 1000V multimeter across all 3 phases (L1, L2, L3).",
                        pageNumber = 1,
                        problemKeywords = listOf("high voltage", "loto", "shock", "lockout", "multimeter", "zero voltage"),
                        safetyWarnings = listOf(
                            "CRITICAL HAZARD: 415V AC 3-Phase shock hazard. Isolate power and apply Lockout/Tagout.",
                            "WARNING: Discharge run and start capacitors before touching terminal leads."
                        ),
                        specifications = mapOf("Rated Voltage" to "400V / 690V AC 50Hz", "Insulation Class" to "Class F"),
                        steps = listOf(
                            "Apply LOTO lock to main motor control center (MCC) breaker.",
                            "Test phase-to-phase and phase-to-ground voltage with CAT III 1000V meter."
                        )
                    ),
                    DocumentSection(
                        sectionId = "sec_insulation_testing",
                        sectionTitle = "Winding Insulation Resistance & Megohmmeter Testing",
                        content = "Test stator winding insulation resistance using a 500V or 1000V DC megohmmeter (Megger). Connect test leads between phase terminals (U, V, W) and motor frame ground. Insulation resistance must exceed 100 Megohms for healthy operation.",
                        pageNumber = 2,
                        problemKeywords = listOf("insulation", "megger", "resistance", "winding", "stator", "ground fault"),
                        safetyWarnings = listOf("CAUTION: High DC test voltage applied during megger test. Do not touch leads during test."),
                        specifications = mapOf("Min Insulation Resistance" to "100 MΩ", "Test DC Voltage" to "1000 V DC"),
                        steps = listOf(
                            "Disconnect motor leads from inverter output terminals.",
                            "Apply 1000V DC megger test between phase U1 and motor frame ground for 60 seconds."
                        )
                    )
                )
            ),
            TechnicalDocument(
                documentId = "DOC-IT-ROUTER-001",
                title = "Enterprise Router & Network Gateway Configuration Manual",
                source = "IT_Router_Config_Manual.json",
                domain = WorkDomain.IT_NETWORKING,
                documentType = DocumentType.CONFIGURATION_GUIDE,
                manufacturer = "Cisco Systems",
                model = "ISR-4331-K9",
                equipmentType = "Enterprise Router",
                component = "WAN Interface & Management Port",
                safetyLevel = SafetySeverity.LOW,
                sections = listOf(
                    DocumentSection(
                        sectionId = "sec_router_led",
                        sectionTitle = "Router LED Diagnostics & Interface Status",
                        content = "System status LEDs provide immediate diagnostic feedback. STAT LED solid green indicates normal operation. Amber STAT LED indicates boot diagnostic failure or system thermal warning. WAN link LED amber/off indicates physical carrier loss or SFP transceiver mismatch.",
                        pageNumber = 1,
                        problemKeywords = listOf("led", "diagnostic", "stat", "amber", "wan", "sfp", "carrier loss"),
                        safetyWarnings = listOf("CAUTION: Do not stare directly into active fiber optic transceiver optical ports."),
                        specifications = mapOf("Console Baud Rate" to "9600 8-N-1", "Power Consumption" to "42 W"),
                        steps = listOf(
                            "Observe front panel STAT and SYS LED indicators.",
                            "Verify RJ-45 Cat6 cable seating in GigabitEthernet 0/0/0 port."
                        )
                    ),
                    DocumentSection(
                        sectionId = "sec_wan_reset",
                        sectionTitle = "WAN Interface Reset & VLAN Routing Configuration",
                        content = "To resolve WAN interface packet drop or ARP timeout, access command line interface (CLI) via console cable. Execute 'interface GigabitEthernet0/0/0' followed by 'shutdown' and 'no shutdown' commands to reset phy layer. Configure sub-interfaces with 'encapsulation dot1Q <vlan-id>' for trunk VLAN routing.",
                        pageNumber = 2,
                        problemKeywords = listOf("wan", "reset", "vlan", "routing", "cli", "ip address", "trunk"),
                        safetyWarnings = listOf("NOTE: Interface reset will briefly interrupt all active WAN network traffic."),
                        specifications = mapOf("Default IP" to "192.168.1.1", "VLAN Protocol" to "IEEE 802.1Q"),
                        steps = listOf(
                            "Connect console cable to RJ-45 console port and launch terminal emulator.",
                            "Issue 'shutdown' then 'no shutdown' on WAN interface GE0/0/0."
                        )
                    )
                )
            ),
            TechnicalDocument(
                documentId = "DOC-PLUMB-PUMP-001",
                title = "Submersible Sump Pump & Piping Installation Manual",
                source = "Plumbing_Pump_Manual.json",
                domain = WorkDomain.PLUMBING,
                documentType = DocumentType.INSTALLATION_GUIDE,
                manufacturer = "Zoeller",
                model = "M53 Mighty-Mate",
                equipmentType = "Submersible Sump Pump",
                component = "Impeller & Check Valve Assembly",
                safetyLevel = SafetySeverity.MEDIUM,
                sections = listOf(
                    DocumentSection(
                        sectionId = "sec_pump_safety",
                        sectionTitle = "Sump Pump Electrical & Water Safety Guidelines",
                        content = "WARNING: Electrical shock hazard. Always disconnect main power breaker before reaching into sump basin. Pump must be connected to a dedicated Ground Fault Circuit Interrupter (GFCI) outlet. Never handle pump with wet hands or standing in water.",
                        pageNumber = 1,
                        problemKeywords = listOf("pump", "water", "gfci", "shock", "basin", "sump"),
                        safetyWarnings = listOf("WARNING: Shock hazard in standing water. Disconnect GFCI outlet before basin servicing."),
                        specifications = mapOf("Motor HP" to "0.3 HP", "Discharge Size" to "1.5 in NPT"),
                        steps = listOf(
                            "Unplug pump power cord from GFCI wall receptacle.",
                            "Inspect sump pit for debris and standing sediment."
                        )
                    ),
                    DocumentSection(
                        sectionId = "sec_check_valve",
                        sectionTitle = "Check Valve Replacement & Pressure Switch Calibration",
                        content = "Replace worn 1.5-inch inline rubber check valve to prevent discharge water backflow into sump basin. Ensure flow direction arrow on check valve points upward away from pump. Calibrate vertical float pressure switch travel to prevent continuous pump cycling.",
                        pageNumber = 3,
                        problemKeywords = listOf("check valve", "backflow", "pressure switch", "float switch", "cycling"),
                        safetyWarnings = listOf("NOTICE: Ensure weeping hole (3/16 inch) is drilled in discharge pipe below check valve."),
                        specifications = mapOf("Check Valve Size" to "1.5 in Full-Flow", "Float Switch Type" to "Mechanical Float Switch"),
                        steps = listOf(
                            "Loosen stainless steel hose clamps on 1.5-inch check valve.",
                            "Install replacement check valve ensuring arrow points UP."
                        )
                    )
                )
            ),
            TechnicalDocument(
                documentId = "MAN-CB-2025",
                title = "Main Electrical Panel Circuit Breaker Manual",
                source = "CircuitBreaker_Manual.json",
                domain = WorkDomain.ELECTRICAL,
                documentType = DocumentType.TROUBLESHOOTING_GUIDE,
                manufacturer = "Square D",
                model = "CB-200A-MAIN",
                equipmentType = "Circuit Breaker Panel",
                component = "Dual Pole Breaker Switch",
                safetyLevel = SafetySeverity.CRITICAL_STOP,
                sections = listOf(
                    DocumentSection(
                        sectionId = "sec_breaker_reset",
                        sectionTitle = "Dual Pole Breaker Reset & Overload Diagnostics",
                        content = "Dual pole circuit breaker repeatedly trips or lever rests in center position due to branch overload condition or internal bimetallic strip trip latch lockout. Firmly push the center-tripped breaker toggle switch all the way to OFF until it clicks and resets internal spring tension before switching back to ON.",
                        pageNumber = 2,
                        problemKeywords = listOf("breaker", "trip", "tripped", "no power", "short circuit", "overload", "main panel"),
                        safetyWarnings = listOf("HAZARD: ALWAYS USE HIGH-VOLTAGE INSULATED GLOVES WHEN WORKING NEAR BUS BARS."),
                        specifications = mapOf("Panel Rating" to "120/240V AC Single Phase 200A", "Breaker Type" to "Thermal-Magnetic Dual Pole 30A"),
                        steps = listOf(
                            "Push center-tripped breaker toggle switch completely to OFF position until spring clicks.",
                            "Switch breaker to ON with a single smooth motion while facing away from panel."
                        )
                    )
                )
            ),
            TechnicalDocument(
                documentId = "MAN-WM-IQOO-2025",
                title = "Smart Washing Machine (iQOO Demo Appliance) Service Manual",
                source = "iQOO_Demo_WashingMachine_Manual.json",
                domain = WorkDomain.APPLIANCE,
                documentType = DocumentType.SERVICE_MANUAL,
                manufacturer = "iQOO",
                model = "WM-9000X",
                equipmentType = "Washing Machine",
                component = "Drain Pump & Inverter Drive Board",
                safetyLevel = SafetySeverity.HIGH,
                sections = listOf(
                    DocumentSection(
                        sectionId = "sec_drain_pump",
                        sectionTitle = "Drain Pump Inspection & Capacitor C42 Replacement",
                        content = "Washing machine fails to drain water during spin cycle or displays E20 error. Root cause is obstruction in drain pump impeller or faulty 45uF run capacitor on power drive board. Mount replacement 45uF run capacitor onto metal bracket and torque ground screw to 1.8 Nm.",
                        pageNumber = 2,
                        problemKeywords = listOf("drain", "pump", "water", "overflow", "not draining", "e20", "run capacitor"),
                        safetyWarnings = listOf("DANGER: Unplug appliance from AC outlet before opening back service panel."),
                        specifications = mapOf("Operating Voltage" to "220V - 240V AC 50Hz", "Capacitor Rating" to "45uF +/- 5% 450VAC"),
                        steps = listOf(
                            "Inspect motor harness and drain pump 3-pin connector.",
                            "Replace faulty 45uF run capacitor on bracket and torque screw to 1.8 Nm."
                        )
                    )
                )
            )
        )
    }
}
