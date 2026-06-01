package com.example.network

import android.util.Log
import com.example.data.DataStoreManager
import com.example.data.FitnessDao
import com.example.data.WeeklyReport
import com.example.utils.AlgorithmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ─────────────────────────────────────────────────────────────────
// DATA SHAPES FOR API RESPONSES
// ─────────────────────────────────────────────────────────────────

data class GeminiMacroEstimate(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val confidence: String, // "high", "medium", "low"
    val notes: String
)

data class ParsedWorkoutExercise(
    val name: String,
    val muscleGroup: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val restSeconds: Int
)

data class ParsedWorkoutDay(
    val label: String,
    val day: String,
    val exercises: List<ParsedWorkoutExercise>
)

// ─────────────────────────────────────────────────────────────────
// SERVICE IMPLEMENTATION
// ─────────────────────────────────────────────────────────────────

class GeminiService(
    private val dataStore: DataStoreManager,
    private val dao: FitnessDao
) {

    private val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    // ── COACH PERSONA — prepended to every coaching message ──────
    private val coachPersona = """
        You are APEX, a highly knowledgeable personal trainer and sports nutritionist.
        You communicate in a direct, encouraging, science-backed style.
        Always reference the user's specific numbers when they are available.
        Keep responses concise — 3 to 5 sentences maximum unless a detailed breakdown is explicitly requested.
        Never give generic advice when specific data is present.
        Do not suggest seeing a doctor unless there is a genuine safety concern.
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────
    // FUNCTION 1 — WEEKLY REPORT
    // Called every Sunday. Stores response in Room WeeklyReport table.
    // ─────────────────────────────────────────────────────────────

    suspend fun generateWeeklyReport(
        context: com.example.data.AlgorithmViewModel
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dbWeights = dao.getAllWeightEntries()
            val engineWeights = dbWeights.groupBy { it.date }.map { (date, list) ->
                com.example.utils.WeightEntry(date, list.map { it.weight }.average())
            }.sortedBy { it.date }
            
            val dbNutrition = dao.getAllNutritionEntriesFlow().firstOrNull() ?: emptyList()
            val engineNutrition = dbNutrition.groupBy { it.date }.map { (date, list) ->
                com.example.utils.NutritionEntry(
                    date = date,
                    calories = list.sumOf { it.calories },
                    protein = list.sumOf { it.protein }.toInt(),
                    carbs = list.sumOf { it.carbs }.toInt(),
                    fat = list.sumOf { it.fat }.toInt()
                )
            }.sortedBy { it.date }
            
            val dbSessions = dao.getAllTrainingSessions()
            val completedSessions = dbSessions.filter { it.completed }.map { session ->
                val dbSets = dao.getSetsForSession(session.id)
                val exerciseLogs = dbSets.groupBy { it.exerciseId }.map { (exId, sets) ->
                    val firstSet = sets.firstOrNull()
                    val name = firstSet?.exerciseName ?: "Exercise"
                    val muscle = firstSet?.muscleGroup ?: "General"
                    com.example.utils.ExerciseLog(
                        id = exId,
                        name = name,
                        muscleGroup = muscle,
                        sets = sets.map { s ->
                            com.example.utils.ExerciseSet(
                                weight = s.weight,
                                reps = s.reps,
                                rpe = s.rpe,
                                isWarmup = s.isWarmup,
                                completed = s.completed
                            )
                        }
                    )
                }
                com.example.utils.TrainingSession(
                    date = session.date,
                    sessionType = session.sessionType,
                    completed = session.completed,
                    sessionFeel = session.sessionFeel,
                    durationMinutes = session.durationMinutes,
                    exercises = exerciseLogs
                )
            }

            val valCalTarget = context.targets.value?.calories ?: 2500
            val targets = com.example.utils.NutritionTargets(
                calories = valCalTarget,
                protein = 160,
                carbs = 280,
                fat = 75,
                weeklyTrainingSessions = 4
            )

            val goalStr = dataStore.goalFlow.firstOrNull() ?: "Gain Muscle"

            val prompt = AlgorithmEngine.buildGeminiWeeklyPrompt(
                weightLog = engineWeights,
                nutritionLog = engineNutrition,
                trainingLog = completedSessions,
                targets = targets,
                goal = goalStr
            )

            val systemPrompt = """
                $coachPersona
                Generate a weekly training and nutrition summary. Structure it as:
                1. What went well this week (1-2 sentences, specific numbers)
                2. What to focus on next week (1-2 sentences, actionable)
                3. One science-backed insight relevant to this user's data
                Keep total response under 150 words.
            """.trimIndent()

            callGemini("$systemPrompt\n\n$prompt").also { result ->
                result.onSuccess { text ->
                    val cal = java.util.Calendar.getInstance()
                    while (cal.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) {
                        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                    }
                    val weekStart = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
                    dao.insertWeeklyReport(
                        WeeklyReport(
                            weekStart = weekStart,
                            score = context.complianceScore.value,
                            geminiResponse = text
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "generateWeeklyReport failed", e)
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // FUNCTION 2 — COACH CHAT RESPONSE
    // Called on every user message send. Context injected from ViewModel.
    // ─────────────────────────────────────────────────────────────

    suspend fun generateCoachResponse(
        userMessage: String,
        coachContext: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val fullPrompt = buildString {
            appendLine(coachPersona)
            appendLine()
            appendLine("=== USER'S CURRENT DATA ===")
            appendLine(coachContext)
            appendLine()
            appendLine("=== USER MESSAGE ===")
            appendLine(userMessage)
        }
        callGemini(fullPrompt)
    }

    // ─────────────────────────────────────────────────────────────
    // FUNCTION 3 — MEAL PHOTO MACRO PARSE
    // Called from nutrition photo log. Returns structured macro estimate.
    // ─────────────────────────────────────────────────────────────

    suspend fun parseMealPhoto(base64Image: String, mimeType: String = "image/jpeg"): Result<GeminiMacroEstimate> =
        withContext(Dispatchers.IO) {
            val prompt = """
                Analyze this meal photo and estimate the macronutrient content.
                Respond ONLY with a valid JSON object. No markdown, no backticks, no explanation.
                Format exactly:
                {
                  "calories": <integer>,
                  "protein": <decimal grams>,
                  "carbs": <decimal grams>,
                  "fat": <decimal grams>,
                  "confidence": "<high|medium|low>",
                  "notes": "<brief description of what you identified>"
                }
                
                If the image is not a meal or food, return:
                {"calories":0,"protein":0,"carbs":0,"fat":0,"confidence":"low","notes":"No food detected"}
            """.trimIndent()

            val apiKey = getApiKey() ?: return@withContext Result.failure(
                Exception("Add your Gemini API key in Settings")
            )

            try {
                // Image requests use a different content structure
                val requestBody = org.json.JSONObject().apply {
                    put("contents", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(org.json.JSONObject().apply {
                                    put("text", prompt)
                                })
                                put(org.json.JSONObject().apply {
                                    put("inline_data", org.json.JSONObject().apply {
                                        put("mime_type", mimeType)
                                        put("data", base64Image)
                                    })
                                })
                            })
                        })
                    })
                }.toString()

                val responseText = executeRequest(apiKey, requestBody)
                    ?: return@withContext Result.failure(Exception("Coach unavailable — check your connection"))

                val cleanJson = extractJsonFromRawText(responseText)
                val obj = org.json.JSONObject(cleanJson)

                Result.success(
                    GeminiMacroEstimate(
                        calories = obj.optInt("calories", 0),
                        protein = obj.optDouble("protein", 0.0),
                        carbs = obj.optDouble("carbs", 0.0),
                        fat = obj.optDouble("fat", 0.0),
                        confidence = obj.optString("confidence", "low"),
                        notes = obj.optString("notes", "")
                    )
                )
            } catch (e: Exception) {
                Log.e("GeminiService", "Meal photo parse failed", e)
                Result.failure(Exception("Coach unavailable — check your connection"))
            }
        }

    // ─────────────────────────────────────────────────────────────
    // FUNCTION 4 — WORKOUT PLAN PARSE
    // Called from plan upload. Returns structured plan days and exercises.
    // ─────────────────────────────────────────────────────────────

    suspend fun parseWorkoutPlan(planText: String): Result<List<ParsedWorkoutDay>> =
        withContext(Dispatchers.IO) {
            val prompt = """
                Parse this workout plan into structured JSON.
                Respond ONLY with a valid JSON array. No markdown, no backticks, no explanation.
                Each element in the array represents one training day:
                [
                  {
                    "label": "<Day label e.g. Upper A>",
                    "day": "<Day of week e.g. Monday>",
                    "exercises": [
                      {
                        "name": "<Exercise name>",
                        "muscleGroup": "<primary muscle: chest|back|quad|hamstring|glute|bicep|tricep|front_delt|side_delt|rear_delt|core|calf|cardio|full_body>",
                        "sets": <integer>,
                        "repsMin": <integer>,
                        "repsMax": <integer>,
                        "restSeconds": <integer>
                      }
                    ]
                  }
                ]
                
                Workout plan to parse:
                $planText
            """.trimIndent()

            callGemini(prompt).mapCatching { responseText ->
                val cleanJson = extractJsonFromRawText(responseText)
                val array = org.json.JSONArray(cleanJson)
                val list = mutableListOf<ParsedWorkoutDay>()
                for (i in 0 until array.length()) {
                    val dayObj = array.getJSONObject(i)
                    val exercisesArray = dayObj.optJSONArray("exercises") ?: org.json.JSONArray()
                    val exercisesList = mutableListOf<ParsedWorkoutExercise>()
                    for (j in 0 until exercisesArray.length()) {
                        val ex = exercisesArray.getJSONObject(j)
                        exercisesList.add(
                            ParsedWorkoutExercise(
                                name = ex.optString("name", ""),
                                muscleGroup = ex.optString("muscleGroup", "full_body"),
                                sets = ex.optInt("sets", 3),
                                repsMin = ex.optInt("repsMin", 8),
                                repsMax = ex.optInt("repsMax", 12),
                                restSeconds = ex.optInt("restSeconds", 90)
                            )
                        )
                    }
                    list.add(
                        ParsedWorkoutDay(
                            label = dayObj.optString("label", ""),
                            day = dayObj.optString("day", ""),
                            exercises = exercisesList
                        )
                    )
                }
                list
            }
        }

    // ─────────────────────────────────────────────────────────────
    // CORE — SHARED HTTP CALLER
    // All functions route through here.
    // ─────────────────────────────────────────────────────────────

    private suspend fun callGemini(promptText: String): Result<String> =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(
                    Exception("Add your Gemini API key in Settings")
                )
            }

            val requestBody = org.json.JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply { put("text", promptText) })
                        })
                    })
                })
            }.toString()

            try {
                val responseText = executeRequest(apiKey, requestBody)
                    ?: return@withContext Result.failure(
                        Exception("Coach unavailable — check your connection")
                    )
                Result.success(responseText)
            } catch (e: Exception) {
                Log.e("GeminiService", "Gemini call failed", e)
                Result.failure(Exception("Coach unavailable — check your connection"))
            }
        }

    /**
     * Raw HTTP POST to Gemini. Returns parsed text from
     * candidates[0].content.parts[0].text or null on any failure.
     */
    private fun executeRequest(apiKey: String, requestBody: String): String? {
        return try {
            val url = URL("$BASE_URL?key=$apiKey")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 30_000
            }

            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e("GeminiService", "HTTP $responseCode: $errorBody")
                return null
            }

            val responseText = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val root = org.json.JSONObject(responseText)
            root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

        } catch (e: Exception) {
            Log.e("GeminiService", "executeRequest failed", e)
            null
        }
    }

    private suspend fun getApiKey(): String? {
        return dataStore.geminiApiKeyFlow.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun extractJsonFromRawText(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substring(7)
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3)
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length - 3)
        }
        return clean.trim()
    }
}
