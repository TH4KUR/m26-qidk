package com.megathon.incident.signals

/** One classifier output: the class's display name (for YamNet, its AudioSet name) and its score in 0..1. */
data class ClassScore(val label: String, val score: Float)
