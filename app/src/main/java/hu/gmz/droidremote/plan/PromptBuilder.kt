package hu.gmz.droidremote.plan

import android.content.Context

class PromptBuilder(private val context: Context) {

    fun systemPrompt(): String = read("prompts/system.md")

    fun actorPrompt(
        goal: String,
        step: Int,
        maxSteps: Int,
        lastAction: String,
        lastChange: String,
        screen: String,
    ): String = read("prompts/actor.md")
        .replace("{{goal}}", goal)
        .replace("{{step}}", step.toString())
        .replace("{{maxSteps}}", maxSteps.toString())
        .replace("{{lastAction}}", lastAction)
        .replace("{{lastChange}}", lastChange)
        .replace("{{screen}}", screen)

    private fun read(asset: String): String =
        context.assets.open(asset).bufferedReader().use { it.readText() }
}
