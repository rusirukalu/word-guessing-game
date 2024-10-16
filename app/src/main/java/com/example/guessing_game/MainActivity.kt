package com.example.guessing_game

import android.annotation.SuppressLint
import android.widget.EditText
import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var guessEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var attemptsTextView: TextView
    private lateinit var letterButton: Button
    private lateinit var clueButton: Button
    private lateinit var scoreTextView: TextView
    private lateinit var letterCountButton: Button

    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var attempts = 0
    private var score = 100
    private val maxAttempts = 10
    private var secretWord = ""
    private var timeInSeconds = 0
    private var startTime = 0L
    private var timerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        timerTextView = findViewById(R.id.timerTextView)
        guessEditText = findViewById(R.id.guessEditText)
        submitButton = findViewById(R.id.submitButton)
        attemptsTextView = findViewById(R.id.attemptsTextView)
        letterButton = findViewById(R.id.letterButton)
        clueButton = findViewById(R.id.clueButton)
        scoreTextView = findViewById(R.id.scoreTextView)
        letterCountButton = findViewById(R.id.letterCountButton)


        // SharedPreferences for user onboarding
        sharedPreferences = getSharedPreferences("GamePrefs", MODE_PRIVATE)

        // Ask for name on app start
        askUserName()


        fetchRandomWord() // Fetch the random word from API

        // Start the timer
        startTimer()

        letterCountButton = findViewById(R.id.letterCountButton)
        letterCountButton.setOnClickListener { showLetterCount() }


        // Setup button listeners
        submitButton.setOnClickListener { checkGuess() }
        letterButton.setOnClickListener { askForLetter() }
        clueButton.setOnClickListener { giveClue() }
    }



    @SuppressLint("MutatingSharedPrefs")
    private fun askUserName() {
        // Create an EditText for user to enter name
        val nameEditText = EditText(this).apply {
            hint = "Enter your name"
        }

        // Build an AlertDialog to ask for the user's name
        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter your name")
            .setView(nameEditText)
            .setPositiveButton("OK") { dialog, _ ->
                val userName = nameEditText.text.toString().trim()

                // Check if the entered name is empty
                if (userName.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show()
                    askUserName() // Reopen dialog if name is empty
                } else {
                    // Retrieve the stored set of usernames from SharedPreferences
                    val savedNamesSet = sharedPreferences.getStringSet("usernames", mutableSetOf()) ?: mutableSetOf()

                    if (savedNamesSet.contains(userName)) {
                        // If the user exists, welcome them back
                        Toast.makeText(this, "Welcome back, $userName!", Toast.LENGTH_SHORT).show()
                    } else {
                        // If the user is new, add them to the set and store it
                        savedNamesSet.add(userName)
                        sharedPreferences.edit().putStringSet("usernames", savedNamesSet).apply()
                        Toast.makeText(this, "Hello, $userName! Welcome to the app!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setCancelable(false) // Prevent dialog from closing without input
            .create()

        dialog.show() // Show the dialog for the user to input their name
    }


    // Fetch a random word from an online API
    private fun fetchRandomWord() {
        CoroutineScope(Dispatchers.IO).launch {
            val apiUrl = "https://random-word-api.herokuapp.com/word"
            val url = URL(apiUrl)
            val urlConnection: HttpURLConnection = url.openConnection() as HttpURLConnection
            try {
                val inStream = urlConnection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(inStream)
                if (jsonArray.length() > 0) {
                    secretWord = jsonArray.getString(0) // Fetch the random word
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to fetch word. Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error fetching word: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                urlConnection.disconnect()
            }
        }
    }

    // Start the game timer
    private fun startTimer() {
        startTime = System.currentTimeMillis()
        timerRunning = true
        handler.postDelayed(timerRunnable, 1000)
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timerRunning) {
                timeInSeconds++
                val minutes = timeInSeconds / 60
                val seconds = timeInSeconds % 60
                timerTextView.text = String.format("%02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun stopTimer() {
        timerRunning = false
        handler.removeCallbacks(timerRunnable)
    }

    // Check the user's guess
    private fun checkGuess() {
        val userGuess = guessEditText.text.toString()

        if (attempts < maxAttempts) {
            attempts++
            attemptsTextView.text = "Attempts: $attempts"

            if (userGuess.equals(secretWord, true)) {
                stopTimer()
                val timeElapsed = System.currentTimeMillis() - startTime
                Toast.makeText(this, "Correct! Time: ${timeElapsed / 1000} seconds, Score: $score", Toast.LENGTH_SHORT).show()
                updateLeaderboard()
            } else {
                score -= 10
                scoreTextView.text = "Score: $score"
                if (score <= 0 || attempts == maxAttempts) {
                    stopTimer()
                    Toast.makeText(this, "Game over! New word will be given.", Toast.LENGTH_SHORT).show()
                    resetGame()
                } else {
                    Toast.makeText(this, "Incorrect! Try again. Score: $score", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Reset the game
    private fun resetGame() {
        attempts = 0
        score = 100
        fetchRandomWord()
        scoreTextView.text = "Score: $score"
    }


    @SuppressLint("SetTextI18n")
    private fun showLetterCount() {
        // Make sure the secretWord is not empty
        if (secretWord.isEmpty()) {
            runOnUiThread {
                Toast.makeText(this, "Secret word not fetched yet. Please try again later.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (score >= 5) {
            // Deduct 5 points for showing the letter count
            score -= 5

            // Run the following UI updates on the main thread
            runOnUiThread {
                // Update score text view and show the number of letters
                scoreTextView.text = "Score: $score"
                val letterCount = secretWord.length
                Toast.makeText(this, "The secret word has $letterCount letters. Score: $score", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Run this UI message on the main thread
            runOnUiThread {
                Toast.makeText(this, "Not enough score to ask for the letter count.", Toast.LENGTH_SHORT).show()
            }
        }
    }






    // Ask for a letter occurrence
    private fun askForLetter() {
        val letter = guessEditText.text.toString().firstOrNull()

        if (letter == null) {
            Toast.makeText(this, "Please enter a letter.", Toast.LENGTH_SHORT).show()
            return
        }

        if (score < 5) {
            Toast.makeText(this, "Not enough score to ask for a letter.", Toast.LENGTH_SHORT).show()
            return
        }

        if (letter in secretWord) {
            val occurrences = secretWord.count { it.equals(letter, true) } // Check occurrences (case-insensitive)
            score -= 5
            scoreTextView.text = "Score: $score"
            Toast.makeText(this, "The letter '$letter' occurs $occurrences time(s). Score: $score", Toast.LENGTH_SHORT).show()
        } else {
            score -= 5
            scoreTextView.text = "Score: $score"
            Toast.makeText(this, "The letter '$letter' does not occur in the word. Score: $score", Toast.LENGTH_SHORT).show()
        }

        // Clear the EditText after processing
        guessEditText.text.clear()
    }


//    private fun giveTip() {
//        // Allow fetching tips only after 5 attempts
//        if (attempts < 5) {
//            Toast.makeText(this, "Make at least 5 attempts before asking for a tip.", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Check if a tip has already been given
//        if (tipGiven) {
//            Toast.makeText(this, "You've already received a tip for this word.", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Determine which API to use
//        val apiUrl = if ((0..1).random() == 0) {
//            "https://api-ninjas.com/api/rhyme?word=$secretWord"
//        } else {
//            "https://api-ninjas.com/api/thesaurus?word=$secretWord"
//        }
//
//        CoroutineScope(Dispatchers.IO).launch {
//            val client = OkHttpClient()
//            val request = Request.Builder().url(apiUrl).build()
//
//            try {
//                val response: Response = client.newCall(request).execute()
//                if (response.isSuccessful) {
//                    val result = response.body?.string()
//                    val jsonArray = JSONArray(result)
//
//                    // Get the tip message
//                    val tipMessage = if (jsonArray.length() > 0) {
//                        if (apiUrl.contains("rhyme")) {
//                            "A rhyming word is: ${jsonArray.getString(0)}"
//                        } else {
//                            "A similar word is: ${jsonArray.getJSONObject(0).getString("word")}"
//                        }
//                    } else {
//                        "No tips found."
//                    }
//
//                    // Show the tip message on the main thread
//                    withContext(Dispatchers.Main) {
//                        Toast.makeText(this@MainActivity, tipMessage, Toast.LENGTH_SHORT).show()
//                        tipGiven = true // Set to true after giving the tip
//                    }
//                } else {
//                    withContext(Dispatchers.Main) {
//                        Toast.makeText(this@MainActivity, "Error fetching tip. Please try again.", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@MainActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }



    // Give a clue
    private fun giveClue() {
        if (score >= 5) {
            score -= 5
            scoreTextView.text = "Score: $score"
            Toast.makeText(this, "Clue: The word starts with '${secretWord.first()}'. Score: $score", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Not enough score for a clue.", Toast.LENGTH_SHORT).show()
        }
    }

    // Update leaderboard (stub for actual implementation)
    private fun updateLeaderboard() {
        Toast.makeText(this, "Leaderboard updated.", Toast.LENGTH_SHORT).show()
    }
}